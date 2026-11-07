#!/usr/bin/env bash
# Publicación de una versión candidata de SARC en el VPS (contrato: contracts/deploy-interface.md).
# Se instala como /usr/local/sbin/sarc-deploy (root:root 0755) y solo se ejecuta a través del comando forzado
# de la cuenta `sarc-deploy` (sudo -n, una única regla sudoers).
#
# Uso:
#   sarc-deploy <version>                          publicación normal (pasos 0 a 11)
#   sarc-deploy --primera-instalacion <version>    base vacía: sin pasos 4 y 6; termina EN_CURSO
#   sarc-deploy --verificar <version>              cierra una primera instalación EN_CURSO (COMPLETADA o FALLIDA)
# <version> = AAAA.MM.DD-<n>-<sha7>. Cualquier otra forma de argumentos termina con código 2 sin repetir la entrada.
#
# Códigos: 0 correcto (COMPLETADA o EN_CURSO) · 2 argumentos no válidos · 10 otra publicación en curso o primera
# instalación sin verificar · 11 suma o Release · 12 configuración · 13 certificado · 14 sin copia local reciente ·
# 15 esquema incoherente · 16 copia previa · 17 instalación · 18 prueba de recepción · 20 readiness · 21 comprobaciones
# posteriores. 2 y 10–18: nada cambió en producción. 20–21: producción en mantenimiento.
# Escribe una línea por intento en /var/lib/sarc/deploy-history.jsonl y, al terminar, una línea
# `SARC_DEPLOY_RESULT <json>` con el resultado y migrationsApplied[] (la lee el flujo `publicar`).
# Nunca imprime variables de entorno, credenciales ni respuestas de la aplicación.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

# ---------- Argumentos (se vuelven a validar aunque el comando forzado ya lo hizo) ----------
uso() {
	log_error uso "argumentos no válidos: se esperaba [--primera-instalacion|--verificar] <version>"
	exit 2
}
version_re='^[0-9]{4}\.[0-9]{2}\.[0-9]{2}-[0-9]+-[0-9a-f]{7}$'
mode="normal"
version=""
case $# in
1) version="$1" ;;
2)
	case "$1" in
	--primera-instalacion) mode="primera" ;;
	--verificar) mode="verificar" ;;
	*) uso ;;
	esac
	version="$2"
	;;
*) uso ;;
esac
[[ "$version" =~ $version_re ]] || uso

# ---------- Rutas y configuración ----------
state_dir="${SARC_STATE_DIR:-/var/lib/sarc}"
releases_dir="${SARC_RELEASES_DIR:-/opt/sarc/releases}"
current_link="${SARC_CURRENT_LINK:-/opt/sarc/current}"
backup_dir="${SARC_BACKUP_DIR:-/var/backups/sarc}"
history="$state_dir/deploy-history.jsonl"
maintenance_flag="$state_dir/maintenance.flag"
downloads="$state_dir/downloads/$version"
ready_timeout="${SARC_READY_TIMEOUT:-180}"
ready_interval="${SARC_READY_INTERVAL:-5}"
readiness_url="${SARC_READINESS_URL:-http://127.0.0.1:8080/actuator/health/readiness}"

step="inicio"
record=0
result=""
failed_step=""
previous_version=""
backup_file=""
rank_before=0
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
pre_checks=()
post_checks=()
migrations_applied=()

# shellcheck disable=SC2329  # se invoca desde write_history y finish (trap EXIT)
json_array() {
	if (($# == 0)); then
		echo '[]'
	else
		printf '%s\n' "$@" | jq -R . | jq -cs .
	fi
}

# shellcheck disable=SC2329
write_history() {
	local approved="${SARC_APPROVED_BY:-}" run="${SARC_RUN_ID:-}"
	jq -cn --arg version "$version" --arg mode "$mode" --arg previous "$previous_version" \
		--arg run "$run" --arg approved "$approved" --arg started "$started_at" \
		--arg finished "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg backup "$backup_file" \
		--arg result "$result" --arg failed "$failed_step" \
		--argjson pre "$(json_array "${pre_checks[@]}")" --argjson post "$(json_array "${post_checks[@]}")" \
		--argjson migrations "$(json_array "${migrations_applied[@]}")" \
		'def nulo: if . == "" then null else . end;
		 {version:$version, mode:$mode, previousVersion:($previous|nulo), runId:($run|nulo),
		  approvedBy:($approved|nulo), startedAt:$started, finishedAt:$finished, preChecks:$pre,
		  preDeployBackup:($backup|nulo), migrationsApplied:$migrations, postChecks:$post,
		  result:$result, failedStep:($failed|nulo), recovery:null}' >>"$history"
}

# shellcheck disable=SC2329
finish() {
	local rc=$?
	if ((record == 1)); then
		if [[ -z "$result" && $rc -ne 0 ]]; then
			result="FALLIDA"
			failed_step="$step"
		fi
		if [[ -n "$result" ]]; then
			write_history
			printf 'SARC_DEPLOY_RESULT %s\n' "$(jq -cn --arg v "$version" --arg m "$mode" --arg r "$result" \
				--argjson mig "$(json_array "${migrations_applied[@]}")" '{version:$v, mode:$m, result:$r, migrationsApplied:$mig}')"
		fi
	fi
	exit "$rc"
}
trap finish EXIT

fail() { # fail <codigo> <paso> <mensaje>
	local code="$1"
	failed_step="$2"
	result="FALLIDA"
	log_error "$2" "$3"
	exit "$code"
}

psql_sarc() {
	compose exec -T postgres psql -U sarc -d sarc -X -A -t -v ON_ERROR_STOP=1 -c "$1"
}

wait_database() {
	local i
	for ((i = 0; i < 30; i++)); do
		if compose exec -T postgres pg_isready -U sarc -d sarc >/dev/null 2>&1; then
			return 0
		fi
		sleep 2
	done
	return 1
}

# ---------- Configuración ----------
mkdir -p "$state_dir"
load_env "${SARC_ENV_FILE:-/etc/sarc/sarc.env}"

# ---------- Paso 0: bloqueo exclusivo y estado de la última publicación ----------
step="0-bloqueo"
with_lock "$state_dir/deploy.lock"
last_line=""
[[ -s "$history" ]] && last_line="$(tail -n 1 "$history")"
last_json="${last_line:-}"
[[ -n "$last_json" ]] || last_json='{}'
last_result="$(jq -r '.result // empty' <<<"$last_json" 2>/dev/null || true)"
if [[ "$mode" == "verificar" ]]; then
	last_version="$(jq -r '.version // empty' <<<"$last_line" 2>/dev/null || true)"
	if [[ "$last_result" != "EN_CURSO" || "$last_version" != "$version" ]]; then
		die 10 "0-estado" "no hay una primera instalación EN_CURSO para esa versión"
	fi
	if [[ "$(basename "$(readlink -f "$current_link" 2>/dev/null || echo)")" != "$version" ]]; then
		die 10 "0-estado" "la versión enlazada en current no es la que se quiere verificar"
	fi
else
	if [[ "$last_result" == "EN_CURSO" ]]; then
		die 10 "0-estado" "hay una primera instalación sin verificar: ejecutar el modo verificar antes de otra publicación"
	fi
fi
record=1
pre_checks+=("0-bloqueo:ok")

# ---------- Modo verificar: solo comprobaciones posteriores completas ----------
if [[ "$mode" == "verificar" ]]; then
	step="10-comprobaciones-posteriores"
	previous_version="$version"
	set +e
	salida="$(sarc-post-checks "$version")"
	rc=$?
	set -e
	while IFS= read -r linea; do
		[[ "$linea" == comprobacion\ * ]] && post_checks+=("${linea#comprobacion }")
	done <<<"$salida"
	if ((rc != 0)); then
		touch "$maintenance_flag"
		fail 21 "10-comprobaciones-posteriores" "las comprobaciones posteriores fallaron: el servicio queda en mantenimiento"
	fi
	rm -f "$maintenance_flag"
	result="COMPLETADA"
	log_info verificar "verificación completada para la versión $version"
	exit 0
fi

# ---------- Paso 1: Release, sumas y manifiesto ----------
step="1-sumas"
base_url="${SARC_RELEASE_BASE_URL:-}"
if [[ -z "$base_url" ]]; then
	require_env SARC_GITHUB_REPO
	base_url="https://github.com/${SARC_GITHUB_REPO}/releases/download"
fi
jar_name="sarc-backend-${version}.jar"
tgz_name="sarc-frontend-${version}.tar.gz"
mkdir -p "$downloads"
for asset in SHA256SUMS manifest.json "$jar_name" "$tgz_name"; do
	if ! curl -fsSL -m 300 --retry 3 -o "$downloads/$asset" "$base_url/$version/$asset"; then
		fail 11 "1-sumas" "no se pudo descargar $asset del Release $version"
	fi
done
if ! (cd "$downloads" && sha256sum -c SHA256SUMS >/dev/null 2>&1); then
	fail 11 "1-sumas" "las sumas SHA-256 del Release no coinciden con los archivos descargados"
fi
manifest_version="$(jq -r '.version // empty' "$downloads/manifest.json" 2>/dev/null || true)"
if [[ "$manifest_version" != "$version" ]]; then
	fail 11 "1-sumas" "el manifiesto no corresponde a la versión solicitada"
fi
for asset in "$jar_name" "$tgz_name"; do
	declared="$(jq -r --arg n "$asset" '.artifacts[] | select(.name == $n) | .sha256' "$downloads/manifest.json")"
	actual="$(sha256sum "$downloads/$asset" | cut -d' ' -f1)"
	if [[ -z "$declared" || "$declared" != "$actual" ]]; then
		fail 11 "1-sumas" "la suma de $asset no coincide con el manifiesto"
	fi
done
pre_checks+=("1-sumas:ok")

# ---------- Paso 2: configuración obligatoria (sin imprimir valores) ----------
step="2-configuracion"
required=(SPRING_PROFILES_ACTIVE SARC_DB_URL SARC_DB_USER SARC_DB_PASSWORD POSTGRES_PASSWORD
	CLOUDINARY_CLOUD_NAME CLOUDINARY_API_KEY CLOUDINARY_API_SECRET SARC_STORAGE_CLOUDINARY_ROOT_FOLDER
	SARC_DEPLOY_ENV SARC_PUBLIC_IP)
if [[ "$mode" == "primera" ]]; then
	required+=(SARC_BOOTSTRAP_ADMIN_EMAIL SARC_BOOTSTRAP_ADMIN_PASSWORD)
fi
pendientes=()
for name in "${required[@]}"; do
	value="${!name:-}"
	if [[ -z "$value" || "$value" == "<definir>" ]]; then
		pendientes+=("$name")
	fi
done
if ((${#pendientes[@]} > 0)); then
	fail 12 "2-configuracion" "variables obligatorias ausentes o sin definir: ${pendientes[*]}"
fi
pre_checks+=("2-configuracion:ok")

# ---------- Paso 2b: prueba de recepción de avisos vigente ----------
step="2b-prueba-de-recepcion"
if ! sarc-alerts-gate; then
	fail 18 "2b-prueba-de-recepcion" "no hay una prueba de recepción de avisos vigente"
fi
pre_checks+=("2b-prueba-de-recepcion:ok")

# ---------- Paso 3: certificado válido con al menos 72 h ----------
step="3-certificado"
if ! sarc-check-cert; then
	fail 13 "3-certificado" "el certificado HTTPS es inválido o vence en menos de 72 h"
fi
pre_checks+=("3-certificado:ok")

# ---------- Pasos 4 a 6: copia local, esquema y copia previa (la primera instalación no los necesita) ----------
step="5-esquema"
if ! compose up -d postgres >/dev/null 2>&1 || ! wait_database; then
	fail 15 "5-esquema" "la base de datos no está disponible"
fi
if [[ "$mode" == "primera" ]]; then
	tablas="$(psql_sarc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'" | tr -d '[:space:]')"
	if [[ "$tablas" != "0" ]]; then
		fail 15 "5-esquema" "la base ya tiene tablas de SARC: --primera-instalacion solo se acepta con la base vacía"
	fi
	pre_checks+=("5-esquema:base-vacia")
else
	step="4-copia-local"
	if ! sarc-backup-freshness; then
		fail 14 "4-copia-local" "sin copia local reciente verificable (menos de 24 h)"
	fi
	pre_checks+=("4-copia-local:ok")

	step="5-esquema"
	if ! sarc-schema-check "$downloads/manifest.json"; then
		fail 15 "5-esquema" "el historial de migraciones no es coherente con la versión a publicar"
	fi
	pre_checks+=("5-esquema:ok")
	rank_before="$(psql_sarc "SELECT coalesce(max(installed_rank), 0) FROM flyway_schema_history" | tr -d '[:space:]')"

	step="6-copia-previa"
	if ! backup_file="$(sarc-backup previa | tail -n 1)" || [[ -z "$backup_file" ]]; then
		backup_file=""
		fail 16 "6-copia-previa" "falló la copia previa a la publicación"
	fi
	if ! (cd "$backup_dir" && sha256sum -c "${backup_file}.sha256" >/dev/null 2>&1) || [[ ! -s "$backup_dir/$backup_file" ]]; then
		fail 16 "6-copia-previa" "la copia previa no pasó la verificación de integridad"
	fi
	pre_checks+=("6-copia-previa:ok")
fi

# ---------- Paso 7: mantenimiento y detener la versión anterior ----------
step="7-mantenimiento"
if [[ -e "$current_link" ]]; then
	previous_version="$(basename "$(readlink -f "$current_link")")"
fi
touch "$maintenance_flag"
systemctl stop sarc

# ---------- Paso 8: instalar en releases/<version> y cambiar `current` de forma atómica ----------
step="8-instalacion"
dest="$releases_dir/$version"
{
	mkdir -p "$dest/backend" "$dest/frontend" &&
		cp "$downloads/$jar_name" "$dest/backend/sarc-backend.jar" &&
		tar -xzf "$downloads/$tgz_name" -C "$dest/frontend" &&
		cp "$downloads/manifest.json" "$downloads/SHA256SUMS" "$dest/" &&
		ln -sfn "$dest" "${current_link}.nuevo" &&
		mv -T "${current_link}.nuevo" "$current_link"
} || fail 17 "8-instalacion" "no se pudo instalar la versión (¿disco lleno?)"

# ---------- Paso 9: arrancar (Flyway migra) y esperar readiness ----------
step="9-readiness"
systemctl start sarc
ready=0
waited=0
while ((waited < ready_timeout)); do
	respuesta="$(curl -fsS -m 5 "$readiness_url" 2>/dev/null || true)"
	if [[ "$respuesta" == *'"status":"UP"'* ]]; then
		ready=1
		break
	fi
	sleep "$ready_interval"
	waited=$((waited + ready_interval))
done

# Migraciones aplicadas en este intento (filas nuevas de flyway_schema_history con éxito).
rank_before="${rank_before:-0}"
mapfile -t migrations_applied < <(psql_sarc "SELECT 'V' || version FROM flyway_schema_history WHERE success AND version IS NOT NULL AND installed_rank > ${rank_before} ORDER BY installed_rank" 2>/dev/null | tr -d '\r' | grep -v '^$' || true)

if ((ready == 0)); then
	ultima="$(psql_sarc "SELECT coalesce(max('V' || version), 'ninguna') FROM flyway_schema_history WHERE success" 2>/dev/null | tr -d '[:space:]' || echo desconocida)"
	log_error "9-readiness" "la aplicación no llegó a UP en ${ready_timeout} s; última migración aplicada: ${ultima:-desconocida}; migraciones aplicadas en este intento: ${#migrations_applied[@]}"
	log_error "9-readiness" "no se reactiva la versión anterior; seguir docs/operacion/recuperar.md"
	fail 20 "9-readiness" "readiness no llegó a UP; producción queda en mantenimiento"
fi

# ---------- Paso 10: comprobaciones posteriores ----------
step="10-comprobaciones-posteriores"
flags=()
if [[ "$mode" == "primera" ]]; then
	flags=(--solo-basicas)
fi
set +e
salida="$(sarc-post-checks "${flags[@]}" "$version")"
rc=$?
set -e
while IFS= read -r linea; do
	[[ "$linea" == comprobacion\ * ]] && post_checks+=("${linea#comprobacion }")
done <<<"$salida"
if ((rc != 0)); then
	fail 21 "10-comprobaciones-posteriores" "alguna comprobación posterior falló; producción queda en mantenimiento"
fi

# ---------- Paso 11: fin del mantenimiento y registro ----------
step="11-fin"
rm -f "$maintenance_flag"
if [[ "$mode" == "primera" ]]; then
	result="EN_CURSO"
	log_info publicar "primera instalación pendiente de verificación (EN_CURSO) para $version"
else
	result="COMPLETADA"
	log_info publicar "publicación completada: $version"
fi

# Conserva las 5 últimas versiones (nunca la actual ni la anterior).
# shellcheck disable=SC2012  # nombres de versión con formato fijo
while IFS= read -r old; do
	[[ -z "$old" || "$old" == "$version" || "$old" == "$previous_version" ]] && continue
	rm -rf -- "${releases_dir:?}/${old}"
done < <(ls -1 "$releases_dir" 2>/dev/null | sort -V -r | tail -n +6)

exit 0
