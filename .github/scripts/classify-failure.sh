#!/usr/bin/env bash
# Clasifica el fallo de un trabajo de CI como «de infraestructura» o «del cambio» (FR-004, T017).
#
# Uso: STEPS_JSON='<toJSON(steps)>' classify-failure.sh <nombre-del-trabajo> [registro...]
#
# Regla:
#   - Fallo de infraestructura: falla un paso de preparación (id con prefijo `prep-`: checkout, setup-java,
#     setup-node, descarga de dependencias o de imágenes, descarga de herramientas) o el registro del
#     paso contiene un error de red o de tiempo de espera.
#   - Cualquier otro paso fallido es un fallo del cambio.
# La clasificación solo informa: ambos tipos de fallo bloquean la versión. Se reintenta con
# «Re-run failed jobs» desde la ejecución en GitHub.
# Salida: Markdown en $GITHUB_STEP_SUMMARY (o en la salida estándar si no está definido). Siempre termina con 0.

job="${1:?nombre del trabajo}"
shift || true
out="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
patron='Could not resolve host|Connection timed out|ETIMEDOUT|503 Service Unavailable'

steps_json="${STEPS_JSON:-}"
[[ -n "$steps_json" ]] || steps_json='{}'
fallidos="$(jq -r 'to_entries[] | select(.value.outcome == "failure") | .key' <<<"$steps_json" 2>/dev/null || true)"

if [[ -z "$fallidos" ]]; then
	printf '### %s: sin fallos\n\n' "$job" >>"$out"
	exit 0
fi

tipo="del cambio"
motivo="falla un paso de verificación"
while IFS= read -r id; do
	if [[ "$id" == prep-* ]]; then
		tipo="de infraestructura"
		motivo="falla un paso de preparación (\`$id\`)"
		break
	fi
done <<<"$fallidos"

if [[ "$tipo" == "del cambio" ]]; then
	for registro in "$@"; do
		if [[ -f "$registro" ]] && grep -q -E "$patron" "$registro"; then
			tipo="de infraestructura"
			motivo="el registro contiene un error de red o de tiempo de espera"
			break
		fi
	done
fi

{
	printf '### %s: fallo %s\n\n' "$job" "$tipo"
	printf -- '- Motivo: %s.\n' "$motivo"
	printf -- '- Pasos fallidos: %s.\n' "$(tr '\n' ' ' <<<"$fallidos" | sed 's/ $//; s/ /, /g')"
	printf -- '- La versión **no** se considera apta en ningún caso. Si es de infraestructura, reintentar con «Re-run failed jobs».\n\n'
} >>"$out"
