#!/usr/bin/env bash
# Genera manifest.json y SHA256SUMS de una versión candidata (contrato: contracts/release-manifest.md).
# Requiere: bash, jq, sha256sum. No lee variables de entorno de configuración ni secretos.
#
# Uso:
#   build-manifest.sh --version <AAAA.MM.DD-run-sha7> --commit <40 hex> --run-id <n> \
#     --artifacts-dir <carpeta con el JAR y el .tar.gz> --migrations-dir <carpeta db/migration> \
#     --checks-file <tsv> --output-dir <carpeta> [--previous-manifest <manifest.json anterior>]
#
# --checks-file: una línea por comprobación, separada por tabuladores: nombre, conclusión, tipo, url.
#   Tipo: `codigo` o `infraestructura`. Todas las conclusiones deben ser `success`.
# Opcional: SARC_CREATED_AT (UTC, para resultados reproducibles).
# Códigos: 0 correcto; 1 alguna comprobación no es success o falta un artefacto; 2 uso incorrecto.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-$SCRIPT_DIR/../scripts/lib}/common.sh"

version="" commit="" run_id="" artifacts_dir="" migrations_dir="" checks_file="" output_dir="" previous=""

while (($# > 0)); do
	case "$1" in
	--version) version="${2:-}" ;;
	--commit) commit="${2:-}" ;;
	--run-id) run_id="${2:-}" ;;
	--artifacts-dir) artifacts_dir="${2:-}" ;;
	--migrations-dir) migrations_dir="${2:-}" ;;
	--checks-file) checks_file="${2:-}" ;;
	--output-dir) output_dir="${2:-}" ;;
	--previous-manifest) previous="${2:-}" ;;
	*) die 2 manifiesto "argumento desconocido: $1" ;;
	esac
	shift 2 || die 2 manifiesto "falta el valor de un argumento"
done

[[ "$version" =~ ^[0-9]{4}\.[0-9]{2}\.[0-9]{2}-[0-9]+-[0-9a-f]{7}$ ]] || die 2 manifiesto "versión con formato incorrecto"
[[ "$commit" =~ ^[0-9a-f]{40}$ ]] || die 2 manifiesto "commit con formato incorrecto (se esperan 40 hexadecimales)"
[[ "$run_id" =~ ^[0-9]+$ ]] || die 2 manifiesto "identificador de ejecución no numérico"
[[ -d "$artifacts_dir" ]] || die 2 manifiesto "carpeta de artefactos inexistente"
[[ -d "$migrations_dir" ]] || die 2 manifiesto "carpeta de migraciones inexistente"
[[ -f "$checks_file" ]] || die 2 manifiesto "archivo de comprobaciones inexistente"
[[ -n "$output_dir" ]] || die 2 manifiesto "falta --output-dir"
mkdir -p "$output_dir"

# --- Comprobaciones: todas deben ser success ---
checks_json="[]"
fallidas=()
while IFS=$'\t' read -r name conclusion kind url; do
	[[ -z "$name" || "$name" == \#* ]] && continue
	[[ "$kind" == "codigo" || "$kind" == "infraestructura" ]] || die 2 manifiesto "tipo de comprobación no válido en '$name'"
	[[ "$url" =~ ^https:// ]] || die 2 manifiesto "URL de comprobación no válida en '$name'"
	if [[ "$url" == *@* ]]; then
		die 2 manifiesto "la URL de '$name' contiene credenciales"
	fi
	if [[ "$conclusion" != "success" ]]; then
		fallidas+=("$name")
	fi
	checks_json="$(jq -c --arg n "$name" --arg c "$conclusion" --arg k "$kind" --arg u "$url" \
		'. + [{name:$n, conclusion:$c, kind:$k, url:$u}]' <<<"$checks_json")"
done <"$checks_file"
if ((${#fallidas[@]} > 0)); then
	die 1 manifiesto "no se genera el manifiesto: comprobaciones sin éxito: ${fallidas[*]}"
fi
[[ "$checks_json" != "[]" ]] || die 1 manifiesto "no hay comprobaciones registradas"

# --- Artefactos ---
jar="sarc-backend-${version}.jar"
tgz="sarc-frontend-${version}.tar.gz"
artifacts_json="[]"
sums=""
for artifact in "$jar" "$tgz"; do
	[[ -f "$artifacts_dir/$artifact" ]] || die 1 manifiesto "falta el artefacto $artifact"
	sum="$(sha256sum "$artifacts_dir/$artifact" | cut -d' ' -f1)"
	bytes="$(wc -c <"$artifacts_dir/$artifact" | tr -d ' ')"
	artifacts_json="$(jq -c --arg n "$artifact" --argjson b "$bytes" --arg s "$sum" \
		'. + [{name:$n, bytes:$b, sha256:$s}]' <<<"$artifacts_json")"
	sums+="${sum}  ${artifact}"$'\n'
done

# Suma de comprobación que Flyway guarda en flyway_schema_history.checksum: CRC-32 (con signo, 32 bits) del
# contenido sin BOM ni saltos de línea. Permite a sarc-deploy comparar el historial de la base con el manifiesto.
flyway_checksum() {
	local crc
	crc="$(sed '1s/^ï»¿//' "$1" | tr -d '
' | gzip -1 -c | tail -c8 | head -c4 | od -An -tu4 | tr -d ' ')"
	if ((crc > 2147483647)); then
		crc=$((crc - 4294967296))
	fi
	echo "$crc"
}

# --- Migraciones (ordenadas por número) ---
migrations_json="[]"
latest=""
latest_n=-1
while IFS= read -r file; do
	base="${file##*/}"
	[[ "$base" =~ ^V([0-9]+)[^_]*__.+\.sql$ ]] || continue
	n=$((10#${BASH_REMATCH[1]}))
	sum="$(sha256sum "$file" | cut -d' ' -f1)"
	flyway="$(flyway_checksum "$file")"
	migrations_json="$(jq -c --arg n "$base" --arg s "$sum" --argjson c "$flyway" 		'. + [{name:$n, sha256:$s, flywayChecksum:$c}]' <<<"$migrations_json")"
	if ((n > latest_n)); then
		latest_n="$n"
		latest="V${BASH_REMATCH[1]}"
	fi
done < <(find "$migrations_dir" -maxdepth 1 -type f -name 'V*.sql' | sort -V)
[[ -n "$latest" ]] || die 1 manifiesto "no hay migraciones en la carpeta indicada"

# --- schemaChanges: informativo, frente al Release candidato anterior ---
schema_changes=true
if [[ -n "$previous" && -f "$previous" ]]; then
	previous_latest="$(jq -r '.migrations.latest // ""' "$previous")"
	if [[ "$previous_latest" == "$latest" ]]; then
		schema_changes=false
	fi
fi

created_at="${SARC_CREATED_AT:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"

jq -n \
	--arg version "$version" --arg commit "$commit" --argjson run "$run_id" --arg created "$created_at" \
	--argjson checks "$checks_json" --argjson artifacts "$artifacts_json" \
	--arg latest "$latest" --argjson files "$migrations_json" --argjson changes "$schema_changes" \
	'{schema:1, version:$version, commit:$commit, ciRunId:$run, createdAt:$created,
	  checks:$checks, artifacts:$artifacts,
	  migrations:{latest:$latest, files:$files}, schemaChanges:$changes}' >"$output_dir/manifest.json"

manifest_sum="$(sha256sum "$output_dir/manifest.json" | cut -d' ' -f1)"
printf '%s%s  manifest.json\n' "$sums" "$manifest_sum" >"$output_dir/SHA256SUMS"

log_info manifiesto "manifiesto y sumas generados para la versión $version"
