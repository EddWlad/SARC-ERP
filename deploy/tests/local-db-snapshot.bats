#!/usr/bin/env bats
# Pruebas de deploy/scripts/sarc-local-db-snapshot.sh (T041): solo bases locales y solo lectura.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	SCRIPT="$REPO_ROOT/deploy/scripts/sarc-local-db-snapshot.sh"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	TMP="$(mktemp -d)"
	mkdir -p "$TMP/bin"
	export PATH="$TMP/bin:$PATH"
	# Doble de psql: registra los argumentos y PGOPTIONS; responde con una tabla y un conteo.
	{
		echo '#!/usr/bin/env bash'
		echo 'echo "$PGOPTIONS" >>"$PSQL_LOG"'
		echo 'printf "%s\n" "$@" >>"$PSQL_LOG"'
		echo 'case "$*" in *information_schema*) echo auth_user;; *count*) echo 3;; esac'
	} >"$TMP/bin/psql"
	chmod +x "$TMP/bin/psql"
	export PSQL_LOG="$TMP/psql.log"
}

teardown() {
	rm -rf "$TMP"
}

@test "se niega con un host que no es local" {
	run "$SCRIPT" "jdbc:postgresql://db.example.invalid:5432/sarc"
	[ "$status" -eq 2 ]
	[ ! -e "$PSQL_LOG" ]
}

@test "se niega sin argumentos" {
	run "$SCRIPT"
	[ "$status" -eq 2 ]
}

@test "con una base local imprime conteos y abre sesiones de solo lectura" {
	run "$SCRIPT" "jdbc:postgresql://localhost:5432/sarc"
	[ "$status" -eq 0 ]
	[[ "$output" == *$'auth_user\t3'* ]]
	grep -q "default_transaction_read_only=on" "$PSQL_LOG"
	! grep -q -i -E "insert|update|delete|drop|truncate" "$PSQL_LOG"
}
