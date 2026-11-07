#!/usr/bin/env bash
# Comprueba que el computador local tiene una copia verificada reciente (FR-043).
#
# Lee los recibos ${SARC_BACKUP_DIR:-/var/backups/sarc}/received/*.ok que sube el computador local tras
# verificar cada copia. Cada recibo es un archivo de texto con líneas clave=valor:
#   fecha=<AAAA-MM-DDThh:mm:ssZ>   (momento en que el computador local verificó la copia)
#   archivo=<nombre de la copia>
#   sha256=<suma de la copia cifrada>
# Devuelve 0 si el recibo más reciente tiene menos de SARC_MAX_BACKUP_AGE_HOURS (24 por omisión) y 14 si no
# hay recibos, ninguno tiene fecha válida o el más reciente tiene esa antigüedad o más.
# Lo usa `sarc-deploy` en el paso 4. No imprime rutas ni contenido de los recibos.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

received_dir="${SARC_BACKUP_DIR:-/var/backups/sarc}/received"
max_hours="${SARC_MAX_BACKUP_AGE_HOURS:-24}"
now="$(date -u +%s)"

latest=0
shopt -s nullglob
for receipt in "$received_dir"/*.ok; do
	fecha="$(sed -n 's/^fecha=//p' "$receipt" | head -n 1 | tr -d '[:space:]')"
	[[ -n "$fecha" ]] || continue
	# Formato "AAAA-MM-DD hh:mm:ss" en UTC: lo entienden date de GNU y de BusyBox.
	normalizada="${fecha/T/ }"
	normalizada="${normalizada%Z}"
	epoch="$(date -u -d "$normalizada" +%s 2>/dev/null)" || continue
	if ((epoch > latest)); then
		latest="$epoch"
	fi
done

if ((latest == 0)); then
	die 14 copia-local "sin recibo de copia local verificada: el computador local no ha confirmado ninguna copia"
fi

age_hours=$(((now - latest) / 3600))
if ((now - latest >= max_hours * 3600)); then
	die 14 copia-local "sin copia local reciente verificable: el último recibo tiene ${age_hours} h (máximo ${max_hours} h)"
fi
log_info copia-local "recibo de copia local verificada de hace ${age_hours} h"
