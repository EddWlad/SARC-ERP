#!/usr/bin/env bash
# Aplica el umbral de severidad de FR-005 al resultado JSON de OSV-Scanner.
#
# Uso: osv-gate.sh <osv-results.json>
# Falla (código 1) si alguna vulnerabilidad tiene CVSS >= 7.0 (alta o crítica). Las de severidad
# media (4.0–6.9) y las sin puntuación solo se anotan como aviso en $GITHUB_STEP_SUMMARY.
# Las excepciones aceptadas viven en osv-scanner.toml (con motivo y fecha de revisión).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-$SCRIPT_DIR/../../deploy/scripts/lib}/common.sh"

file="${1:?archivo JSON de OSV-Scanner}"
[[ -f "$file" ]] || die 2 dependencias "no existe el resultado de OSV-Scanner"
out="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# Una fila por grupo de vulnerabilidades: puntuación<TAB>paquete<TAB>identificadores.
filas="$(jq -r '
	[.results[]?.packages[]? as $p
	 | $p.groups[]?
	 | [(.max_severity // ""), ($p.package.name // "?"), ((.ids // []) | join(","))]]
	| .[] | @tsv' "$file")"

altas=0
avisos=()
while IFS=$'\t' read -r score paquete ids; do
	[[ -z "$ids" ]] && continue
	if [[ -z "$score" ]]; then
		avisos+=("- Sin puntuación: \`$paquete\` ($ids)")
	elif awk -v s="$score" 'BEGIN { exit !(s + 0 >= 7.0) }'; then
		log_error dependencias "severidad $score en $paquete ($ids)"
		altas=$((altas + 1))
	elif awk -v s="$score" 'BEGIN { exit !(s + 0 >= 4.0) }'; then
		avisos+=("- Severidad media ($score): \`$paquete\` ($ids)")
	fi
done <<<"$filas"

if ((${#avisos[@]} > 0)); then
	{
		printf '### Dependencias: avisos (no bloquean)\n\n'
		printf '%s\n' "${avisos[@]}"
		printf '\n'
	} >>"$out"
fi

if ((altas > 0)); then
	die 1 dependencias "$altas vulnerabilidad(es) de severidad alta o crítica (CVSS >= 7.0); actualizar o registrar una excepción en osv-scanner.toml"
fi
log_info dependencias "sin vulnerabilidades de severidad alta o crítica"
