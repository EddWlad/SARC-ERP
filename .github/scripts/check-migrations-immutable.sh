#!/usr/bin/env bash
# Comprueba que las migraciones Flyway ya existentes no cambian ni desaparecen y que las nuevas
# tienen un número mayor que el último (Constitución III, FR-018).
#
# Uso: .github/scripts/check-migrations-immutable.sh [referencia-base]
#   referencia-base: por omisión origin/${GITHUB_BASE_REF} en un PR y HEAD^ en un push.
# Ejecutar desde la raíz del repositorio, con historial completo (fetch-depth: 0).
# Códigos: 0 correcto; 1 migración modificada, borrada o con número no válido; 2 uso incorrecto.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-$SCRIPT_DIR/../../deploy/scripts/lib}/common.sh"

MIGRATION_DIR="${SARC_MIGRATION_DIR:-sarc-bakend/src/main/resources/db/migration}"

base="${1:-}"
if [[ -z "$base" ]]; then
	if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
		base="origin/${GITHUB_BASE_REF}"
	else
		base="HEAD^"
	fi
fi

git rev-parse --verify --quiet "${base}^{commit}" >/dev/null || die 2 migraciones "referencia base inexistente: $base"
merge_base="$(git merge-base "$base" HEAD)" || die 2 migraciones "sin ancestro común con la base"

# Número de una migración: dígitos tras la V inicial (V010b__x.sql -> 10). Vacío si el nombre no es válido.
numero_de() {
	local file="${1##*/}"
	if [[ "$file" =~ ^V([0-9]+)[^_]*__.+\.sql$ ]]; then
		echo $((10#${BASH_REMATCH[1]}))
	fi
}

# Último número presente en la base de comparación.
ultimo=0
while IFS= read -r path; do
	[[ -z "$path" ]] && continue
	n="$(numero_de "$path")"
	if [[ -n "$n" ]] && ((n > ultimo)); then
		ultimo="$n"
	fi
done < <(git ls-tree -r --name-only "$merge_base" -- "$MIGRATION_DIR")

errores=0
declare -A usados=()

while IFS=$'\t' read -r status path; do
	[[ -z "$status" ]] && continue
	name="${path##*/}"
	[[ "$name" == *.sql ]] || continue
	case "$status" in
	A)
		n="$(numero_de "$name")"
		if [[ -z "$n" ]]; then
			log_error migraciones "nombre no válido para una migración: $name"
			errores=$((errores + 1))
		elif ((n <= ultimo)); then
			log_error migraciones "número no posterior al último existente ($ultimo): $name"
			errores=$((errores + 1))
		elif [[ -n "${usados[$n]:-}" ]]; then
			log_error migraciones "número repetido entre migraciones nuevas: $name"
			errores=$((errores + 1))
		else
			usados[$n]=1
		fi
		;;
	*)
		log_error migraciones "migración existente modificada o borrada ($status): $name"
		errores=$((errores + 1))
		;;
	esac
done < <(git diff --name-status --no-renames "$merge_base" HEAD -- "$MIGRATION_DIR")

if ((errores > 0)); then
	log_error migraciones "$errores problema(s): las migraciones aplicadas no se editan; crear una migración nueva con número mayor"
	exit 1
fi
log_info migraciones "migraciones correctas (último número en la base: $ultimo)"
