#!/usr/bin/env bash
# Comprueba que el historial de Flyway de la base es coherente con el manifiesto de la versión (paso 5; FR-013, FR-019).
#
# Uso: sarc-schema-check <manifest.json>
# Lee flyway_schema_history con `docker compose exec -T postgres psql` y lo compara con `migrations.files`:
#   - una migración aplicada que el manifiesto no conoce;
#   - una aplicada con suma distinta (flywayChecksum del manifiesto frente a la columna checksum);
#   - una fila con success = false;
#   - una pendiente con número menor que la última aplicada.
# Cualquiera de ellas devuelve 15. Imprime solo nombres de migración, nunca datos.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

manifest="${1:-}"
[[ -r "$manifest" ]] || die 2 esquema "uso: sarc-schema-check <manifest.json>"

if ! filas="$(compose exec -T postgres psql -U sarc -d sarc -X -A -t -F '|' -v ON_ERROR_STOP=1 -c \
	"SELECT installed_rank, coalesce(version, ''), coalesce(checksum::text, ''), success FROM flyway_schema_history ORDER BY installed_rank")"; then
	die 15 esquema "no se pudo leer flyway_schema_history"
fi

errores=0
ultimo=-1
declare -A aplicadas=()
while IFS='|' read -r _rank version checksum success; do
	[[ -z "$version" ]] && continue
	if [[ "$success" != "t" ]]; then
		log_error esquema "migración fallida en el historial: V${version}"
		errores=$((errores + 1))
		continue
	fi
	nombre="$(jq -r --arg p "V${version}__" '.migrations.files[] | select(.name | startswith($p)) | .name' "$manifest" | head -n 1)"
	if [[ -z "$nombre" ]]; then
		log_error esquema "migración aplicada desconocida para esta versión: V${version}"
		errores=$((errores + 1))
		continue
	fi
	esperada="$(jq -r --arg n "$nombre" '.migrations.files[] | select(.name == $n) | .flywayChecksum' "$manifest")"
	if [[ "$esperada" != "$checksum" ]]; then
		log_error esquema "suma distinta en la migración aplicada: $nombre"
		errores=$((errores + 1))
	fi
	aplicadas["$nombre"]=1
	numero=$((10#${version%%[!0-9]*}))
	if ((numero > ultimo)); then
		ultimo="$numero"
	fi
done <<<"$filas"

# Pendientes: las del manifiesto que no están aplicadas deben ser posteriores a la última aplicada.
while IFS= read -r nombre; do
	[[ -z "$nombre" ]] && continue
	[[ -n "${aplicadas[$nombre]:-}" ]] && continue
	if [[ "$nombre" =~ ^V([0-9]+) ]]; then
		numero=$((10#${BASH_REMATCH[1]}))
		if ((numero < ultimo)); then
			log_error esquema "migración pendiente con número menor que la última aplicada: $nombre"
			errores=$((errores + 1))
		fi
	fi
done < <(jq -r '.migrations.files[].name' "$manifest")

if ((errores > 0)); then
	exit 15
fi
log_info esquema "historial de Flyway coherente con el manifiesto"
