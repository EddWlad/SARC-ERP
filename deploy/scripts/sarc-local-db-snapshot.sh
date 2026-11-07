#!/usr/bin/env bash
# Imprime el conteo de filas por tabla de una base local, para comprobar que los ensayos no la
# modifican (SC-006). Solo lee. Se niega a ejecutarse si la base no es local.
#
# Uso: sarc-local-db-snapshot.sh <url-jdbc-o-uri-postgres>
#   Ejemplos: jdbc:postgresql://localhost:5432/sarc  |  postgresql://sarc@127.0.0.1:5432/sarc
# La contraseña, si hace falta, se toma de PGPASSWORD (nunca se imprime).
# Requiere el cliente `psql` (en la máquina o dentro de un contenedor postgres:18).
# Códigos: 0 correcto; 2 uso incorrecto o base no local.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-$SCRIPT_DIR/lib}/common.sh"

url="${1:-}"
[[ -n "$url" ]] || die 2 snapshot "uso: sarc-local-db-snapshot.sh <url-de-la-base-local>"

# Extrae host, puerto, base y usuario de una URL JDBC o de una URI postgres.
sin_esquema="${url#jdbc:}"
sin_esquema="${sin_esquema#postgresql://}"
sin_esquema="${sin_esquema#postgres://}"
usuario=""
if [[ "$sin_esquema" == *@* ]]; then
	usuario="${sin_esquema%%@*}"
	usuario="${usuario%%:*}"
	sin_esquema="${sin_esquema#*@}"
fi
host_puerto="${sin_esquema%%/*}"
base_y_opciones="${sin_esquema#*/}"
base="${base_y_opciones%%\?*}"
host="${host_puerto%%:*}"
puerto="5432"
if [[ "$host_puerto" == *:* ]]; then
	puerto="${host_puerto##*:}"
fi

case "$host" in
localhost | 127.0.0.1 | "[::1]" | "::1") ;;
*) die 2 snapshot "la base no es local (host no permitido); este script solo se ejecuta contra localhost/127.0.0.1" ;;
esac
[[ -n "$base" && "$puerto" =~ ^[0-9]+$ ]] || die 2 snapshot "URL de base no válida"

args=(-X -A -t -h "$host" -p "$puerto" -d "$base")
if [[ -n "$usuario" ]]; then
	args+=(-U "$usuario")
fi

# Solo lectura: todas las sesiones se abren con default_transaction_read_only = on.
export PGOPTIONS="-c default_transaction_read_only=on"
tablas="$(psql "${args[@]}" -v ON_ERROR_STOP=1 -c \
	"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name")"
while IFS= read -r tabla; do
	[[ -z "$tabla" ]] && continue
	cuenta="$(psql "${args[@]}" -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM \"$tabla\"")"
	printf '%s\t%s\n' "$tabla" "$cuenta"
done <<<"$tablas"
