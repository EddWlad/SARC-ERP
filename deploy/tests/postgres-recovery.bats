#!/usr/bin/env bats
# PostgreSQL real, sin red y con volumen desechable dedicado a este caso.

teardown() {
	if [[ -n "${PG_SANDBOX:-}" ]]; then
		docker rm -f "$PG_SANDBOX" >/dev/null 2>&1 || true
		docker volume rm "$PG_SANDBOX" >/dev/null 2>&1 || true
	fi
}

esperar_postgres() {
	for _ in $(seq 1 45); do
		if docker exec "$PG_SANDBOX" pg_isready -h 127.0.0.1 -U sarc >/dev/null 2>&1; then
			return 0
		fi
		sleep 1
	done
	return 1
}

sql() {
	docker exec "$PG_SANDBOX" psql -X -U sarc -d sarc -At -v ON_ERROR_STOP=1 -c "$1"
}

@test "PostgreSQL recupera datos confirmados después de una caída y un reinicio" {
	command -v docker >/dev/null 2>&1 || skip "docker no disponible"
	export MSYS_NO_PATHCONV=1
	PG_SANDBOX="sarc-qa-recuperacion-$$-$RANDOM"
	docker volume create "$PG_SANDBOX" >/dev/null
	docker run -d --name "$PG_SANDBOX" --network none --restart unless-stopped \
		-e POSTGRES_USER=sarc -e POSTGRES_DB=sarc -e POSTGRES_HOST_AUTH_METHOD=trust \
		--mount "type=volume,source=$PG_SANDBOX,target=/var/lib/postgresql" postgres:18 >/dev/null
	esperar_postgres
	sql 'CREATE TABLE prueba_recuperacion (valor integer); INSERT INTO prueba_recuperacion VALUES (42);'
	reinicios="$(docker inspect -f '{{.RestartCount}}' "$PG_SANDBOX")"
	docker exec -u postgres "$PG_SANDBOX" sh -c 'pg_ctl -D "$PGDATA" -m immediate stop' || true
	for _ in $(seq 1 30); do
		actual="$(docker inspect -f '{{.RestartCount}}' "$PG_SANDBOX")"
		((actual > reinicios)) && break
		sleep 1
	done
	((actual > reinicios))
	esperar_postgres
	[ "$(sql 'SELECT valor FROM prueba_recuperacion')" = "42" ]
	docker restart "$PG_SANDBOX" >/dev/null
	esperar_postgres
	[ "$(sql 'SELECT valor FROM prueba_recuperacion')" = "42" ]
}
