#!/usr/bin/env bats
# Pruebas de deploy/scripts/sarc-post-checks.sh (T026). curl y unzip son dobles; datos ficticios.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	SCRIPT="$REPO_ROOT/deploy/scripts/sarc-post-checks.sh"
	TMP="$(mktemp -d)"
	export TMP CALLS="$TMP/calls.log"
	mkdir -p "$TMP/bin" "$TMP/current/backend"
	export PATH="$TMP/bin:$PATH"
	VERSION="2026.11.20-57-e85c910"
	export FAKE_BUILD_VERSION="$VERSION"
	export REPORT_ID="11111111-1111-4111-8111-111111111111"
	export FAKE_PASS="clave-verif-ficticia"

	echo "jar ficticio" >"$TMP/current/backend/sarc-backend.jar"
	echo "{\"version\":\"$VERSION\"}" >"$TMP/current/manifest.json"
	export SARC_CURRENT_LINK="$TMP/current"
	{
		echo "SARC_PUBLIC_IP=192.0.2.10"
		echo "SARC_VERIFY_REPORT_ID=$REPORT_ID"
	} >"$TMP/sarc.env"
	export SARC_ENV_FILE="$TMP/sarc.env"
	{
		echo "SARC_VERIFY_USER=verificacion@example.invalid"
		echo "SARC_VERIFY_PASSWORD=$FAKE_PASS"
	} >"$TMP/verificacion.env"
	export SARC_VERIFY_ENV_FILE="$TMP/verificacion.env"
	unset SARC_VERIFY_USER SARC_VERIFY_PASSWORD SARC_VERIFY_REPORT_ID SARC_PUBLIC_IP

	crear_dobles
}

teardown() {
	rm -rf "$TMP"
}

crear_dobles() {
	cat >"$TMP/bin/unzip" <<'DOBLE'
#!/usr/bin/env bash
echo "build.version=$FAKE_BUILD_VERSION"
DOBLE
	cat >"$TMP/bin/curl" <<'DOBLE'
#!/usr/bin/env bash
echo "curl $*" >>"$CALLS"
salida=""; jar=""; url=""; metodo=GET; cabecera=""; datos=""
while (($# > 0)); do
	case "$1" in
	-o) salida="$2"; shift ;;
	-b) shift ;;
	-c) jar="$2"; shift ;;
	-X) metodo="$2"; shift ;;
	-H) case "$2" in X-XSRF-TOKEN:*) cabecera="${2#X-XSRF-TOKEN: }" ;; esac; shift ;;
	-d) datos="$(cat)"; shift ;;
	-w | -m | --cacert) shift ;;
	-*) ;;
	*) url="$1" ;;
	esac
	shift
done
codigo() { printf '%s' "$1"; }
cuerpo() { [ -n "$salida" ] && printf '%s' "$1" >"$salida"; }
sesion() { [ -f "$jar" ] && grep -q "SESION" "$jar"; }
case "$url" in
*127.0.0.1:8080*)
	if [ "${FAKE_DB:-up}" = down ]; then cuerpo '{"status":"DOWN"}'; codigo 503; else cuerpo '{"status":"UP"}'; codigo 200; fi ;;
*/actuator/health/readiness) cuerpo '{"status":"UP"}'; codigo 200 ;;
*/api/v1/auth/me)
	if sesion; then cuerpo '{}'; codigo 200
	else
		printf '192.0.2.10\tFALSE\t/\tFALSE\t0\tXSRF-TOKEN\ttokenficticio\n' >>"$jar"
		cuerpo '{}'; codigo 401
	fi ;;
*/api/v1/auth/login)
	if [ "$cabecera" = "tokenficticio" ] && [[ "$datos" == *"verificacion@example.invalid"* ]] && [[ "$datos" == *"$FAKE_PASS"* ]]; then
		printf '192.0.2.10\tFALSE\t/\tFALSE\t0\tSESION\tabc\n' >>"$jar"; cuerpo '{}'; codigo 200
	else cuerpo '{}'; codigo 401; fi ;;
*/api/v1/uptime/*) if sesion && [ "${FAKE_UPTIME:-ok}" = ok ]; then cuerpo '[]'; codigo 200; else cuerpo '{}'; codigo 500; fi ;;
*/api/v1/reports/issued/*/download) if sesion && [[ "$url" == *"$REPORT_ID"* ]]; then cuerpo 'PDF-FICTICIO'; codigo 200; else cuerpo '{}'; codigo 404; fi ;;
*/api/v1/*) if sesion; then cuerpo '{}'; codigo 200; else cuerpo '{}'; codigo 401; fi ;;
*) cuerpo '<html></html>'; codigo 200 ;;
esac
DOBLE
	chmod +x "$TMP/bin/curl" "$TMP/bin/unzip"
}

veredicto() { echo "$output" | grep -c '^VEREDICTO '; }

@test "todas las comprobaciones pasan: COMPLETADA con un unico veredicto" {
	run "$SCRIPT" "$VERSION"
	[ "$status" -eq 0 ]
	for nombre in disponibilidad base-de-datos version ingreso usuarios inventario consumo uptime reportes archivos; do
		[[ "$output" == *"comprobacion $nombre OK"* ]]
	done
	[ "$(veredicto)" -eq 1 ]
	[[ "$output" == *"VEREDICTO COMPLETADA"* ]]
}

@test "la credencial de la cuenta de verificacion no aparece en la salida ni en los argumentos" {
	run "$SCRIPT" "$VERSION"
	[ "$status" -eq 0 ]
	[[ "$output" != *"$FAKE_PASS"* ]]
	! grep -q "$FAKE_PASS" "$CALLS"
	! grep -q "verificacion@example.invalid" "$CALLS"
}

@test "la version se compara con build-info del JAR y nunca con /actuator/info" {
	run "$SCRIPT" "$VERSION"
	! grep -q "actuator/info" "$CALLS"
	FAKE_BUILD_VERSION="2026.01.01-1-aaaaaaa" run "$SCRIPT" "$VERSION"
	[ "$status" -eq 21 ]
	[[ "$output" == *"comprobacion version FALLO"* ]]
}

@test "una clave equivocada da FALLIDA con codigo 21" {
	sed -i 's/^SARC_VERIFY_PASSWORD=.*/SARC_VERIFY_PASSWORD=clave-equivocada/' "$TMP/verificacion.env"
	run "$SCRIPT" "$VERSION"
	[ "$status" -eq 21 ]
	[[ "$output" == *"comprobacion ingreso FALLO"* ]]
	[[ "$output" == *"VEREDICTO FALLIDA"* ]]
}

@test "sin la credencial de verificacion o sin SARC_VERIFY_REPORT_ID es FALLIDA" {
	rm "$TMP/verificacion.env"
	run "$SCRIPT" "$VERSION"
	[ "$status" -eq 21 ]
	[[ "$output" == *"comprobacion ingreso FALLO"* ]]
	# Con credencial pero sin reporte.
	printf 'SARC_VERIFY_USER=verificacion@example.invalid\nSARC_VERIFY_PASSWORD=%s\n' "$FAKE_PASS" >"$TMP/verificacion.env"
	sed -i '/SARC_VERIFY_REPORT_ID/d' "$TMP/sarc.env"
	run "$SCRIPT" "$VERSION"
	[ "$status" -eq 21 ]
	[[ "$output" == *"comprobacion archivos FALLO"* ]]
	[[ "$output" == *"VEREDICTO FALLIDA"* ]]
}

@test "con --solo-basicas y sin cuenta ni reporte el veredicto es EN_CURSO con el resto PENDIENTE" {
	rm "$TMP/verificacion.env"
	sed -i '/SARC_VERIFY_REPORT_ID/d' "$TMP/sarc.env"
	run "$SCRIPT" --solo-basicas "$VERSION"
	[ "$status" -eq 0 ]
	for nombre in disponibilidad base-de-datos version; do
		[[ "$output" == *"comprobacion $nombre OK"* ]]
	done
	for nombre in ingreso usuarios inventario consumo uptime reportes archivos; do
		[[ "$output" == *"comprobacion $nombre PENDIENTE"* ]]
	done
	[[ "$output" == *"VEREDICTO EN_CURSO"* ]]
	[[ "$output" != *"VEREDICTO COMPLETADA"* ]]
}

@test "con --solo-basicas una base caida es FALLIDA" {
	FAKE_DB=down run "$SCRIPT" --solo-basicas "$VERSION"
	[ "$status" -eq 21 ]
	[[ "$output" == *"comprobacion base-de-datos FALLO"* ]]
}

@test "un modulo que falla da FALLIDA y lo nombra" {
	FAKE_UPTIME=fail run "$SCRIPT" "$VERSION"
	[ "$status" -eq 21 ]
	[[ "$output" == *"comprobacion uptime FALLO"* ]]
	[[ "$output" == *"comprobacion inventario OK"* ]]
}
