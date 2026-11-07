#!/usr/bin/env bats
# Ningún script ni flujo contiene operaciones destructivas fuera de la lista permitida (T024; FR-021, FR-022).
# Recorre deploy/scripts, deploy/release, .github/scripts y .github/workflows. Las pruebas bats no se revisan:
# necesitan crear y borrar datos ficticios.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	ALLOW="$BATS_TEST_DIRNAME/destructive-allowlist.txt"
	cd "$REPO_ROOT"
	shopt -s nullglob
	ARCHIVOS=(deploy/scripts/*.sh deploy/scripts/lib/*.sh deploy/release/*.sh .github/scripts/*.sh .github/workflows/*.yml)
}

# permitida <archivo> <linea>: 0 si la línea (sin sangría) está en la lista permitida para ese archivo.
permitida() {
	local archivo="$1" linea="$2" entrada
	linea="$(echo "$linea" | sed 's/^[[:space:]]*//')"
	while IFS= read -r entrada; do
		[[ -z "$entrada" || "$entrada" == \#* ]] && continue
		[[ "$entrada" == "$archivo|$linea" ]] && return 0
	done <"$ALLOW"
	return 1
}

# Busca un patrón (grep -E, sin distinguir mayúsculas) y falla por cada coincidencia no permitida.
buscar() {
	local patron="$1" archivo contenido numero linea encontrados=0
	for archivo in "${ARCHIVOS[@]}"; do
		while IFS= read -r contenido; do
			[[ -z "$contenido" ]] && continue
			numero="${contenido%%:*}"
			linea="${contenido#*:}"
			if ! permitida "$archivo" "$linea"; then
				echo "operación destructiva no permitida en $archivo:$numero" >&2
				encontrados=$((encontrados + 1))
			fi
		done < <(grep -n -i -E "$patron" "$archivo" || true)
	done
	[ "$encontrados" -eq 0 ]
}

@test "hay archivos que revisar" {
	[ "${#ARCHIVOS[@]}" -gt 5 ]
}

@test "sin DROP, TRUNCATE ni DELETE FROM" {
	buscar 'drop[[:space:]]+(table|database|schema|index|view|extension)|drop[[:space:]]|truncate|delete[[:space:]]+from'
}

@test "sin pg_restore --clean ni dropdb" {
	buscar 'pg_restore[^|;&]*--clean|dropdb'
}

@test "sin eliminar volumenes de Docker ni compose down -v" {
	buscar 'docker[[:space:]]+volume[[:space:]]+(rm|prune)|compose[[:space:]]+down[^|;&]*(-v|--volumes)'
}

@test "sin borrar los datos de PostgreSQL ni las versiones fuera de la lista permitida" {
	buscar 'rm[[:space:]]+-rf[[:space:]]+/var/lib/postgresql|rm[[:space:]]+-rf[[:space:]]+/opt/sarc/releases'
}

@test "sin rm -rf ni find -delete fuera de la lista permitida" {
	buscar 'rm[[:space:]]+-[a-z]*r[a-z]*f|rm[[:space:]]+-[a-z]*f[a-z]*r|find[[:space:]].*-delete'
}

@test "la lista permitida solo contiene las dos operaciones previstas" {
	[ "$(grep -v -E '^[[:space:]]*(#|$)' "$ALLOW" | wc -l | tr -d ' ')" -eq 2 ]
	grep -q '^deploy/scripts/sarc-deploy.sh|' "$ALLOW"
	grep -q '^deploy/scripts/sarc-backup.sh|' "$ALLOW"
}
