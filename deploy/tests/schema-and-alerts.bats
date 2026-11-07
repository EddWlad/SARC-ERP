#!/usr/bin/env bats
# Pruebas de sarc-schema-check.sh (paso 5) y sarc-alerts-gate.sh (paso 2b). Datos ficticios.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	SCHEMA="$REPO_ROOT/deploy/scripts/sarc-schema-check.sh"
	GATE="$REPO_ROOT/deploy/scripts/sarc-alerts-gate.sh"
	TMP="$(mktemp -d)"
	export TMP
	mkdir -p "$TMP/bin"
	export PATH="$TMP/bin:$PATH"
	# Doble de docker: devuelve el historial indicado en FAKE_HISTORY (filas rank|version|checksum|success).
	{
		echo '#!/usr/bin/env bash'
		echo 'printf "%b" "$FAKE_HISTORY"'
	} >"$TMP/bin/docker"
	chmod +x "$TMP/bin/docker"
	export SARC_ENV_FILE="$TMP/sarc.env" SARC_COMPOSE_FILE="$TMP/compose.yaml"
	cat >"$TMP/manifest.json" <<'JSON'
{"migrations":{"latest":"V003","files":[
 {"name":"V001__uno.sql","sha256":"x","flywayChecksum":111},
 {"name":"V002__dos.sql","sha256":"y","flywayChecksum":-222},
 {"name":"V003__tres.sql","sha256":"z","flywayChecksum":333}]}}
JSON
}

teardown() {
	rm -rf "$TMP"
}

@test "esquema: historial coherente y una pendiente posterior pasa" {
	FAKE_HISTORY='1|001|111|t\n2|002|-222|t\n' run "$SCHEMA" "$TMP/manifest.json"
	[ "$status" -eq 0 ]
}

@test "esquema: una migracion aplicada desconocida devuelve 15" {
	FAKE_HISTORY='1|001|111|t\n2|009|5|t\n' run "$SCHEMA" "$TMP/manifest.json"
	[ "$status" -eq 15 ]
	[[ "$output" == *"V009"* ]]
}

@test "esquema: una suma distinta devuelve 15" {
	FAKE_HISTORY='1|001|111|t\n2|002|999|t\n' run "$SCHEMA" "$TMP/manifest.json"
	[ "$status" -eq 15 ]
	[[ "$output" == *"V002__dos.sql"* ]]
}

@test "esquema: una fila fallida devuelve 15" {
	FAKE_HISTORY='1|001|111|t\n2|002|-222|f\n' run "$SCHEMA" "$TMP/manifest.json"
	[ "$status" -eq 15 ]
}

@test "esquema: una pendiente con numero menor que la ultima aplicada devuelve 15" {
	# V002 está en el manifiesto pero no aplicada, y ya hay una V003 aplicada.
	FAKE_HISTORY='1|001|111|t\n3|003|333|t\n' run "$SCHEMA" "$TMP/manifest.json"
	[ "$status" -eq 15 ]
	[[ "$output" == *"V002__dos.sql"* ]]
}

@test "esquema: imprime solo nombres de migracion, no datos" {
	FAKE_HISTORY='1|001|111|t\n2|009|5|t\n' run "$SCHEMA" "$TMP/manifest.json"
	[[ "$output" != *"|"* ]]
}

# ---------- Prueba de recepcion ----------

preparar_avisos() {
	export SARC_STATE_DIR="$TMP/state" SARC_AVISOS_CONF="$TMP/avisos.conf"
	mkdir -p "$SARC_STATE_DIR"
	echo "destinatarios=responsable@example.invalid,suplente@example.invalid" >"$SARC_AVISOS_CONF"
	huella="$(sha256sum "$SARC_AVISOS_CONF" | cut -d' ' -f1)"
	printf 'fecha=2026-11-20T10:00:00Z\nhuella=%s\nconfirmado=responsable\nconfirmado=suplente\n' "$huella" \
		>"$SARC_STATE_DIR/prueba-recepcion.ok"
}

@test "gate: una prueba vigente con ambas confirmaciones pasa" {
	preparar_avisos
	run "$GATE"
	[ "$status" -eq 0 ]
	[[ "$output" != *"example.invalid"* ]]
}

@test "gate: sin archivo devuelve 18" {
	preparar_avisos
	rm "$SARC_STATE_DIR/prueba-recepcion.ok"
	run "$GATE"
	[ "$status" -eq 18 ]
}

@test "gate: si cambian los destinatarios la huella no coincide y devuelve 18" {
	preparar_avisos
	echo "destinatarios=otro@example.invalid" >"$SARC_AVISOS_CONF"
	run "$GATE"
	[ "$status" -eq 18 ]
	[[ "$output" != *"otro@example.invalid"* ]]
}

@test "gate: falta la confirmacion de una persona y devuelve 18" {
	preparar_avisos
	sed -i '/confirmado=suplente/d' "$SARC_STATE_DIR/prueba-recepcion.ok"
	run "$GATE"
	[ "$status" -eq 18 ]
}

@test "gate: la misma persona confirmando dos veces no basta" {
	preparar_avisos
	sed -i 's/confirmado=suplente/confirmado=responsable/' "$SARC_STATE_DIR/prueba-recepcion.ok"
	run "$GATE"
	[ "$status" -eq 18 ]
}
