#!/usr/bin/env bats
# Pruebas de .github/scripts/check-migrations-immutable.sh (T010) sobre un repositorio Git temporal.

setup() {
	REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../.." && pwd)"
	SCRIPT="$REPO_ROOT/.github/scripts/check-migrations-immutable.sh"
	export SARC_LIB_DIR="$REPO_ROOT/deploy/scripts/lib"
	TMP="$(mktemp -d)"
	WORK="$TMP/repo"
	MIG="sarc-bakend/src/main/resources/db/migration"
	mkdir -p "$WORK/$MIG"
	cd "$WORK"
	git init -q
	git config user.email "prueba@example.invalid"
	git config user.name "Prueba"
	git config commit.gpgsign false
	for n in 001 002 003; do echo "-- migracion $n" >"$MIG/V${n}__base.sql"; done
	git add -A
	git commit -q -m base
}

teardown() {
	cd /
	rm -rf "$TMP"
}

@test "una migracion nueva con numero mayor pasa" {
	echo "-- nueva" >"$MIG/V004__nueva.sql"
	git add -A && git commit -q -m nueva
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 0 ]
}

@test "modificar una migracion existente falla" {
	echo "-- editada" >>"$MIG/V001__base.sql"
	git add -A && git commit -q -m editar
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 1 ]
	[[ "$output" == *"V001__base.sql"* ]]
}

@test "borrar una migracion existente falla" {
	git rm -q "$MIG/V002__base.sql"
	git commit -q -m borrar
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 1 ]
	[[ "$output" == *"V002__base.sql"* ]]
}

@test "renombrar una migracion existente falla" {
	git mv "$MIG/V003__base.sql" "$MIG/V003__otro.sql"
	git commit -q -m renombrar
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 1 ]
}

@test "una migracion nueva con numero menor o igual al ultimo falla" {
	echo "-- intermedia" >"$MIG/V002b__intermedia.sql"
	git add -A && git commit -q -m intermedia
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 1 ]
	[[ "$output" == *"V002b__intermedia.sql"* ]]
}

@test "V010b con numero menor al ultimo tras V016 falla" {
	for n in 004 005 006 007 008 009 010 011 012 013 014 015 016; do echo "-- $n" >"$MIG/V${n}__x.sql"; done
	git add -A && git commit -q -m mas
	echo "-- b" >"$MIG/V010b__x.sql"
	git add -A && git commit -q -m b
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 1 ]
	[[ "$output" == *"V010b__x.sql"* ]]
}

@test "un archivo no sql en la carpeta se ignora" {
	echo "notas" >"$MIG/LEEME.md"
	git add -A && git commit -q -m notas
	run "$SCRIPT" HEAD~1
	[ "$status" -eq 0 ]
}

@test "una base inexistente devuelve 2" {
	run "$SCRIPT" referencia-que-no-existe
	[ "$status" -eq 2 ]
}
