#!/usr/bin/env bats
# Pruebas de deploy/scripts/sarc-backup-freshness.sh (T044).

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	SCRIPT="$REPO_ROOT/deploy/scripts/sarc-backup-freshness.sh"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	TMP="$(mktemp -d)"
	export SARC_BACKUP_DIR="$TMP/backups"
	mkdir -p "$SARC_BACKUP_DIR/received"
}

teardown() {
	rm -rf "$TMP"
}

# recibo <nombre> <horas de antigüedad>
recibo() {
	local epoch=$(($(date -u +%s) - $2 * 3600))
	printf 'fecha=%s\narchivo=%s\nsha256=0000\n' "$(date -u -d "@$epoch" +%Y-%m-%dT%H:%M:%SZ)" "$1" \
		>"$SARC_BACKUP_DIR/received/$1.ok"
}

@test "sin recibos devuelve 14" {
	run "$SCRIPT"
	[ "$status" -eq 14 ]
}

@test "un recibo de 23 h pasa" {
	recibo sarc-a.dump.age 23
	run "$SCRIPT"
	[ "$status" -eq 0 ]
}

@test "un recibo de 24 h o mas devuelve 14" {
	recibo sarc-a.dump.age 24
	run "$SCRIPT"
	[ "$status" -eq 14 ]
	recibo sarc-b.dump.age 30
	run "$SCRIPT"
	[ "$status" -eq 14 ]
}

@test "usa el recibo mas reciente aunque existan otros viejos" {
	recibo sarc-vieja.dump.age 72
	recibo sarc-nueva.dump.age 2
	run "$SCRIPT"
	[ "$status" -eq 0 ]
}

@test "un recibo sin fecha valida no cuenta" {
	printf 'archivo=x\n' >"$SARC_BACKUP_DIR/received/x.ok"
	run "$SCRIPT"
	[ "$status" -eq 14 ]
}

@test "no imprime el contenido de los recibos" {
	recibo sarc-secreto-no-imprimir.dump.age 2
	run "$SCRIPT"
	[[ "$output" != *"sarc-secreto-no-imprimir"* ]]
}
