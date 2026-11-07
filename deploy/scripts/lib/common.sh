#!/usr/bin/env bash
# Biblioteca común de los scripts de SARC en el VPS.
# Se instala como /usr/local/lib/sarc/common.sh y los scripts la cargan con:
#   source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"
# Las pruebas definen SARC_LIB_DIR=deploy/scripts/lib.
# Sin valores reales: ninguna función imprime variables de entorno ni cuerpos de respuesta.

set -Eeuo pipefail

# log_info <paso> <mensaje...>  ->  "<fecha UTC> INFO <paso> <mensaje>" en la salida estándar.
log_info() {
	local step="${1:-general}"
	shift || true
	printf '%s INFO %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$step" "$*"
}

# log_error <paso> <mensaje...>  ->  "<fecha UTC> ERROR <paso> <mensaje>" en la salida de errores.
log_error() {
	local step="${1:-general}"
	shift || true
	printf '%s ERROR %s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$step" "$*" >&2
}

# die <codigo> <paso> <mensaje...>: registra el error y termina con el código indicado.
die() {
	local code="$1"
	local step="$2"
	shift 2
	log_error "$step" "$*"
	exit "$code"
}

# require_env NOMBRE...: termina con código 12 nombrando solo las variables ausentes o vacías.
# Nunca imprime los valores.
require_env() {
	local name
	local missing=()
	for name in "$@"; do
		if [[ -z "${!name:-}" ]]; then
			missing+=("$name")
		fi
	done
	if ((${#missing[@]} > 0)); then
		log_error configuracion "variables obligatorias ausentes: ${missing[*]}"
		exit 12
	fi
}

# ping_check <nombre> [ok|fail|start]: envía el latido a la URL HC_URL_<NOMBRE> (en mayúsculas y
# con '_' en lugar de '-') sin cuerpo. Sin URL definida se omite con un aviso; nunca imprime la URL.
ping_check() {
	local name="${1:?nombre de la comprobación}"
	local signal="${2:-ok}"
	local var="HC_URL_${name^^}"
	var="${var//-/_}"
	local url="${!var:-}"
	if [[ -z "$url" ]]; then
		log_error latido "sin URL de latido para '$name' (variable $var); no se envía"
		return 0
	fi
	case "$signal" in
	ok) ;;
	fail) url="${url%/}/fail" ;;
	start) url="${url%/}/start" ;;
	*) die 2 latido "señal no válida: $signal" ;;
	esac
	if ! curl -fsS -m 10 --retry 3 -o /dev/null "$url"; then
		log_error latido "no se pudo enviar el latido '$name' ($signal)"
		return 1
	fi
	return 0
}

# with_lock <archivo>: toma un bloqueo exclusivo no bloqueante (flock -n) que dura hasta que
# termina el proceso. Si otra instancia lo tiene, termina con código 10.
with_lock() {
	local lock_file="${1:?archivo de bloqueo}"
	if ! exec 9>"$lock_file"; then
		log_error bloqueo "no se pudo abrir el archivo de bloqueo"
		exit 10
	fi
	if ! flock -n 9; then
		log_error bloqueo "otra operación está en curso"
		exit 10
	fi
}

# compose <argumentos...>: docker compose de producción con la configuración de /etc/sarc.
# Rutas redefinibles para las pruebas: SARC_ENV_FILE y SARC_COMPOSE_FILE.
compose() {
	docker compose --env-file "${SARC_ENV_FILE:-/etc/sarc/sarc.env}" -p sarc \
		-f "${SARC_COMPOSE_FILE:-/opt/sarc/compose/compose.prod.yaml}" "$@"
}

# load_env <archivo>: carga líneas CLAVE=valor de un archivo de entorno sin interpretarlas como shell
# (los valores pueden llevar <, >, $ o espacios). Ignora comentarios y líneas vacías; quita comillas
# simples o dobles exteriores. Nunca imprime los valores. Falla con 12 si el archivo no existe.
load_env() {
	local file="${1:?archivo de entorno}"
	local line key value
	if [[ ! -r "$file" ]]; then
		log_error configuracion "no se puede leer el archivo de configuración"
		exit 12
	fi
	while IFS= read -r line || [[ -n "$line" ]]; do
		[[ "$line" =~ ^[[:space:]]*# ]] && continue
		if [[ "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
			key="${BASH_REMATCH[1]}"
			value="${BASH_REMATCH[2]}"
			if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then
				value="${BASH_REMATCH[1]}"
			fi
			export "$key=$value"
		fi
	done <"$file"
}
