#!/usr/bin/env bash
# Paso 2b de la publicación: exige una prueba de recepción de avisos vigente (FR-038a).
#
# Lee ${SARC_STATE_DIR:-/var/lib/sarc}/prueba-recepcion.ok, con una clave por línea:
#   fecha=<AAAA-MM-DDThh:mm:ssZ>
#   huella=<SHA-256 de /etc/sarc/avisos.conf en el momento de la prueba>
#   confirmado=<usuario de GitHub del responsable>
#   confirmado=<usuario de GitHub del suplente>
# Falla con 18 si el archivo no existe, si la huella no coincide con la de /etc/sarc/avisos.conf actual (cambiaron
# destinatarios o canales) o si falta la confirmación de alguna persona designada.
# Solo la primera instalación y su verificación admiten una excepción explícita sin suplente.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

estado="${SARC_STATE_DIR:-/var/lib/sarc}/prueba-recepcion.ok"
avisos="${SARC_AVISOS_CONF:-/etc/sarc/avisos.conf}"
modo="${1:-normal}"
[[ $# -le 1 && "$modo" =~ ^(normal|primera|verificar)$ ]] || die 18 avisos "modo de comprobación inválido"

[[ -r "$estado" ]] || die 18 avisos "no hay prueba de recepción de avisos (ver docs/operacion/avisos.md)"
[[ -r "$avisos" ]] || die 18 avisos "falta /etc/sarc/avisos.conf"

huella_actual="$(sha256sum "$avisos" | cut -d' ' -f1)"
huella_prueba="$(sed -n 's/^huella=//p' "$estado" | head -n 1 | tr -d '[:space:]')"
if [[ -z "$huella_prueba" || "$huella_prueba" != "$huella_actual" ]]; then
	die 18 avisos "la prueba de recepción no corresponde a los destinatarios y canales actuales: repetirla"
fi

responsable="$(sed -n 's/^responsable_github=//p' "$avisos")"
suplente="$(sed -n 's/^suplente_github=//p' "$avisos")"
excepcion="$(sed -n 's/^excepcion_sin_suplente=//p' "$avisos")"
confirmados="$(sed -n 's/^confirmado=//p' "$estado")"
confirmo() {
	[[ "$1" =~ ^[a-zA-Z0-9-]+$ ]] && grep -Fxiq -- "$1" <<<"$confirmados"
}

confirmo "$responsable" || die 18 avisos "falta la confirmación de recepción del responsable designado"
if [[ -n "$suplente" ]]; then
	if [[ "${responsable,,}" == "${suplente,,}" ]] || ! confirmo "$suplente"; then
		die 18 avisos "falta la confirmación de un suplente distinto del responsable"
	fi
elif [[ "$excepcion" == "primera-instalacion" && "$modo" != "normal" ]]; then
	log_info avisos "excepción temporal de primera instalación con un responsable"
else
	die 18 avisos "falta un suplente con recepción de avisos confirmada"
fi

fecha="$(sed -n 's/^fecha=//p' "$estado" | head -n 1 | tr -d '[:space:]')"
log_info avisos "prueba de recepción vigente (${fecha:-sin fecha})"
