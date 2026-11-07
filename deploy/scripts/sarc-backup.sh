#!/usr/bin/env bash
# Copia lógica cifrada de PostgreSQL (contrato: contracts/backup-artifacts.md).
#
# Uso: sarc-backup nocturna|previa|manual
# Genera en ${SARC_BACKUP_DIR:-/var/backups/sarc}:
#   sarc-<AAAAMMDDThhmmZ>-<kind>.dump.age         pg_dump -Fc cifrado con age (clave PÚBLICA; el VPS no puede descifrar)
#   sarc-<AAAAMMDDThhmmZ>-<kind>.dump.age.sha256  suma de la copia cifrada (formato sha256sum)
#   sarc-<AAAAMMDDThhmmZ>-<kind>.dump.age.meta.json  createdAt, kind, pgVersion, latestMigration, tableCounts, huella de la clave
# Sin datos de filas en los metadatos. Retención en el VPS: 7 días. En `nocturna` envía el latido
# `copia-nocturna` (o /fail si falla).
# Configuración: /etc/sarc/sarc.env (SARC_ENV_FILE), clave pública en /etc/sarc/backup-recipient.pub
# (SARC_BACKUP_RECIPIENT). Requiere docker compose, age, jq y sha256sum.
# Códigos: 0 correcto; 1 fallo de la copia; 2 uso incorrecto; 12 configuración ausente.

# shellcheck source=/dev/null
source "${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh"

kind="${1:-}"
case "$kind" in
nocturna | previa | manual) ;;
*) die 2 copia "uso: sarc-backup nocturna|previa|manual" ;;
esac

env_file="${SARC_ENV_FILE:-/etc/sarc/sarc.env}"
[[ -r "$env_file" ]] && load_env "$env_file"
backup_dir="${SARC_BACKUP_DIR:-/var/backups/sarc}"
recipient="${SARC_BACKUP_RECIPIENT:-/etc/sarc/backup-recipient.pub}"
retention_days="${SARC_BACKUP_RETENTION_DAYS:-7}"

fallo() {
	log_error copia "la copia '$kind' falló"
	[[ -n "${partial:-}" ]] && rm -f "$partial"
	if [[ "$kind" == "nocturna" ]]; then
		ping_check copia-nocturna fail || true
	fi
	exit 1
}
trap fallo ERR

[[ -r "$recipient" ]] || die 12 copia "falta la clave pública de cifrado (backup-recipient.pub)"
mkdir -p "$backup_dir"

stamp="$(date -u +%Y%m%dT%H%MZ)"
name="sarc-${stamp}-${kind}.dump.age"
final="$backup_dir/$name"
partial="$backup_dir/.${name}.partial"

[[ ! -e "$final" ]] || die 1 copia "ya existe una copia con ese nombre"

psql_sarc() {
	compose exec -T postgres psql -U sarc -d sarc -X -A -t -v ON_ERROR_STOP=1 -c "$1"
}

log_info copia "iniciando copia '$kind'"
# pg_dump dentro del contenedor, cifrado en el mismo flujo: nunca queda un volcado sin cifrar en disco.
compose exec -T postgres pg_dump -U sarc -d sarc -Fc | age -R "$recipient" -o "$partial"
[[ -s "$partial" ]] || die 1 copia "la copia cifrada quedó vacía"
mv "$partial" "$final"
partial=""

(cd "$backup_dir" && sha256sum "$name" >"${name}.sha256")

pg_version="$(psql_sarc "SHOW server_version" | tr -d '[:space:]')"
latest_migration="$(psql_sarc "SELECT 'V' || version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1" | tr -d '[:space:]')"
table_counts="$(psql_sarc "SELECT coalesce(json_object_agg(table_name, (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I', table_schema, table_name), false, true, '')))[1]::text::bigint), '{}'::json) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")"
key_fingerprint="$(sha256sum "$recipient" | cut -c1-16)"

jq -n --arg createdAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg kind "$kind" --arg pg "$pg_version" \
	--arg migration "$latest_migration" --argjson counts "$table_counts" --arg key "$key_fingerprint" \
	'{createdAt:$createdAt, kind:$kind, pgVersion:$pg, latestMigration:$migration, tableCounts:$counts, keyFingerprint:$key}' \
	>"$backup_dir/${name}.meta.json"

# Permisos: solo lectura para el grupo de la cuenta SFTP; el resto sin acceso.
group="${SARC_BACKUP_GROUP:-sarc-backup}"
for f in "$final" "$final.sha256" "$final.meta.json"; do
	chmod 0640 "$f"
	if getent group "$group" >/dev/null 2>&1; then
		chgrp "$group" "$f"
	fi
done

# Retención: solo copias de más de N días de este mismo formato (en el VPS: 7 días).
find "$backup_dir" -maxdepth 1 -type f -name 'sarc-*.dump.age*' -mtime +"$retention_days" -delete

log_info copia "copia '$kind' completada: $name"
if [[ "$kind" == "nocturna" ]]; then
	ping_check copia-nocturna ok || log_error copia "no se pudo enviar el latido"
fi
echo "$name"
