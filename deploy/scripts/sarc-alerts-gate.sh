#!/usr/bin/env bash
# Paso 2b de la publicación: exige una prueba de recepción de avisos vigente (FR-038a).
#
# Lee ${SARC_STATE_DIR:-/var/lib/sarc}/prueba-recepcion.ok, con una clave por línea:
#   fecha=<AAAA-MM-DDThh:mm:ssZ>
#   huella=<SHA-256 de /etc/sarc/avisos.conf en el momento de la prueba>
#   confirmado=<usuario de GitHub del responsable>
#   confirmado=<usuario de GitHub del suplente>
# Falla con 18 si el archivo no existe, si la huella no coincide con la de /etc/sarc/avisos.conf actual (cambiaron
# destinatarios o canales) o si falta la confirmación de dos personas distintas. No imprime direcciones de correo.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

estado="${SARC_STATE_DIR:-/var/lib/sarc}/prueba-recepcion.ok"
avisos="${SARC_AVISOS_CONF:-/etc/sarc/avisos.conf}"

[[ -r "$estado" ]] || die 18 avisos "no hay prueba de recepción de avisos (ver docs/operacion/avisos.md)"
[[ -r "$avisos" ]] || die 18 avisos "falta /etc/sarc/avisos.conf"

huella_actual="$(sha256sum "$avisos" | cut -d' ' -f1)"
huella_prueba="$(sed -n 's/^huella=//p' "$estado" | head -n 1 | tr -d '[:space:]')"
if [[ -z "$huella_prueba" || "$huella_prueba" != "$huella_actual" ]]; then
	die 18 avisos "la prueba de recepción no corresponde a los destinatarios y canales actuales: repetirla"
fi

confirmados="$(sed -n 's/^confirmado=//p' "$estado" | sed 's/[[:space:]]//g' | grep -v '^$' | sort -u | wc -l | tr -d ' ')"
if ((confirmados < 2)); then
	die 18 avisos "falta la confirmación de recepción del responsable y del suplente"
fi

fecha="$(sed -n 's/^fecha=//p' "$estado" | head -n 1 | tr -d '[:space:]')"
log_info avisos "prueba de recepción vigente (${fecha:-sin fecha})"
