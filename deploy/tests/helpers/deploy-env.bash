# Entorno de pruebas compartido para sarc-deploy y sus piezas. Carga con: load helpers/deploy-env
# Crea un VPS ficticio en una carpeta temporal: dobles de curl, docker, systemctl y de los scripts hermanos
# (sarc-alerts-gate, sarc-check-cert, sarc-backup-freshness, sarc-schema-check, sarc-backup, sarc-post-checks).
# Cada doble devuelve el código indicado por FAKE_RC_<NOMBRE> (0 por omisión) y registra su llamada en $TMP/calls.log.
# Todo es ficticio: ningún valor real, ninguna red, ningún servidor.

deploy_setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	DEPLOY="$REPO_ROOT/deploy/scripts/sarc-deploy.sh"
	WRAPPER="$REPO_ROOT/deploy/scripts/sarc-deploy-ssh-wrapper.sh"
	TMP="$(mktemp -d)"
	VERSION="2026.11.20-57-e85c910"
	export TMP VERSION

	export SARC_STATE_DIR="$TMP/state" SARC_RELEASES_DIR="$TMP/releases" SARC_CURRENT_LINK="$TMP/current"
	export SARC_BACKUP_DIR="$TMP/backups" SARC_ENV_FILE="$TMP/sarc.env"
	export SARC_RELEASE_BASE_URL="https://releases.example.invalid/download"
	export SARC_READY_TIMEOUT=3 SARC_READY_INTERVAL=1
	export SARC_READINESS_URL="http://127.0.0.1:8080/actuator/health/readiness"
	mkdir -p "$SARC_STATE_DIR" "$SARC_RELEASES_DIR" "$SARC_BACKUP_DIR" "$TMP/bin" "$TMP/fixture/$VERSION"

	# Configuración ficticia completa.
	cat >"$SARC_ENV_FILE" <<ENV
SPRING_PROFILES_ACTIVE=prod
SARC_DB_URL=jdbc:postgresql://127.0.0.1:5432/sarc
SARC_DB_USER=sarc
SARC_DB_PASSWORD=clave-ficticia-1
POSTGRES_PASSWORD=clave-ficticia-2
CLOUDINARY_CLOUD_NAME=nube-ficticia
CLOUDINARY_API_KEY=clave-ficticia-3
CLOUDINARY_API_SECRET=secreto-ficticio-4
SARC_STORAGE_CLOUDINARY_ROOT_FOLDER=sarc-prod
SARC_DEPLOY_ENV=produccion
SARC_PUBLIC_IP=192.0.2.10
SARC_BOOTSTRAP_ADMIN_EMAIL=admin@example.invalid
SARC_BOOTSTRAP_ADMIN_PASSWORD=clave-ficticia-5
ENV

	# Release ficticio con sumas y manifiesto coherentes.
	local fx="$TMP/fixture/$VERSION"
	echo "jar ficticio" >"$fx/sarc-backend-$VERSION.jar"
	local site="$TMP/site"
	mkdir -p "$site"
	echo "<html>sarc</html>" >"$site/index.html"
	tar -czf "$fx/sarc-frontend-$VERSION.tar.gz" -C "$site" .
	jq -n --arg v "$VERSION" \
		--arg js "$(sha256sum "$fx/sarc-backend-$VERSION.jar" | cut -d' ' -f1)" \
		--arg ts "$(sha256sum "$fx/sarc-frontend-$VERSION.tar.gz" | cut -d' ' -f1)" \
		'{schema:1, version:$v, artifacts:[{name:("sarc-backend-"+$v+".jar"), sha256:$js},{name:("sarc-frontend-"+$v+".tar.gz"), sha256:$ts}],
		  migrations:{latest:"V016", files:[]}, schemaChanges:false}' >"$fx/manifest.json"
	(cd "$fx" && sha256sum "sarc-backend-$VERSION.jar" "sarc-frontend-$VERSION.tar.gz" manifest.json >SHA256SUMS)

	export PATH="$TMP/bin:$PATH"
	export CALLS="$TMP/calls.log"
	export READINESS=up
	crear_dobles
}

deploy_teardown() {
	cd /
	rm -rf "$TMP"
}

crear_dobles() {
	# curl: readiness local y descargas del Release ficticio.
	cat >"$TMP/bin/curl" <<'DOBLE'
#!/usr/bin/env bash
salida=""
url=""
while (($# > 0)); do
	case "$1" in
	-o) salida="$2"; shift ;;
	-w | -m | --retry) shift ;;
	-*) ;;
	*) url="$1" ;;
	esac
	shift
done
case "$url" in
*127.0.0.1:8080*)
	[ "$READINESS" = up ] && echo '{"status":"UP"}' || exit 22
	;;
*/download/*)
	[ -n "${FAKE_DOWNLOAD_FAIL:-}" ] && exit 22
	cp "$TMP/fixture/${url#*/download/}" "$salida"
	;;
esac
DOBLE
	# systemctl: registra las llamadas; `start sarc` marca que el servicio arrancó.
	cat >"$TMP/bin/systemctl" <<'DOBLE'
#!/usr/bin/env bash
echo "systemctl $*" >>"$CALLS"
[ "$1 $2" = "start sarc" ] && touch "$TMP/started"
exit 0
DOBLE
	# docker compose: responde a las consultas de sarc-deploy según el texto de la consulta.
	cat >"$TMP/bin/docker" <<'DOBLE'
#!/usr/bin/env bash
echo "docker $*" >>"$CALLS"
sql="$*"
case "$sql" in
*"up -d"*) exit 0 ;;
*pg_isready*) exit 0 ;;
*information_schema.tables*) echo "${FAKE_TABLES:-0}" ;;
*"max(installed_rank)"*) echo "${FAKE_RANK_BEFORE:-16}" ;;
*"installed_rank >"*) [ -e "$TMP/started" ] && printf '%b' "${FAKE_APPLIED:-}" ;;
*"max('V'"*) echo "V016" ;;
esac
exit 0
DOBLE
	local nombre
	for nombre in sarc-alerts-gate sarc-check-cert sarc-backup-freshness sarc-schema-check; do
		cat >"$TMP/bin/$nombre" <<DOBLE
#!/usr/bin/env bash
echo "$nombre \$*" >>"\$CALLS"
rc="FAKE_RC_\$(echo "$nombre" | tr '-' '_')"
exit "\${!rc:-0}"
DOBLE
	done
	cat >"$TMP/bin/sarc-backup" <<'DOBLE'
#!/usr/bin/env bash
echo "sarc-backup $*" >>"$CALLS"
[ "${FAKE_RC_sarc_backup:-0}" -ne 0 ] && exit "${FAKE_RC_sarc_backup}"
nombre="sarc-20261120T0700Z-previa.dump.age"
echo "copia cifrada ficticia" >"$SARC_BACKUP_DIR/$nombre"
(cd "$SARC_BACKUP_DIR" && sha256sum "$nombre" >"$nombre.sha256")
echo "$nombre"
DOBLE
	cat >"$TMP/bin/sarc-post-checks" <<'DOBLE'
#!/usr/bin/env bash
echo "sarc-post-checks $*" >>"$CALLS"
rc="${FAKE_RC_sarc_post_checks:-0}"
case " $* " in
*" --solo-basicas "*)
	echo "comprobacion disponibilidad OK"; echo "comprobacion base-de-datos OK"; echo "comprobacion version OK"
	echo "comprobacion ingreso PENDIENTE"; echo "VEREDICTO EN_CURSO" ;;
*)
	if [ "$rc" -eq 0 ]; then echo "comprobacion ingreso OK"; echo "VEREDICTO COMPLETADA"
	else echo "comprobacion ingreso FALLO"; echo "VEREDICTO FALLIDA"; fi ;;
esac
exit "$rc"
DOBLE
	chmod +x "$TMP"/bin/*
}

# Última línea del registro de publicaciones como JSON.
ultima_linea() { tail -n 1 "$SARC_STATE_DIR/deploy-history.jsonl"; }
numero_de_lineas() { wc -l <"$SARC_STATE_DIR/deploy-history.jsonl" | tr -d ' '; }
