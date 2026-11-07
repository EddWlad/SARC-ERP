# Pruebas de los scripts de despliegue

Pruebas con datos sintéticos y dobles en `PATH`; nunca tocan la base local `pg_sarc`, el VPS ni Cloudinary.

## Cómo ejecutarlas

```bash
shellcheck deploy/scripts/*.sh deploy/scripts/lib/*.sh deploy/release/*.sh .github/scripts/*.sh
bats deploy/tests/
```

- `shellcheck` usa `deploy/.shellcheckrc` (shell `bash`, severidad `style`).
- Las pruebas definen `SARC_LIB_DIR=deploy/scripts/lib` y anteponen a `PATH` una carpeta de dobles (`curl`, `docker`, `psql`, `systemctl`, `sha256sum`…) creada en `deploy/tests/tmp/` (ignorada por Git).
- Si no tienes las herramientas instaladas, puedes usar contenedores desechables:

```bash
docker run --rm -v "$PWD:/mnt" -w /mnt koalaman/shellcheck:stable deploy/scripts/*.sh
docker run --rm -v "$PWD:/code" bats/bats:latest /code/deploy/tests
```

Sin valores reales: ningún archivo de esta carpeta contiene credenciales, direcciones ni datos de personas.
