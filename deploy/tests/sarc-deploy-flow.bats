#!/usr/bin/env bats
# Pruebas del flujo completo de sarc-deploy (pasos 7 a 11), el registro de publicaciones y los modos
# --primera-instalacion y --verificar (T025). Dobles y datos ficticios; ver helpers/deploy-env.bash.

load helpers/deploy-env

setup() { deploy_setup; }
teardown() { deploy_teardown; }

@test "camino feliz: registra COMPLETADA con los campos del registro de publicacion" {
	FAKE_APPLIED='V017\nV018\n' run "$DEPLOY" "$VERSION"
	[ "$status" -eq 0 ]
	[ "$(numero_de_lineas)" -eq 1 ]
	linea="$(ultima_linea)"
	[ "$(jq -r .result <<<"$linea")" = "COMPLETADA" ]
	[ "$(jq -r .version <<<"$linea")" = "$VERSION" ]
	[ "$(jq -r .mode <<<"$linea")" = "normal" ]
	[ "$(jq -r '.preDeployBackup' <<<"$linea")" = "sarc-20261120T0700Z-previa.dump.age" ]
	[ "$(jq -r '.migrationsApplied | join(",")' <<<"$linea")" = "V017,V018" ]
	# Campos del contrato (data-model.md): presentes aunque puedan ser nulos.
	jq -e 'has("version") and has("previousVersion") and has("runId") and has("approvedBy") and has("startedAt") and has("finishedAt") and has("preChecks") and has("preDeployBackup") and has("migrationsApplied") and has("postChecks") and has("result") and has("failedStep") and has("recovery")' <<<"$linea" >/dev/null
	[ "$(jq -r '.postChecks | length' <<<"$linea")" -ge 1 ]
	# Instalada, enlazada y sin mantenimiento.
	[ "$(basename "$(readlink -f "$SARC_CURRENT_LINK")")" = "$VERSION" ]
	[ -f "$SARC_RELEASES_DIR/$VERSION/backend/sarc-backend.jar" ]
	[ -f "$SARC_RELEASES_DIR/$VERSION/frontend/index.html" ]
	[ ! -e "$SARC_STATE_DIR/maintenance.flag" ]
	grep -q "systemctl stop sarc" "$CALLS"
	grep -q "systemctl start sarc" "$CALLS"
	# Salida para el flujo de GitHub con migrationsApplied[].
	[[ "$output" == *"SARC_DEPLOY_RESULT"* ]]
	[[ "$output" == *'"migrationsApplied":["V017","V018"]'* ]]
	# Sin secretos en la salida.
	[[ "$output" != *"clave-ficticia"* ]]
}

@test "sin migraciones nuevas migrationsApplied queda vacio" {
	run "$DEPLOY" "$VERSION"
	[ "$status" -eq 0 ]
	[ "$(jq -c .migrationsApplied <<<"$(ultima_linea)")" = "[]" ]
}

@test "si readiness no llega a UP devuelve 20, deja el mantenimiento y no reactiva la version anterior" {
	# Versión anterior ya instalada y enlazada.
	anterior="2026.11.19-50-aaaaaaa"
	mkdir -p "$SARC_RELEASES_DIR/$anterior"
	ln -s "$SARC_RELEASES_DIR/$anterior" "$SARC_CURRENT_LINK"
	READINESS=down FAKE_APPLIED='V017\n' run "$DEPLOY" "$VERSION"
	[ "$status" -eq 20 ]
	[ -e "$SARC_STATE_DIR/maintenance.flag" ]
	# current apunta a la nueva (no se vuelve a la anterior) y el servicio no se inicia otra vez.
	[ "$(basename "$(readlink -f "$SARC_CURRENT_LINK")")" = "$VERSION" ]
	[ "$(grep -c "systemctl start sarc" "$CALLS")" -eq 1 ]
	linea="$(ultima_linea)"
	[ "$(jq -r .result <<<"$linea")" = "FALLIDA" ]
	[ "$(jq -r .failedStep <<<"$linea")" = "9-readiness" ]
	[ "$(jq -r '.migrationsApplied | join(",")' <<<"$linea")" = "V017" ]
	[ "$(jq -r .previousVersion <<<"$linea")" = "$anterior" ]
	[[ "$output" == *"recuperar.md"* ]]
}

@test "si una comprobacion posterior falla devuelve 21 y deja el mantenimiento" {
	FAKE_RC_sarc_post_checks=21 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 21 ]
	[ -e "$SARC_STATE_DIR/maintenance.flag" ]
	linea="$(ultima_linea)"
	[ "$(jq -r .result <<<"$linea")" = "FALLIDA" ]
	[ "$(jq -r .failedStep <<<"$linea")" = "10-comprobaciones-posteriores" ]
}

@test "una falla previa tambien queda registrada como FALLIDA con su paso" {
	FAKE_RC_sarc_check_cert=13 run "$DEPLOY" "$VERSION"
	[ "$status" -eq 13 ]
	linea="$(ultima_linea)"
	[ "$(jq -r .result <<<"$linea")" = "FALLIDA" ]
	[ "$(jq -r .failedStep <<<"$linea")" = "3-certificado" ]
}

@test "primera instalacion: solo comprobaciones basicas, registra EN_CURSO, retira el mantenimiento y devuelve 0" {
	run "$DEPLOY" --primera-instalacion "$VERSION"
	[ "$status" -eq 0 ]
	grep -q "sarc-post-checks --solo-basicas" "$CALLS"
	linea="$(ultima_linea)"
	[ "$(jq -r .result <<<"$linea")" = "EN_CURSO" ]
	[ "$(jq -r .mode <<<"$linea")" = "primera" ]
	[ ! -e "$SARC_STATE_DIR/maintenance.flag" ]
	[ "$(basename "$(readlink -f "$SARC_CURRENT_LINK")")" = "$VERSION" ]
}

@test "una publicacion normal con la ultima EN_CURSO devuelve 10" {
	"$DEPLOY" --primera-instalacion "$VERSION" >/dev/null
	run "$DEPLOY" "$VERSION"
	[ "$status" -eq 10 ]
	[ "$(numero_de_lineas)" -eq 1 ]
}

@test "verificar sobre una version que no esta EN_CURSO se rechaza con 10" {
	run "$DEPLOY" --verificar "$VERSION"
	[ "$status" -eq 10 ]
	"$DEPLOY" "$VERSION" >/dev/null
	run "$DEPLOY" --verificar "$VERSION"
	[ "$status" -eq 10 ]
}

@test "verificar sobre una EN_CURSO registra COMPLETADA si todo pasa" {
	"$DEPLOY" --primera-instalacion "$VERSION" >/dev/null
	run "$DEPLOY" --verificar "$VERSION"
	[ "$status" -eq 0 ]
	[ "$(jq -r .result <<<"$(ultima_linea)")" = "COMPLETADA" ]
	[ "$(jq -r .mode <<<"$(ultima_linea)")" = "verificar" ]
	[ ! -e "$SARC_STATE_DIR/maintenance.flag" ]
	# Despues de COMPLETADA ya se admite otra publicacion normal.
	run "$DEPLOY" "$VERSION"
	[ "$status" -eq 0 ]
}

@test "verificar fallido activa el mantenimiento, registra FALLIDA y devuelve 21" {
	"$DEPLOY" --primera-instalacion "$VERSION" >/dev/null
	FAKE_RC_sarc_post_checks=21 run "$DEPLOY" --verificar "$VERSION"
	[ "$status" -eq 21 ]
	[ -e "$SARC_STATE_DIR/maintenance.flag" ]
	[ "$(jq -r .result <<<"$(ultima_linea)")" = "FALLIDA" ]
}

@test "conserva solo las 5 ultimas versiones sin tocar la actual ni la anterior" {
	for v in 2026.01.01-1-aaaaaaa 2026.01.02-2-bbbbbbb 2026.01.03-3-ccccccc 2026.01.04-4-ddddddd 2026.01.05-5-eeeeeee 2026.01.06-6-fffffff; do
		mkdir -p "$SARC_RELEASES_DIR/$v"
	done
	run "$DEPLOY" "$VERSION"
	[ "$status" -eq 0 ]
	[ "$(ls -1 "$SARC_RELEASES_DIR" | wc -l | tr -d ' ')" -eq 5 ]
	[ -d "$SARC_RELEASES_DIR/$VERSION" ]
	[ ! -d "$SARC_RELEASES_DIR/2026.01.01-1-aaaaaaa" ]
}
