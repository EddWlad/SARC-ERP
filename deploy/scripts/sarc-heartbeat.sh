#!/usr/bin/env bash
# Latidos cada 5 minutos hacia Healthchecks.io (contracts/health-and-alerts.md):
#   - `vps-latido`: se envía siempre; si el VPS cae o pierde la red, el latido falta y llega el aviso.
#   - `servicio`: solo si la readiness local (127.0.0.1) responde UP; si no, se envía /fail de inmediato
#     (sin esperar la gracia).
# Variables: SARC_READINESS_URL (por omisión http://127.0.0.1:8080/actuator/health/readiness), SARC_ENV_FILE.
# Siempre termina con 0: un latido que falla no debe marcar la unidad como fallida.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

env_file="${SARC_ENV_FILE:-/etc/sarc/sarc.env}"
if [[ -r "$env_file" ]]; then
	load_env "$env_file"
fi

ping_check vps-latido ok || true

url="${SARC_READINESS_URL:-http://127.0.0.1:8080/actuator/health/readiness}"
respuesta="$(curl -fsS -m 5 "$url" 2>/dev/null || true)"
if [[ "$respuesta" == *'"status":"UP"'* ]]; then
	ping_check servicio ok || true
else
	log_error servicio "readiness local no responde UP"
	ping_check servicio fail || true
fi
exit 0
