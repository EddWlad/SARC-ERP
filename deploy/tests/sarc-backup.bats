#!/usr/bin/env bats
# Pruebas de deploy/scripts/sarc-backup.sh (T043) con dobles de docker y curl, age real y datos ficticios.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	SCRIPT="$REPO_ROOT/deploy/scripts/sarc-backup.sh"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	TMP="$(mktemp -d)"
	mkdir -p "$TMP/bin" "$TMP/backups"
	export PATH="$TMP/bin:$PATH"
	export SARC_BACKUP_DIR="$TMP/backups"
	export SARC_BACKUP_GROUP="grupo-inexistente-para-pruebas"
	export SARC_ENV_FILE="$TMP/sarc.env"
	echo "HC_URL_COPIA_NOCTURNA=https://hc.example.invalid/ping/uuid" >"$SARC_ENV_FILE"
	age-keygen -o "$TMP/clave-privada.txt" 2>"$TMP/pub.txt"
	sed -n 's/^Public key: //p' "$TMP/pub.txt" >"$TMP/backup-recipient.pub"
	export SARC_BACKUP_RECIPIENT="$TMP/backup-recipient.pub"
	export CURL_LOG="$TMP/curl.log"
	export DOCKER_MODE="ok"

	# Doble de docker: responde a pg_dump y a las consultas de metadatos.
	{
		echo '#!/usr/bin/env bash'
		echo 'case "$*" in'
		echo '  *pg_dump*) [ "$DOCKER_MODE" = fallo ] && exit 1; printf "VOLCADO-SINTETICO-DE-PRUEBA";;'
		echo '  *server_version*) echo "18.6";;'
		echo '  *flyway_schema_history*) echo "V016";;'
		echo '  *json_object_agg*) echo "{\"auth_user\":2,\"uptime_week\":5}";;'
		echo 'esac'
	} >"$TMP/bin/docker"
	{
		echo '#!/usr/bin/env bash'
		echo 'printf "%s\n" "$*" >>"$CURL_LOG"'
	} >"$TMP/bin/curl"
	chmod +x "$TMP/bin/docker" "$TMP/bin/curl"
}

teardown() {
	rm -rf "$TMP"
}

@test "genera los tres archivos del contrato con el nombre esperado" {
	run "$SCRIPT" nocturna
	[ "$status" -eq 0 ]
	nombre="$(ls "$SARC_BACKUP_DIR" | grep -E '^sarc-[0-9]{8}T[0-9]{4}Z-nocturna\.dump\.age$')"
	[ -n "$nombre" ]
	[ -s "$SARC_BACKUP_DIR/$nombre" ]
	[ -s "$SARC_BACKUP_DIR/$nombre.sha256" ]
	[ -s "$SARC_BACKUP_DIR/$nombre.meta.json" ]
	(cd "$SARC_BACKUP_DIR" && sha256sum -c "$nombre.sha256")
	# Cifrada con age: se descifra con la clave privada y no contiene el volcado en claro.
	! grep -q "VOLCADO-SINTETICO" "$SARC_BACKUP_DIR/$nombre"
	[ "$(age -d -i "$TMP/clave-privada.txt" "$SARC_BACKUP_DIR/$nombre")" = "VOLCADO-SINTETICO-DE-PRUEBA" ]
}

@test "meta.json tiene los campos del contrato y ningun dato de filas" {
	run "$SCRIPT" previa
	[ "$status" -eq 0 ]
	meta="$(ls "$SARC_BACKUP_DIR"/*.meta.json)"
	[ "$(jq -r .kind "$meta")" = "previa" ]
	[ "$(jq -r .pgVersion "$meta")" = "18.6" ]
	[ "$(jq -r .latestMigration "$meta")" = "V016" ]
	[ "$(jq -r '.tableCounts.auth_user' "$meta")" = "2" ]
	[ -n "$(jq -r .createdAt "$meta")" ]
	[ -n "$(jq -r .keyFingerprint "$meta")" ]
	# Solo las claves esperadas.
	[ "$(jq -r 'keys | join(",")' "$meta")" = "createdAt,keyFingerprint,kind,latestMigration,pgVersion,tableCounts" ]
}

@test "la retencion borra solo copias de mas de 7 dias" {
	viejo="$SARC_BACKUP_DIR/sarc-20200101T0200Z-nocturna.dump.age"
	reciente="$SARC_BACKUP_DIR/sarc-20991231T0200Z-nocturna.dump.age"
	ajeno="$SARC_BACKUP_DIR/notas.txt"
	touch "$viejo" "$viejo.sha256" "$reciente" "$ajeno"
	touch -t 202001010200 "$viejo" "$viejo.sha256" "$ajeno"
	run "$SCRIPT" nocturna
	[ "$status" -eq 0 ]
	[ ! -e "$viejo" ]
	[ ! -e "$viejo.sha256" ]
	[ -e "$reciente" ]
	[ -e "$ajeno" ]
}

@test "un fallo de pg_dump envia /fail y devuelve distinto de cero sin dejar copia" {
	export DOCKER_MODE=fallo
	run "$SCRIPT" nocturna
	[ "$status" -ne 0 ]
	grep -q "/fail" "$CURL_LOG"
	[ -z "$(ls "$SARC_BACKUP_DIR" | grep -E 'dump\.age' || true)" ]
}

@test "una copia nocturna correcta envia el latido ok" {
	run "$SCRIPT" nocturna
	[ "$status" -eq 0 ]
	grep -q "https://hc.example.invalid/ping/uuid" "$CURL_LOG"
	! grep -q "/fail" "$CURL_LOG"
}

@test "un tipo de copia desconocido devuelve 2" {
	run "$SCRIPT" otra
	[ "$status" -eq 2 ]
}

@test "sin clave publica devuelve 12" {
	rm "$SARC_BACKUP_RECIPIENT"
	run "$SCRIPT" manual
	[ "$status" -eq 12 ]
}
