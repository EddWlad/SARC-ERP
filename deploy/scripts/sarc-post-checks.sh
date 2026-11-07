#!/usr/bin/env bash
# Comprobaciones posteriores a una publicación (FR-015, FR-016; contracts/deploy-interface.md).
#
# Uso: sarc-post-checks [--solo-basicas] [<version>]
#   --solo-basicas  primera instalación: solo exige disponibilidad, base de datos y versión; ingreso, usuarios,
#                   módulos y archivos quedan PENDIENTE (aún no existen la cuenta ni el reporte de verificación).
#                   El veredicto es EN_CURSO (nunca COMPLETADA).
#   sin opción      exige todas; sin la credencial de la cuenta de verificación o sin SARC_VERIFY_REPORT_ID el
#                   veredicto es FALLIDA.
# Salida: una línea `comprobacion <nombre> <OK|FALLO|PENDIENTE>` por comprobación y `VEREDICTO <COMPLETADA|EN_CURSO|FALLIDA>`.
# Código: 0 con COMPLETADA o EN_CURSO; 21 con FALLIDA. Nunca imprime la credencial ni cuerpos de respuesta.
# Configuración: /etc/sarc/sarc.env y /etc/sarc/verificacion.env (SARC_VERIFY_USER, SARC_VERIFY_PASSWORD; 0600 root).
# La versión se lee de META-INF/build-info.properties dentro del JAR enlazado por `current` y de manifest.json,
# nunca de /actuator/info (exige ADMINISTRADOR). Veredicto en menos de 10 minutos: cada petición tiene 20 s de tope.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

solo_basicas=0
version=""
for arg in "$@"; do
	case "$arg" in
	--solo-basicas) solo_basicas=1 ;;
	-*) die 2 posteriores "opción no válida" ;;
	*) version="$arg" ;;
	esac
done

load_env "${SARC_ENV_FILE:-/etc/sarc/sarc.env}"
current="${SARC_CURRENT_LINK:-/opt/sarc/current}"
base_url="${SARC_POSTCHECK_BASE_URL:-https://${SARC_PUBLIC_IP:-}}"
local_readiness="${SARC_READINESS_URL:-http://127.0.0.1:8080/actuator/health/readiness}"

work="$(mktemp -d)"
trap 'rm -r -- "$work"' EXIT
chmod 0700 "$work"
jar="$work/cookies.txt"
body="$work/body"

declare -A resultado=()
orden=(disponibilidad base-de-datos version ingreso usuarios inventario consumo uptime reportes archivos)

registrar() {
	resultado["$1"]="$2"
}

# curl con validación TLS; en la VM de ensayo (SARC_DEPLOY_ENV=ensayo) acepta la CA de prueba.
http() {
	local -a extra=()
	if [[ -z "${SARC_POSTCHECK_BASE_URL:-}" ]]; then
		extra+=(--noproxy '*' --connect-to "${SARC_PUBLIC_IP:-}:443:127.0.0.1:443")
	fi
	if [[ "${SARC_DEPLOY_ENV:-produccion}" == "ensayo" && -n "${SARC_TEST_CA_FILE:-}" ]]; then
		extra+=(--cacert "$SARC_TEST_CA_FILE")
	fi
	curl -sS -m 20 -o "$body" -w '%{http_code}' -b "$jar" -c "$jar" "${extra[@]}" "$@" 2>/dev/null || echo "000"
}

# --- 1. Disponibilidad: página y readiness por HTTPS local, con el certificado público ---
disponible=FALLO
if [[ -n "${SARC_PUBLIC_IP:-}${SARC_POSTCHECK_BASE_URL:-}" ]]; then
	code="$(http "$base_url/")"
	if [[ "$code" == "200" ]]; then
		code="$(http "$base_url/actuator/health/readiness")"
		if [[ "$code" == "200" ]] && grep -q '"status":"UP"' "$body"; then
			disponible=OK
		fi
	fi
fi
registrar disponibilidad "$disponible"

# --- 2. Base de datos: readiness local incluye `db` ---
bd=FALLO
code="$(curl -sS -m 20 -o "$body" -w '%{http_code}' "$local_readiness" 2>/dev/null || echo 000)"
if [[ "$code" == "200" ]] && grep -q '"status":"UP"' "$body"; then
	bd=OK
fi
registrar base-de-datos "$bd"

# --- 3. Versión: build-info del JAR y manifest.json ---
ver=FALLO
build_version="$(unzip -p "$current/backend/sarc-backend.jar" META-INF/build-info.properties 2>/dev/null | sed -n 's/^build\.version=//p' | tr -d '\r' | head -n 1 || true)"
manifest_version="$(jq -r '.version // empty' "$current/manifest.json" 2>/dev/null || true)"
esperada="${version:-$manifest_version}"
if [[ -n "$build_version" && -n "$esperada" && "$build_version" == "$esperada" && "$manifest_version" == "$esperada" ]]; then
	ver=OK
fi
registrar version "$ver"

# --- 4 a 10: requieren la cuenta y el reporte de verificación ---
if ((solo_basicas)); then
	for nombre in ingreso usuarios inventario consumo uptime reportes archivos; do
		registrar "$nombre" PENDIENTE
	done
else
	for nombre in ingreso usuarios inventario consumo uptime reportes archivos; do
		registrar "$nombre" FALLO
	done
	verif_env="${SARC_VERIFY_ENV_FILE:-/etc/sarc/verificacion.env}"
	if [[ -r "$verif_env" ]]; then
		load_env "$verif_env"
	fi
	if [[ -n "${SARC_VERIFY_USER:-}" && -n "${SARC_VERIFY_PASSWORD:-}" && "${SARC_VERIFY_USER}" != "<definir>" ]]; then
		export SARC_VERIFY_USER SARC_VERIFY_PASSWORD
		# La SPA primero recibe la cookie XSRF-TOKEN en cualquier petición y la reenvía como X-XSRF-TOKEN.
		http "$base_url/api/v1/auth/me" >/dev/null
		xsrf="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "$jar" | tail -n 1)"
		# La credencial se pasa por entorno y entrada estándar: nunca en la lista de argumentos.
		code="$(jq -n '{email: env.SARC_VERIFY_USER, password: env.SARC_VERIFY_PASSWORD}' \
			| http "$base_url/api/v1/auth/login" -X POST -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" -d @-)"
		if [[ "$code" == "200" ]]; then
			registrar ingreso OK
			[[ "$(http "$base_url/api/v1/auth/me")" == "200" ]] && registrar usuarios OK
			[[ "$(http "$base_url/api/v1/equipment?page=0&size=1")" == "200" ]] && registrar inventario OK
			hoy="$(date -u +%Y-%m-%d)"
			[[ "$(http "$base_url/api/v1/energy/summary?from=$hoy&to=$hoy")" == "200" ]] && registrar consumo OK
			[[ "$(http "$base_url/api/v1/uptime/scope/connectors?q=a")" == "200" ]] && registrar uptime OK
			[[ "$(http "$base_url/api/v1/reports/issued?page=0&size=1")" == "200" ]] && registrar reportes OK
			if [[ -n "${SARC_VERIFY_REPORT_ID:-}" && "$SARC_VERIFY_REPORT_ID" != "<definir>" ]]; then
				code="$(http "$base_url/api/v1/reports/issued/${SARC_VERIFY_REPORT_ID}/download")"
				if [[ "$code" == "200" && -s "$body" ]]; then
					registrar archivos OK
				fi
			fi
		fi
	fi
fi

# --- Resultado ---
fallo=0
pendiente=0
for nombre in "${orden[@]}"; do
	printf 'comprobacion %s %s\n' "$nombre" "${resultado[$nombre]}"
	case "${resultado[$nombre]}" in
	FALLO) fallo=1 ;;
	PENDIENTE) pendiente=1 ;;
	esac
done

if ((fallo)); then
	echo "VEREDICTO FALLIDA"
	exit 21
fi
if ((solo_basicas)); then
	echo "VEREDICTO EN_CURSO"
	exit 0
fi
if ((pendiente)); then
	echo "VEREDICTO FALLIDA"
	exit 21
fi
echo "VEREDICTO COMPLETADA"
exit 0
