#!/usr/bin/env bash
# Comprobación diaria del certificado HTTPS (FR-033, FR-033a): existencia, vigencia (>= 72 h) y cadena.
#
# - Vigencia: `openssl x509 -checkend 259200` (72 h).
# - Cadena: `openssl verify` contra el almacén de confianza del SISTEMA (y, si SARC_PUBLIC_IP está definida, la IP).
# - Modo de ensayo: solo con SARC_DEPLOY_ENV=ensayo se admite SARC_TEST_CA_FILE (CA de prueba generada para la VM).
#   En producción (o sin SARC_DEPLOY_ENV) definir SARC_TEST_CA_FILE es un error: nunca se confía en una CA de prueba.
# - Envía `ping_check certificado` (ok o /fail). Lo usa también `sarc-deploy` en el paso 3.
# Configuración: /etc/sarc/sarc.env (SARC_ENV_FILE). Certificado: SARC_CERT_FILE o
# /etc/letsencrypt/live/<SARC_PUBLIC_IP>/fullchain.pem.
# Códigos: 0 correcto; 13 certificado inexistente, inválido o próximo a vencer.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

env_file="${SARC_ENV_FILE:-/etc/sarc/sarc.env}"
if [[ -r "$env_file" ]]; then
	load_env "$env_file"
fi

fallo() {
	log_error certificado "$1"
	ping_check certificado fail || true
	exit 13
}

if [[ -n "${SARC_TEST_CA_FILE:-}" && "${SARC_DEPLOY_ENV:-produccion}" != "ensayo" ]]; then
	fallo "SARC_TEST_CA_FILE solo se admite con SARC_DEPLOY_ENV=ensayo"
fi

cert="${SARC_CERT_FILE:-}"
if [[ -z "$cert" ]]; then
	[[ -n "${SARC_PUBLIC_IP:-}" ]] || fallo "falta SARC_PUBLIC_IP o SARC_CERT_FILE"
	cert="/etc/letsencrypt/live/${SARC_PUBLIC_IP}/fullchain.pem"
fi
[[ -r "$cert" ]] || fallo "no existe el certificado"

if ! openssl x509 -in "$cert" -noout -checkend 259200 >/dev/null 2>&1; then
	fallo "el certificado vence en menos de 72 h o ya venció"
fi

verify_args=(-untrusted "$cert")
if [[ -n "${SARC_TEST_CA_FILE:-}" ]]; then
	[[ -r "$SARC_TEST_CA_FILE" ]] || fallo "no existe la CA de prueba indicada"
	verify_args+=(-CAfile "$SARC_TEST_CA_FILE")
fi
if [[ -n "${SARC_PUBLIC_IP:-}" ]]; then
	verify_args+=(-verify_ip "$SARC_PUBLIC_IP")
fi
if ! openssl verify "${verify_args[@]}" "$cert" >/dev/null 2>&1; then
	fallo "la cadena del certificado no valida contra el almacén de confianza"
fi

log_info certificado "certificado válido con más de 72 h de vigencia"
ping_check certificado ok || log_error certificado "no se pudo enviar el latido"
