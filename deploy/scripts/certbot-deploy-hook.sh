#!/usr/bin/env bash
# Hook de despliegue de Certbot (--deploy-hook): se ejecuta solo tras una emisión o renovación correcta.
# Valida la configuración de Nginx, la recarga y comprueba el certificado nuevo (envía el latido `certificado`).
# Instalar como /usr/local/sbin/certbot-deploy-hook y registrar con:
#   certbot renew --deploy-hook /usr/local/sbin/certbot-deploy-hook
# (o dejarlo en /etc/letsencrypt/renewal-hooks/deploy/).
# Códigos: 0 correcto; 1 la configuración de Nginx no es válida (no se recarga); 13 el certificado no pasa la comprobación.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

if ! nginx -t >/dev/null 2>&1; then
	log_error certbot-hook "la configuración de Nginx no es válida: no se recarga"
	exit 1
fi
systemctl reload nginx
log_info certbot-hook "Nginx recargado con el certificado nuevo"

# La comprobación completa (vigencia, cadena e IP) y el latido.
sarc-check-cert
