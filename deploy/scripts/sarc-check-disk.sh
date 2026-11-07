#!/usr/bin/env bash
# Comprobación horaria de capacidad de disco (FR-037): falla con uso superior al umbral (80 % por omisión)
# y envía `ping_check disco` (ok o /fail).
# Variables: SARC_DISK_PATH (ruta a medir, por omisión /), SARC_DISK_MAX (porcentaje, 80), SARC_ENV_FILE.
# Códigos: 0 correcto; 1 uso por encima del umbral.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

env_file="${SARC_ENV_FILE:-/etc/sarc/sarc.env}"
if [[ -r "$env_file" ]]; then
	load_env "$env_file"
fi

path="${SARC_DISK_PATH:-/}"
max="${SARC_DISK_MAX:-80}"
used="$(df -P "$path" | awk 'NR == 2 { gsub("%", "", $5); print $5 }')"
if [[ ! "$used" =~ ^[0-9]+$ ]]; then
	log_error disco "no se pudo leer el uso de disco"
	ping_check disco fail || true
	exit 1
fi

if ((used > max)); then
	log_error disco "uso de disco ${used} % por encima del umbral (${max} %)"
	ping_check disco fail || true
	exit 1
fi
log_info disco "uso de disco ${used} % (umbral ${max} %)"
ping_check disco ok || log_error disco "no se pudo enviar el latido"
