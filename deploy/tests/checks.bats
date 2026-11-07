#!/usr/bin/env bats
# Pruebas de sarc-check-cert.sh, sarc-check-disk.sh y sarc-heartbeat.sh (T054). Certificados y CA de prueba efimeros.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	CERT="$REPO_ROOT/deploy/scripts/sarc-check-cert.sh"
	DISK="$REPO_ROOT/deploy/scripts/sarc-check-disk.sh"
	BEAT="$REPO_ROOT/deploy/scripts/sarc-heartbeat.sh"
	TMP="$(mktemp -d)"
	mkdir -p "$TMP/bin"
	export PATH="$TMP/bin:$PATH"
	export SARC_ENV_FILE="$TMP/no-existe.env"
	export CURL_LOG="$TMP/curl.log"
	export HC_URL_CERTIFICADO="https://hc.example.invalid/c"
	export HC_URL_DISCO="https://hc.example.invalid/d"
	export HC_URL_VPS_LATIDO="https://hc.example.invalid/v"
	export HC_URL_SERVICIO="https://hc.example.invalid/s"
	unset SARC_TEST_CA_FILE SARC_DEPLOY_ENV SARC_PUBLIC_IP SARC_CERT_FILE
	# Doble de curl: la readiness local responde segun READINESS; el resto se registra como latido.
	{
		echo '#!/usr/bin/env bash'
		echo 'case "$*" in'
		echo '  *127.0.0.1:8080*) [ "$READINESS" = up ] && echo "{\"status\":\"UP\"}" || exit 22;;'
		echo '  *) printf "%s\n" "$*" >>"$CURL_LOG";;'
		echo 'esac'
	} >"$TMP/bin/curl"
	chmod +x "$TMP/bin/curl"
}

teardown() {
	rm -rf "$TMP"
}

# doble_df <usado> <libre>: df que informa el porcentaje de uso indicado.
doble_df() {
	{
		echo '#!/usr/bin/env bash'
		echo "echo 'Filesystem 1024-blocks Used Available Capacity Mounted'"
		echo "echo '/dev/x 100 $1 $2 $1% /'"
	} >"$TMP/bin/df"
	chmod +x "$TMP/bin/df"
}

# crear_certificados <dias de vigencia de la hoja>
crear_certificados() {
	openssl req -x509 -newkey rsa:2048 -nodes -keyout "$TMP/ca.key" -out "$TMP/ca.pem" -days 30 \
		-subj "/CN=CA de prueba SARC" -addext "basicConstraints=critical,CA:TRUE" >/dev/null 2>&1
	openssl req -newkey rsa:2048 -nodes -keyout "$TMP/hoja.key" -out "$TMP/hoja.csr" -subj "/CN=192.0.2.10" >/dev/null 2>&1
	echo "subjectAltName=IP:192.0.2.10" >"$TMP/ext.cnf"
	openssl x509 -req -in "$TMP/hoja.csr" -CA "$TMP/ca.pem" -CAkey "$TMP/ca.key" -CAcreateserial \
		-out "$TMP/hoja.pem" -days "$1" -extfile "$TMP/ext.cnf" >/dev/null 2>&1
	cat "$TMP/hoja.pem" >"$TMP/fullchain.pem"
	export SARC_CERT_FILE="$TMP/fullchain.pem"
}

@test "certificado: falla si el archivo no existe y envia /fail" {
	export SARC_CERT_FILE="$TMP/no-existe.pem"
	run "$CERT"
	[ "$status" -eq 13 ]
	grep -q "/c/fail" "$CURL_LOG"
}

@test "certificado: falla si quedan menos de 72 h" {
	crear_certificados 2
	export SARC_DEPLOY_ENV=ensayo SARC_TEST_CA_FILE="$TMP/ca.pem"
	run "$CERT"
	[ "$status" -eq 13 ]
	grep -q "/c/fail" "$CURL_LOG"
}

@test "certificado: falla si la cadena no valida contra el almacen del sistema" {
	crear_certificados 10
	run "$CERT"
	[ "$status" -eq 13 ]
	grep -q "/c/fail" "$CURL_LOG"
}

@test "certificado: con SARC_DEPLOY_ENV=ensayo valida contra la CA de prueba" {
	crear_certificados 10
	export SARC_DEPLOY_ENV=ensayo SARC_TEST_CA_FILE="$TMP/ca.pem" SARC_PUBLIC_IP="192.0.2.10"
	run "$CERT"
	[ "$status" -eq 0 ]
	grep -q "hc.example.invalid/c" "$CURL_LOG"
	! grep -q "/fail" "$CURL_LOG"
}

@test "certificado: en produccion ignora la CA de prueba y falla si esta definida" {
	crear_certificados 10
	export SARC_DEPLOY_ENV=produccion SARC_TEST_CA_FILE="$TMP/ca.pem"
	run "$CERT"
	[ "$status" -eq 13 ]
	unset SARC_DEPLOY_ENV
	run "$CERT"
	[ "$status" -eq 13 ]
}

@test "certificado: la IP del certificado debe coincidir con SARC_PUBLIC_IP" {
	crear_certificados 10
	export SARC_DEPLOY_ENV=ensayo SARC_TEST_CA_FILE="$TMP/ca.pem" SARC_PUBLIC_IP="192.0.2.99"
	run "$CERT"
	[ "$status" -eq 13 ]
}

@test "disco: falla con uso mayor al 80 por ciento y envia /fail a disco" {
	doble_df 85 15
	run "$DISK"
	[ "$status" -eq 1 ]
	grep -q "/d/fail" "$CURL_LOG"
}

@test "disco: con uso del 60 por ciento pasa" {
	doble_df 60 40
	run "$DISK"
	[ "$status" -eq 0 ]
	! grep -q "/fail" "$CURL_LOG"
}

@test "latido: envia vps-latido siempre y servicio solo si readiness es UP" {
	READINESS=up run "$BEAT"
	[ "$status" -eq 0 ]
	grep -q "hc.example.invalid/v" "$CURL_LOG"
	grep -q "hc.example.invalid/s" "$CURL_LOG"
	! grep -q "/s/fail" "$CURL_LOG"
}

@test "latido: con readiness caida envia vps-latido y /fail a servicio" {
	READINESS=down run "$BEAT"
	[ "$status" -eq 0 ]
	grep -q "hc.example.invalid/v" "$CURL_LOG"
	grep -q "/s/fail" "$CURL_LOG"
}
