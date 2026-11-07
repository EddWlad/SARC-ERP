#!/usr/bin/env bats
# Pruebas de .github/scripts/classify-failure.sh (FR-004) y osv-gate.sh (FR-005). Datos ficticios.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	CLASIFICAR="$REPO_ROOT/.github/scripts/classify-failure.sh"
	GATE="$REPO_ROOT/.github/scripts/osv-gate.sh"
	TMP="$(mktemp -d)"
	export GITHUB_STEP_SUMMARY="$TMP/resumen.md"
}

teardown() {
	rm -rf "$TMP"
}

@test "un paso de preparacion fallido se clasifica como de infraestructura" {
	STEPS_JSON='{"prep-checkout":{"outcome":"failure"},"verify":{"outcome":"skipped"}}' run "$CLASIFICAR" Backend
	[ "$status" -eq 0 ]
	grep -q "fallo de infraestructura" "$GITHUB_STEP_SUMMARY"
}

@test "un paso de verificacion fallido se clasifica como del cambio" {
	echo "FAIL: prueba rota" >"$TMP/registro.log"
	STEPS_JSON='{"prep-java":{"outcome":"success"},"verify":{"outcome":"failure"}}' run "$CLASIFICAR" Backend "$TMP/registro.log"
	[ "$status" -eq 0 ]
	grep -q "fallo del cambio" "$GITHUB_STEP_SUMMARY"
}

@test "un error de red en el registro se clasifica como de infraestructura" {
	echo "curl: (28) Connection timed out after 30000 ms" >"$TMP/registro.log"
	STEPS_JSON='{"verify":{"outcome":"failure"}}' run "$CLASIFICAR" Backend "$TMP/registro.log"
	grep -q "fallo de infraestructura" "$GITHUB_STEP_SUMMARY"
}

@test "sin pasos fallidos el resumen indica que no hay fallos" {
	STEPS_JSON='{"verify":{"outcome":"success"}}' run "$CLASIFICAR" Frontend
	grep -q "sin fallos" "$GITHUB_STEP_SUMMARY"
}

osv_json() {
	printf '{"results":[{"packages":[{"package":{"name":"%s"},"groups":[{"ids":["%s"],"max_severity":"%s"}]}]}]}' "$1" "$2" "$3" >"$TMP/osv.json"
}

@test "osv-gate falla con severidad alta" {
	osv_json libreria-a GHSA-aaaa 7.5
	run "$GATE" "$TMP/osv.json"
	[ "$status" -eq 1 ]
	[[ "$output" == *"libreria-a"* ]]
}

@test "osv-gate falla con severidad critica" {
	osv_json libreria-b GHSA-bbbb 9.8
	run "$GATE" "$TMP/osv.json"
	[ "$status" -eq 1 ]
}

@test "osv-gate solo avisa con severidad media" {
	osv_json libreria-c GHSA-cccc 5.3
	run "$GATE" "$TMP/osv.json"
	[ "$status" -eq 0 ]
	grep -q "libreria-c" "$GITHUB_STEP_SUMMARY"
}

@test "osv-gate pasa sin resultados" {
	echo '{"results":[]}' >"$TMP/osv.json"
	run "$GATE" "$TMP/osv.json"
	[ "$status" -eq 0 ]
}
