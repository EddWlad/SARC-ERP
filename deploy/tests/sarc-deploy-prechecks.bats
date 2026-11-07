#!/usr/bin/env bats
# Pruebas de las comprobaciones previas de sarc-deploy (pasos 0 a 6) y del comando forzado (T023).
# Dobles de curl, docker, systemctl y de los scripts hermanos; datos ficticios. Ver helpers/deploy-env.bash.

load helpers/deploy-env

setup() { deploy_setup; }
teardown() { deploy_teardown; }

# Una comprobación previa fallida devuelve su código, no detiene el servicio y nunca instala nada.
verificar_sin_cambios() {
	! grep -q "systemctl stop" "$CALLS" 2>/dev/null
	[ ! -e "$SARC_STATE_DIR/maintenance.flag" ]
	[ ! -e "$SARC_CURRENT_LINK" ]
}

@test "paso 0: con el bloqueo tomado devuelve 10" {
	command -v flock >/dev/null || skip "flock no disponible"
	( exec 8>"$SARC_STATE_DIR/deploy.lock"; flock -n 8; sleep 3 ) &
	holder=$!
	sleep 1
	run "$DEPLOY" "$VERSION"
	kill "$holder" 2>/dev/null || true
	wait "$holder" 2>/dev/null || true
	[ "$status" -eq 10 ]
	verificar_sin_cambios
}

@test "paso 1: sin Release o con suma distinta devuelve 11" {
	FAKE_DOWNLOAD_FAIL=1 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 11 ]
	verificar_sin_cambios
	echo "alterado" >>"$TMP/fixture/$VERSION/sarc-backend-$VERSION.jar"
	run "$DEPLOY" "$VERSION"
	[ "$status" -eq 11 ]
	verificar_sin_cambios
}

@test "paso 2: una variable ausente o sin definir devuelve 12 sin imprimir valores" {
	sed -i 's/^CLOUDINARY_API_SECRET=.*/CLOUDINARY_API_SECRET=<definir>/' "$SARC_ENV_FILE"
	run "$DEPLOY" "$VERSION"
	[ "$status" -eq 12 ]
	[[ "$output" == *"CLOUDINARY_API_SECRET"* ]]
	[[ "$output" != *"clave-ficticia"* ]]
	[[ "$output" != *"secreto-ficticio"* ]]
	verificar_sin_cambios
}

@test "paso 2b: sin prueba de recepcion vigente devuelve 18" {
	FAKE_RC_sarc_alerts_gate=18 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 18 ]
	verificar_sin_cambios
}

@test "paso 3: certificado invalido o proximo a vencer devuelve 13" {
	FAKE_RC_sarc_check_cert=13 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 13 ]
	verificar_sin_cambios
}

@test "paso 4: sin recibo de copia local reciente devuelve 14" {
	FAKE_RC_sarc_backup_freshness=14 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 14 ]
	verificar_sin_cambios
}

@test "paso 5: esquema incoherente devuelve 15" {
	FAKE_RC_sarc_schema_check=15 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 15 ]
	verificar_sin_cambios
}

@test "paso 6: falla la copia previa y devuelve 16" {
	FAKE_RC_sarc_backup=1 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 16 ]
	verificar_sin_cambios
}

@test "con --primera-instalacion los pasos 4 y 6 no se ejecutan y los demas si" {
	run "$DEPLOY" --primera-instalacion "$VERSION"
	[ "$status" -eq 0 ]
	! grep -q "sarc-backup-freshness" "$CALLS"
	! grep -q "^sarc-backup " "$CALLS"
	! grep -q "sarc-schema-check" "$CALLS"
	grep -q "sarc-alerts-gate" "$CALLS"
	grep -q "sarc-check-cert" "$CALLS"
}

@test "--primera-instalacion se rechaza si la base tiene tablas de SARC" {
	FAKE_TABLES=12 run "$DEPLOY" --primera-instalacion "$VERSION"
	[ "$status" -eq 15 ]
	verificar_sin_cambios
}

# ---------- Comando forzado ----------

doble_sudo() {
	{
		echo '#!/usr/bin/env bash'
		echo 'printf "%s\n" "$*" >"$TMP/sudo-args.txt"'
	} >"$TMP/bin/sudo"
	chmod +x "$TMP/bin/sudo"
}

@test "wrapper: las tres formas validas se ejecutan con sudo -n" {
	doble_sudo
	SSH_ORIGINAL_COMMAND="deploy $VERSION" run "$WRAPPER"
	[ "$status" -eq 0 ]
	[ "$(cat "$TMP/sudo-args.txt")" = "-n /usr/local/sbin/sarc-deploy $VERSION" ]
	SSH_ORIGINAL_COMMAND="deploy --primera-instalacion $VERSION" run "$WRAPPER"
	[ "$(cat "$TMP/sudo-args.txt")" = "-n /usr/local/sbin/sarc-deploy --primera-instalacion $VERSION" ]
	SSH_ORIGINAL_COMMAND="deploy --verificar $VERSION" run "$WRAPPER"
	[ "$(cat "$TMP/sudo-args.txt")" = "-n /usr/local/sbin/sarc-deploy --verificar $VERSION" ]
}

@test "wrapper: rechaza con 2 y sin repetir el texto cualquier otra entrada" {
	doble_sudo
	entradas=(
		"deploy $VERSION extra"
		"deploy $VERSION; id"
		"deploy $VERSION && id"
		"deploy $VERSION | cat"
		"deploy \$(id) $VERSION"
		"deploy \`id\` $VERSION"
		"deploy 2026.11.20-57-E85C910"
		"deploy --otro $VERSION"
		"ls -la"
		""
	)
	for entrada in "${entradas[@]}"; do
		SSH_ORIGINAL_COMMAND="$entrada" run "$WRAPPER"
		[ "$status" -eq 2 ]
		[[ "$output" != *"id"*"$VERSION"* ]]
		[[ "$output" != *"ls -la"* ]]
		[ ! -e "$TMP/sudo-args.txt" ]
	done
	# Salto de linea.
	SSH_ORIGINAL_COMMAND=$'deploy '"$VERSION"$'\nid' run "$WRAPPER"
	[ "$status" -eq 2 ]
	[ ! -e "$TMP/sudo-args.txt" ]
}

@test "sarc-deploy invocado directamente con argumentos no validos devuelve 2 sin ejecutar pasos" {
	run "$DEPLOY" "version-incorrecta"
	[ "$status" -eq 2 ]
	[[ "$output" != *"version-incorrecta"* ]]
	run "$DEPLOY" --otra-cosa "$VERSION"
	[ "$status" -eq 2 ]
	run "$DEPLOY"
	[ "$status" -eq 2 ]
	run "$DEPLOY" --verificar
	[ "$status" -eq 2 ]
	[ ! -e "$CALLS" ]
}
