#!/usr/bin/env bats
# Pruebas de deploy/release/build-manifest.sh (T011). Artefactos y datos ficticios.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	SCRIPT="$REPO_ROOT/deploy/release/build-manifest.sh"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	TMP="$(mktemp -d)"
	VERSION="2026.11.20-57-e85c910"
	COMMIT="e85c9100000000000000000000000000000000aa"
	mkdir -p "$TMP/art" "$TMP/mig" "$TMP/out"
	echo "jar de prueba" >"$TMP/art/sarc-backend-$VERSION.jar"
	echo "tgz de prueba" >"$TMP/art/sarc-frontend-$VERSION.tar.gz"
	echo "-- uno" >"$TMP/mig/V001__uno.sql"
	echo "-- dos" >"$TMP/mig/V002__dos.sql"
	printf 'backend-verify\tsuccess\tcodigo\thttps://github.example.invalid/run/1\n' >"$TMP/checks.tsv"
	printf 'frontend\tsuccess\tcodigo\thttps://github.example.invalid/run/2\n' >>"$TMP/checks.tsv"
}

teardown() {
	rm -rf "$TMP"
}

generar() {
	"$SCRIPT" --version "$VERSION" --commit "$COMMIT" --run-id 123456789 \
		--artifacts-dir "$TMP/art" --migrations-dir "$TMP/mig" --checks-file "$TMP/checks.tsv" \
		--output-dir "$TMP/out" "$@"
}

@test "el manifiesto cumple el contrato" {
	SARC_CREATED_AT="2026-11-20T15:04:05Z" run generar
	[ "$status" -eq 0 ]
	m="$TMP/out/manifest.json"
	[ "$(jq -r .schema "$m")" = "1" ]
	[ "$(jq -r .version "$m")" = "$VERSION" ]
	[ "$(jq -r .commit "$m")" = "$COMMIT" ]
	[ "$(jq -r .ciRunId "$m")" = "123456789" ]
	[ "$(jq -r .createdAt "$m")" = "2026-11-20T15:04:05Z" ]
	[ "$(jq -r '[.checks[].conclusion] | unique | join(",")' "$m")" = "success" ]
	[ "$(jq -r '.checks | length' "$m")" = "2" ]
	[ "$(jq -r .migrations.latest "$m")" = "V002" ]
	[ "$(jq -r '.migrations.files | length' "$m")" = "2" ]
	[ "$(jq -r '.migrations.files[0].flywayChecksum | type' "$m")" = "number" ]
	[ "$(jq -r '.schemaChanges | type' "$m")" = "boolean" ]
}

@test "las sumas de los artefactos coinciden con sha256sum" {
	run generar
	[ "$status" -eq 0 ]
	m="$TMP/out/manifest.json"
	esperado="$(sha256sum "$TMP/art/sarc-backend-$VERSION.jar" | cut -d' ' -f1)"
	[ "$(jq -r --arg n "sarc-backend-$VERSION.jar" '.artifacts[] | select(.name==$n) | .sha256' "$m")" = "$esperado" ]
	esperado="$(sha256sum "$TMP/art/sarc-frontend-$VERSION.tar.gz" | cut -d' ' -f1)"
	[ "$(jq -r --arg n "sarc-frontend-$VERSION.tar.gz" '.artifacts[] | select(.name==$n) | .sha256' "$m")" = "$esperado" ]
	# SHA256SUMS contiene las mismas sumas y se verifica con sha256sum -c.
	cp "$TMP/art/"* "$TMP/out/"
	(cd "$TMP/out" && sha256sum -c SHA256SUMS)
}

@test "falla si alguna comprobacion no es success y no genera archivos" {
	printf 'secretos\tfailure\tcodigo\thttps://github.example.invalid/run/3\n' >>"$TMP/checks.tsv"
	run generar
	[ "$status" -eq 1 ]
	[[ "$output" == *"secretos"* ]]
	[ ! -e "$TMP/out/manifest.json" ]
	[ ! -e "$TMP/out/SHA256SUMS" ]
}

@test "no incluye variables de entorno con SECRET, PASSWORD, KEY o TOKEN" {
	export SARC_TEST_SECRET="valor-secreto-1" SARC_TEST_PASSWORD="valor-secreto-2" \
		SARC_TEST_API_KEY="valor-secreto-3" SARC_TEST_TOKEN="valor-secreto-4"
	run generar
	[ "$status" -eq 0 ]
	! grep -q "valor-secreto" "$TMP/out/manifest.json"
	! grep -q "valor-secreto" "$TMP/out/SHA256SUMS"
	[[ "$output" != *"valor-secreto"* ]]
}

@test "rechaza una URL con credenciales" {
	printf 'malo\tsuccess\tcodigo\thttps://usuario:clave@example.invalid/x\n' >>"$TMP/checks.tsv"
	run generar
	[ "$status" -eq 2 ]
	[[ "$output" != *"clave@"* ]]
}

@test "falla si falta un artefacto" {
	rm "$TMP/art/sarc-frontend-$VERSION.tar.gz"
	run generar
	[ "$status" -eq 1 ]
}

@test "version con formato incorrecto devuelve 2" {
	run "$SCRIPT" --version "1.0.0" --commit "$COMMIT" --run-id 1 \
		--artifacts-dir "$TMP/art" --migrations-dir "$TMP/mig" --checks-file "$TMP/checks.tsv" --output-dir "$TMP/out"
	[ "$status" -eq 2 ]
}

@test "schemaChanges compara con el manifiesto anterior" {
	generar
	cp "$TMP/out/manifest.json" "$TMP/previo.json"
	run generar --previous-manifest "$TMP/previo.json"
	[ "$(jq -r .schemaChanges "$TMP/out/manifest.json")" = "false" ]
	echo "-- tres" >"$TMP/mig/V003__tres.sql"
	run generar --previous-manifest "$TMP/previo.json"
	[ "$(jq -r .schemaChanges "$TMP/out/manifest.json")" = "true" ]
	[ "$(jq -r .migrations.latest "$TMP/out/manifest.json")" = "V003" ]
}
