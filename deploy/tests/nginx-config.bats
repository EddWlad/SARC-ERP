#!/usr/bin/env bats
# Pruebas de deploy/nginx/sarc.conf.template (T055): valida la plantilla renderizada con `nginx -t` en un
# contenedor nginx desechable y comprueba su comportamiento con curl local. Requiere docker, openssl y curl;
# sin docker se omite. Certificado autofirmado y backend ficticio: nada de produccion.

setup_file() {
	command -v docker >/dev/null 2>&1 || skip "docker no disponible"
	export MSYS_NO_PATHCONV=1
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	T="$(mktemp -d)"
	hostpath() { if (cd "$1" && pwd -W >/dev/null 2>&1); then (cd "$1" && pwd -W); else (cd "$1" && pwd); fi; }

	mkdir -p "$T/conf" "$T/certs/live/192.0.2.10" "$T/maint" "$T/var" "$T/front" "$T/acme/.well-known/acme-challenge"
	sed 's/<IP_PUBLICA>/192.0.2.10/g' "$REPO_ROOT/deploy/nginx/sarc.conf.template" >"$T/conf/sarc.conf"
	: >"$T/conf/default.conf"
	cp "$REPO_ROOT/deploy/nginx/maintenance.html" "$T/maint/maintenance.html"
	echo "<html>SARC-FRONTEND</html>" >"$T/front/index.html"
	echo "desafio-de-prueba" >"$T/acme/.well-known/acme-challenge/token"
	CERT_DIR="$(hostpath "$T/certs/live/192.0.2.10")"
	openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj "/CN=192.0.2.10" \
		-keyout "$CERT_DIR/privkey.pem" -out "$CERT_DIR/fullchain.pem" >/dev/null 2>&1
	# Backend ficticio en 127.0.0.1:8080 dentro del mismo contenedor.
	cat >"$T/conf/stub.conf" <<'STUB'
server {
    listen 127.0.0.1:8080;
    location = /actuator/health/readiness { default_type application/json; return 200 '{"status":"UP"}'; }
    location /api/ { default_type text/plain; return 200 'API-DE-PRUEBA'; }
}
STUB

	NAME="sarc-nginx-test-$$"
	docker run -d --name "$NAME" \
		-v "$(hostpath "$T/conf")/sarc.conf:/etc/nginx/conf.d/sarc.conf:ro" \
		-v "$(hostpath "$T/conf")/stub.conf:/etc/nginx/conf.d/stub.conf:ro" \
		-v "$(hostpath "$T/conf")/default.conf:/etc/nginx/conf.d/default.conf:ro" \
		-v "$(hostpath "$T/certs"):/etc/letsencrypt:ro" \
		-v "$(hostpath "$T/maint"):/opt/sarc/maintenance:ro" \
		-v "$(hostpath "$T/var"):/var/lib/sarc" \
		-v "$(hostpath "$T/front"):/opt/sarc/current/frontend:ro" \
		-v "$(hostpath "$T/acme"):/var/www/certbot:ro" \
		-p 127.0.0.1:18080:80 -p 127.0.0.1:18443:443 nginx:stable >/dev/null
	export T NAME
	sleep 3
}

teardown_file() {
	[ -n "${NAME:-}" ] && docker rm -f "$NAME" >/dev/null 2>&1 || true
	[ -n "${T:-}" ] && rm -rf "$T"
}

codigo() { curl -sk -o /dev/null -w '%{http_code}' "$@"; }

@test "nginx -t valida la plantilla renderizada" {
	run docker exec "$NAME" nginx -t
	[ "$status" -eq 0 ]
}

@test "el puerto 80 redirige a 443 salvo el desafio ACME" {
	run curl -s -D - -o /dev/null http://127.0.0.1:18080/algo
	[[ "$output" == *" 301 "* ]]
	[[ "$output" == *"ocation: https://"* ]]
	run curl -s http://127.0.0.1:18080/.well-known/acme-challenge/token
	[[ "$output" == *"desafio-de-prueba"* ]]
}

@test "readiness se reenvia al backend" {
	run curl -sk https://127.0.0.1:18443/actuator/health/readiness
	[[ "$output" == *'"status":"UP"'* ]]
}

@test "cualquier otro /actuator/ responde 404" {
	[ "$(codigo https://127.0.0.1:18443/actuator/info)" = "404" ]
	[ "$(codigo https://127.0.0.1:18443/actuator/health/liveness)" = "404" ]
	[ "$(codigo https://127.0.0.1:18443/actuator/metrics)" = "404" ]
	[ "$(codigo https://127.0.0.1:18443/v3/api-docs)" = "404" ]
}

@test "la API se reenvia y el frontend usa el respaldo a index.html" {
	run curl -sk https://127.0.0.1:18443/api/v1/algo
	[[ "$output" == *"API-DE-PRUEBA"* ]]
	run curl -sk https://127.0.0.1:18443/ruta/del/frontend
	[[ "$output" == *"SARC-FRONTEND"* ]]
}

@test "HSTS sin includeSubDomains y cabeceras de seguridad" {
	run curl -skI https://127.0.0.1:18443/
	[[ "$output" == *"Strict-Transport-Security: max-age=31536000"* ]]
	[[ "$output" != *"includeSubDomains"* ]]
	[[ "$output" == *"X-Content-Type-Options: nosniff"* ]]
}

@test "el limite de peticiones al ingreso responde 429" {
	hubo429=0
	for i in $(seq 1 25); do
		c="$(codigo -X POST https://127.0.0.1:18443/api/v1/auth/login || true)"
		[ "$c" = "429" ] && hubo429=1
	done
	[ "$hubo429" -eq 1 ]
}

@test "el indicador de mantenimiento sirve maintenance.html con 503" {
	touch "$T/var/maintenance.flag"
	[ "$(codigo https://127.0.0.1:18443/)" = "503" ]
	run curl -sk https://127.0.0.1:18443/
	[[ "$output" == *"mantenimiento"* ]]
	rm -f "$T/var/maintenance.flag"
	[ "$(codigo https://127.0.0.1:18443/)" = "200" ]
}

@test "el registro de acceso no incluye cookies ni cadena de consulta" {
	curl -sk -H "Cookie: SESSION=valor-secreto-de-prueba" -H "Authorization: Bearer token-secreto" \
		"https://127.0.0.1:18443/api/v1/algo?clave=parametro-secreto" >/dev/null
	run docker exec "$NAME" cat /var/log/nginx/sarc-access.log
	[[ "$output" != *"valor-secreto-de-prueba"* ]]
	[[ "$output" != *"token-secreto"* ]]
	[[ "$output" != *"parametro-secreto"* ]]
}
