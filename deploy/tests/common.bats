#!/usr/bin/env bats
# Pruebas de deploy/scripts/lib/common.sh (T006). Sin valores reales.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	TMP="$(mktemp -d)"
	mkdir -p "$TMP/bin"
	export PATH="$TMP/bin:$PATH"
}

teardown() {
	rm -rf "$TMP"
}

# Doble de curl: guarda los argumentos recibidos para inspeccionarlos.
crear_curl_doble() {
	{
		echo '#!/usr/bin/env bash'
		echo 'printf "%s\n" "$@" >"$CURL_ARGS_FILE"'
		echo 'exit 0'
	} >"$TMP/bin/curl"
	chmod +x "$TMP/bin/curl"
	export CURL_ARGS_FILE="$TMP/curl-args.txt"
}

@test "require_env falla con 12 y no imprime valores" {
	export SARC_PRUEBA_PRESENTE="valor-secreto-de-prueba"
	unset SARC_PRUEBA_AUSENTE || true
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; require_env SARC_PRUEBA_PRESENTE SARC_PRUEBA_AUSENTE'
	[ "$status" -eq 12 ]
	[[ "$output" == *"SARC_PRUEBA_AUSENTE"* ]]
	[[ "$output" != *"valor-secreto-de-prueba"* ]]
	[[ "$output" != *"SARC_PRUEBA_PRESENTE"* ]]
}

@test "require_env pasa si todas las variables existen" {
	export SARC_A="1" SARC_B="2"
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; require_env SARC_A SARC_B'
	[ "$status" -eq 0 ]
}

@test "with_lock devuelve 10 si el bloqueo esta tomado" {
	command -v flock >/dev/null || skip "flock no disponible"
	LOCK="$TMP/sarc.lock"
	# Un proceso mantiene el bloqueo mientras se ejecuta la segunda instancia.
	( exec 8>"$LOCK"; flock -n 8; sleep 3 ) &
	holder=$!
	sleep 1
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; with_lock "$1"' _ "$LOCK"
	kill "$holder" 2>/dev/null || true
	wait "$holder" 2>/dev/null || true
	[ "$status" -eq 10 ]
}

@test "with_lock permite tomar un bloqueo libre" {
	command -v flock >/dev/null || skip "flock no disponible"
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; with_lock "$1"' _ "$TMP/libre.lock"
	[ "$status" -eq 0 ]
}

@test "ping_check no envia cuerpo y usa la URL de la comprobacion" {
	crear_curl_doble
	export HC_URL_COPIA_NOCTURNA="https://hc.example.invalid/ping/uuid"
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; ping_check copia-nocturna ok'
	[ "$status" -eq 0 ]
	args="$(cat "$CURL_ARGS_FILE")"
	[[ "$args" == *"https://hc.example.invalid/ping/uuid"* ]]
	[[ "$args" != *"--data"* ]]
	[[ "$args" != *"-X"* ]]
	# La URL no se imprime en la salida.
	[[ "$output" != *"hc.example.invalid"* ]]
}

@test "ping_check fail agrega /fail a la URL" {
	crear_curl_doble
	export HC_URL_DISCO="https://hc.example.invalid/ping/uuid"
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; ping_check disco fail'
	[ "$status" -eq 0 ]
	[[ "$(cat "$CURL_ARGS_FILE")" == *"https://hc.example.invalid/ping/uuid/fail"* ]]
}

@test "ping_check sin URL definida no falla y no llama a curl" {
	crear_curl_doble
	unset HC_URL_SIN_DEFINIR || true
	run bash -c 'source "$SARC_LIB_DIR/common.sh"; ping_check sin-definir ok'
	[ "$status" -eq 0 ]
	[ ! -e "$CURL_ARGS_FILE" ]
}
