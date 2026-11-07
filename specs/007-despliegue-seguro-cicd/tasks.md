---
description: "Task list for Feature 007 — preparación de SARC para despliegue seguro y CI/CD"
---

# Tasks: Preparación de SARC para despliegue seguro y CI/CD

**Input**: Design documents from `specs/007-despliegue-seguro-cicd/` — [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Prerequisites**: spec aclarada (5 decisiones), plan con Constitution Check en PASS.

**Tests**: incluidos. La spec exige criterios verificables (SC-001–SC-018) y el plan define pruebas automáticas y ensayos (§7); cada historia trae sus pruebas antes de la implementación.

**Organization**: una fase por historia de usuario (US1–US6, en orden de prioridad). Las tareas que tocan el VPS, cuentas reales o la configuración de GitHub están en la Fase 9 y **no se ejecutan sin autorización explícita** del responsable del producto.

**Reglas transversales para todas las tareas**:

- No modificar `V001`–`V016` ni ninguna migración existente; esta feature no crea migraciones (Constitución III).
- No cambiar reglas de negocio, permisos, importaciones, indicadores, historiales ni reportes (FR-050).
- Ningún archivo versionado contiene valores reales: plantillas con marcadores `<…>`; el repositorio es **público**.
- Ensayos solo con datos sintéticos, en CI o en una VM desechable; nunca contra la base local `pg_sarc` ni producción (FR-026).
- Avisos y registros sin secretos ni datos personales (FR-036, contrato [health-and-alerts.md](contracts/health-and-alerts.md)).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: historia a la que pertenece (US1–US6)

## Path Conventions

- Backend: `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/`, pruebas en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/`
- Flujos de GitHub: `.github/workflows/`, scripts de CI en `.github/scripts/`
- Infraestructura (plantillas y scripts, sin valores reales): `deploy/`
- Guía de operación: `docs/operacion/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: estructura de carpetas, herramientas de verificación de scripts y registro de la decisión técnica.

- [X] T001 Crear la estructura `deploy/{nginx,systemd,compose,env,sudoers,scripts/lib,release,computador-local,tests}/`, `.github/scripts/` y `docs/operacion/`, cada una con un `README.md` de una línea que diga su propósito y la regla «sin valores reales»
- [X] T002 [P] Añadir `deploy/.shellcheckrc` (shell `bash`, severidad `style`) y `deploy/tests/README.md` con cómo ejecutar `shellcheck deploy/scripts/*.sh deploy/release/*.sh .github/scripts/*.sh` y `bats deploy/tests/`
- [X] T003 [P] Redactar el ADR `sarc-bakend/docs/adr/0012-despliegue-y-recuperacion.md` con las adaptaciones del TRD de [plan.md §8](plan.md#8-plan-de-ejecución-y-riesgos): VPS 6 vCPU/12 GB como referencia; Auto Backup de Contabo como complemento; copia independiente en computador local; pérdida máxima 24 h con copia lógica diaria en lugar de archivado continuo; salud pública reducida a *readiness*; comprobación posterior sin Swagger; certificado Let's Encrypt `shortlived` para IP
- [X] T004 [P] Añadir a `.gitignore` las rutas de trabajo locales de ensayo `deploy/tests/tmp/` y `**/*.dump`, `**/*.dump.age` (copias nunca se versionan)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: biblioteca común de scripts, plantilla de configuración e inventario de variables que usan US2–US5.

**⚠️ CRITICAL**: ninguna historia empieza antes de completar esta fase.

- [X] T005 Crear `deploy/scripts/lib/common.sh` con: `set -Eeuo pipefail`; `log_info/log_error` que escriben `fecha nivel paso mensaje` sin imprimir variables de entorno; `require_env NOMBRE…` que falla con código 12 nombrando solo la variable ausente; `ping_check <nombre> [ok|fail|start]` que llama a la URL `HC_URL_<NOMBRE>` del entorno con `curl -fsS -m 10 --retry 3` sin cuerpo; `with_lock <archivo>` con `flock -n` que falla con código 10; todos los scripts cargan la biblioteca desde `${SARC_LIB_DIR:-/usr/local/lib/sarc}/common.sh` y llaman a los demás por su nombre instalado en `/usr/local/sbin/` (las pruebas definen `SARC_LIB_DIR=deploy/scripts/lib` y anteponen dobles en `PATH`)
- [X] T006 [P] Crear la prueba `deploy/tests/common.bats`: `require_env` falla con código 12 y no imprime valores; `with_lock` devuelve 10 si el bloqueo está tomado; `ping_check` no envía cuerpo (usar `curl` simulado en `PATH`)
- [X] T007 [P] Crear la plantilla `deploy/env/sarc.env.example` con todas las variables del inventario de [data-model.md](data-model.md#valor-de-configuración--secreto) y valores `<definir>`: `SPRING_PROFILES_ACTIVE=prod`, `SARC_DB_URL`, `SARC_DB_USER`, `SARC_DB_PASSWORD`, `POSTGRES_PASSWORD`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `SARC_STORAGE_CLOUDINARY_ROOT_FOLDER=sarc-prod`, `SARC_DEPLOY_ENV=produccion` (comentario: «`ensayo` solo en la VM de ensayo»), `SARC_VERIFY_REPORT_ID` (comentario: «se define tras la primera instalación»), `SARC_BOOTSTRAP_ADMIN_EMAIL`, `SARC_BOOTSTRAP_ADMIN_PASSWORD` (comentario: «solo primer arranque; borrar después»), `HC_URL_*` por cada comprobación del contrato de avisos; y la plantilla aparte `deploy/env/verificacion.env.example` con `SARC_VERIFY_USER` y `SARC_VERIFY_PASSWORD` (comentario: «se rellena tras crear la cuenta de verificación; 0600, solo la cuenta que ejecuta `sarc-deploy`; `sarc.service` nunca lo carga»)
- [X] T008 [P] Crear `docs/operacion/inventario-variables.md` con una fila por variable (nombre, propósito, obligatoria en prod, dónde vive: `/etc/sarc/sarc.env` / `/etc/sarc/verificacion.env` / secreto del entorno `production` / computador local, responsable, rotación, cómo verificar que la anterior quedó revocada), sin valores
- [X] T009 [P] Crear `docs/operacion/decisiones-previas.md` (etapa E0) con las decisiones pendientes y su estado: repositorio público o privado (R11), computador local designado, suplente, cuenta Healthchecks.io (uso comercial y correo confirmados), alcance y cuota de Backup en la cuenta de Cloudinary compartida, rol de la cuenta de verificación posterior (menor privilegio, nunca ADMINISTRADOR), activación de «prevent self-review», y los requisitos del computador local (sistema operativo; si no es Windows, los scripts de `deploy/computador-local/` se reescriben en Bash con el mismo contrato; Docker o PostgreSQL 18 cliente para `pg_restore --list` y el ensayo; disco cifrado; espacio para 30 diarias + 12 mensuales + exportación de Cloudinary, medido con el tamaño de la primera copia)

**Checkpoint**: biblioteca y plantillas listas; `shellcheck` y `bats deploy/tests/common.bats` en verde.

---

## Phase 3: User Story 1 - Saber que una versión es apta antes de publicarla (Priority: P1) 🎯 MVP

**Goal**: todo cambio pasa verificaciones completas (incluidas dependencias, secretos e inmutabilidad de migraciones) y cada `main` verde produce una versión candidata inmutable y trazable.

**Independent Test**: un PR limpio queda verde; PRs de ensayo con prueba rota, `V001` modificada, dependencia vulnerable y secreto ficticio quedan rojos con la comprobación nombrada; tras integrar, existe un Release pre-release con `manifest.json` y `SHA256SUMS` cuyas sumas coinciden al recalcularlas (quickstart 1–3).

### Tests for User Story 1

- [X] T010 [P] [US1] Crear `deploy/tests/check-migrations.bats` sobre un repositorio Git temporal: añadir `V017__x.sql` pasa; modificar o borrar un `V*.sql` existente falla; añadir `V010b__x.sql` con número menor o igual al último falla
- [X] T011 [P] [US1] Crear `deploy/tests/build-manifest.bats`: con artefactos ficticios, el manifiesto cumple [release-manifest.md](contracts/release-manifest.md) (`schema: 1`, `version` = `AAAA.MM.DD-<run>-<sha7>`, `checks` con todas las conclusiones `success`, `artifacts[].sha256` igual a `sha256sum`, `migrations.latest`, `schemaChanges`); falla si alguna comprobación no es `success`; no incluye ninguna variable de entorno con `SECRET`, `PASSWORD`, `KEY` o `TOKEN`
- [X] T012 [P] [US1] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/architecture/MigrationUpgradeTest.java` (Testcontainers PG18, `TestcontainersConfiguration`): aplica Flyway hasta la penúltima migración, inserta datos sintéticos mínimos con las fixtures existentes de `support/`, migra a la última y comprueba que el contexto arranca con `ddl-auto: validate` y que los datos siguen presentes

### Implementation for User Story 1

- [X] T013 [US1] Crear `.github/scripts/check-migrations-immutable.sh` (usa `deploy/scripts/lib/common.sh` solo para `log_*`): compara `sarc-bakend/src/main/resources/db/migration/` contra la base (`origin/${GITHUB_BASE_REF}` en PR, `HEAD^` en `push`); falla si cambia o desaparece un archivo existente o si un archivo nuevo no tiene número mayor que el último (hace pasar T010)
- [X] T014 [US1] Ampliar `.github/workflows/ci.yml` con el trabajo `migraciones-inmutables` (checkout con `fetch-depth: 0`, ejecuta T013) y el trabajo `scripts-despliegue` (`shellcheck` y `bats deploy/tests/`)
- [X] T015 [US1] Ampliar `.github/workflows/ci.yml` con el trabajo `dependencias`: en `pull_request`, `actions/dependency-review-action` con `fail-on-severity: high`; en `pull_request` y `push` a `main`, **OSV-Scanner** (binario oficial v2.7.0 fijado por versión y suma SHA-256, porque la action publicada no admite umbral de severidad; el umbral lo aplica `.github/scripts/osv-gate.sh`) sobre `sarc-bakend/pom.xml` y `sarc-frontend/package-lock.json`, que falla con severidad alta o crítica (CVSS ≥ 7.0); severidad media solo como aviso en `$GITHUB_STEP_SUMMARY`; las excepciones aceptadas viven en `osv-scanner.toml` en la raíz con motivo y fecha de revisión (FR-005)
- [X] T016 [US1] Ampliar `.github/workflows/ci.yml` con el trabajo `secretos`: **gitleaks** (CLI, licencia MIT) descargado en una versión fija con verificación de su suma publicada, `gitleaks git --redact --log-opts=<rango de commits nuevos>` y `gitleaks dir --redact` sobre el árbol; cualquier hallazgo falla el trabajo y `--redact` impide imprimir el valor; reglas propias o permisos en `.gitleaks.toml` en la raíz (FR-005, SC-005)
- [X] T017 [US1] Añadir en `ci.yml` un resumen (`$GITHUB_STEP_SUMMARY`) por trabajo con la clasificación de FR-004 según esta regla: es **fallo de infraestructura** si el paso que falla es de preparación (`actions/checkout`, `setup-java`, `setup-node`, descarga de dependencias o de imágenes de Testcontainers, descarga de herramientas) o si el registro contiene un error de red/tiempo de espera (`Could not resolve host`, `Connection timed out`, `ETIMEDOUT`, `503 Service Unavailable`); cualquier otro paso fallido es **fallo del cambio**. La clasificación solo informa; ambos bloquean la versión. Documentar en el mismo archivo que se reintenta con «Re-run failed jobs»
- [X] T018 [US1] Añadir en `ci.yml` el trabajo `avisar-fallo` con `needs` de todos los trabajos, `if: failure()` y permisos `issues: write` y `pull-requests: write`: en `push` a `main` abre o comenta el *issue* abierto con etiqueta `alerta-operativa` y título «CI fallido en main», mencionando y asignando a responsable y suplente (logins en las variables de repositorio `SARC_RESPONSABLE` y `SARC_SUPLENTE`), con solo el nombre del trabajo fallido, el commit corto y el enlace a la ejecución; en `pull_request` del propio repositorio deja un comentario en el PR que menciona a ambos con el nombre del trabajo fallido y el enlace (si ya existe, lo edita en lugar de repetirlo); en un PR desde un *fork* no comenta, porque el token no tiene escritura, y basta la comprobación visible (FR-038)
- [X] T019 [P] [US1] Activar `build-info` en `spring-boot-maven-plugin` de `sarc-bakend/pom.xml` y aceptar la versión desde la propiedad `-Dsarc.version=<version>` (por omisión `0.0.1-SNAPSHOT`), sin dependencias nuevas
- [X] T020 [US1] Crear `deploy/release/build-manifest.sh` que genera `manifest.json` y `SHA256SUMS` según [release-manifest.md](contracts/release-manifest.md) a partir de los artefactos, el commit, el `run_id`, las conclusiones de los trabajos de CI y la lista de `V*.sql` con su suma; `schemaChanges` = la última migración difiere de la del Release candidato anterior (solo informativo; no decide nada en producción); como la versión publicada puede ser otra, `sarc-deploy.sh` vuelve a decidir la compatibilidad comparando `migrations.latest` con la última migración aplicada en producción (hace pasar T011)
- [X] T021 [US1] Crear `.github/workflows/version-candidata.yml`: disparo `workflow_run` del flujo CI sobre `main` con `conclusion == 'success'`; construye una vez `./mvnw -B -DskipTests -Dsarc.version=<version> package` y `npm ci && npm run build`; empaqueta `sarc-backend-<version>.jar` y `sarc-frontend-<version>.tar.gz`; ejecuta T020; escanea los artefactos con la herramienta de T016 usando `gitleaks dir --redact --max-archive-depth 2` para revisar el contenido del JAR y del `.tar.gz` (si la versión fijada no admite archivos comprimidos, los extrae a un temporal y escanea esa carpeta); crea tag `<version>` sobre el commit exacto y Release **pre-release** con los cuatro assets; permisos mínimos (`contents: write`) y sin secretos de entorno
- [X] T022 [US1] Documentar en `docs/operacion/version-candidata.md` cómo localizar una versión, su commit, sus verificaciones y comprobar sus sumas en menos de 2 minutos (SC-003), y qué significa `schemaChanges`

**Checkpoint**: US1 completa; se cumple el Independent Test con PRs de ensayo en una rama desechable.

---

## Phase 4: User Story 2 - Publicar solo con autorización explícita y poder comprobar el resultado (Priority: P1)

**Goal**: una versión candidata llega a producción solo tras aprobación en el entorno `production`, con comprobaciones previas, copia previa, migración controlada, comprobaciones posteriores y registro.

**Independent Test**: contra la VM de ensayo, sin aprobación no cambia nada; con aprobación queda `COMPLETADA` con aprobador y versión; una segunda publicación simultánea se rechaza; una migración de ensayo fallida deja `FALLIDA`, mantenimiento activo y estado del esquema informado (quickstart 4–9, 5b y 8b).

### Tests for User Story 2

- [X] T023 [P] [US2] Crear `deploy/tests/sarc-deploy-prechecks.bats` con dobles de `curl`, `sha256sum`, `systemctl`, `docker` y `psql` en `PATH`: cada paso 0–6 de [deploy-interface.md](contracts/deploy-interface.md) devuelve su código (10 bloqueo, 11 suma, 12 configuración, 18 prueba de recepción ausente o desactualizada, 13 certificado inválido o < 72 h, 14 recibo de copia ≥ 24 h, 15 esquema incoherente, 16 copia previa fallida) y en esos casos **no** se llama a `systemctl stop sarc`; con `--primera-instalacion` los pasos 4 y 6 no se ejecutan y los demás sí, y el modo se rechaza si la base tiene tablas de SARC; además prueba `sarc-deploy-ssh-wrapper.sh` (T032): `deploy <version>`, `deploy --primera-instalacion <version>` y `deploy --verificar <version>` pasan; se rechazan sin repetir el texto recibido los argumentos extra, `;`, `&&`, `|`, `$(…)`, comillas invertidas, saltos de línea, una versión con formato incorrecto, un modo desconocido y la entrada vacía, y `sarc-deploy` invocado directamente con argumentos fuera de esas tres formas termina con código 2 sin ejecutar ningún paso (FR-031, FR-032)
- [X] T024 [P] [US2] Crear `deploy/tests/no-destructive.bats` que recorre `deploy/scripts/*.sh`, `deploy/release/*.sh`, `.github/scripts/*.sh` y `.github/workflows/*.yml` y falla si aparece, sin distinguir mayúsculas, `DROP `, `TRUNCATE`, `DELETE FROM`, `pg_restore --clean`, `dropdb`, `docker volume rm`, `docker compose down -v`, `rm -rf /var/lib/postgresql` o `rm -rf /opt/sarc/releases` fuera de la lista permitida `deploy/tests/destructive-allowlist.txt` (que solo admite la poda de versiones antiguas en `sarc-deploy.sh` y la retención de copias en `sarc-backup.sh`, cada una con su línea exacta) (FR-021, FR-022)
- [X] T025 [P] [US2] Crear `deploy/tests/sarc-deploy-flow.bats`: camino feliz registra en `deploy-history.jsonl` una línea con los campos del registro de publicación de [data-model.md](data-model.md#registro-de-publicación) y `result: COMPLETADA`; si readiness no llega a `UP` en 3 min devuelve 20, deja el mantenimiento y no reactiva la versión anterior cuando hubo migraciones; si una comprobación posterior falla devuelve 21; `--primera-instalacion` con disponibilidad, base de datos y versión correctas y sin cuenta de verificación registra `EN_CURSO`, retira el mantenimiento y devuelve 0; `--verificar` sobre una versión que no está `EN_CURSO` se rechaza; sobre una `EN_CURSO` registra `COMPLETADA` si todo pasa y, si algo falla, activa el mantenimiento, registra `FALLIDA` y devuelve 21; una publicación normal con la última `EN_CURSO` devuelve 10; `migrationsApplied[]` refleja las filas nuevas de `flyway_schema_history`
- [X] T026 [P] [US2] Crear `deploy/tests/post-checks.bats`: cada comprobación posterior del contrato produce un resultado nombrado y el veredicto es único; la credencial de la cuenta de verificación no aparece en la salida; con `--solo-basicas` y sin cuenta ni reporte de verificación, el veredicto es `EN_CURSO` con ingreso, usuarios, módulos y archivos `PENDIENTE`; sin esa opción, la falta de la credencial o de `SARC_VERIFY_REPORT_ID` da `FALLIDA`; la versión se compara con `build-info.properties` del JAR, nunca con `/actuator/info`

### Implementation for User Story 2

- [X] T027 [US2] Crear `deploy/scripts/sarc-schema-check.sh` que lee `flyway_schema_history` con `docker compose exec -T postgres psql` y lo compara con `migrations.files` del manifiesto: falla con 15 si hay una aplicada desconocida, con suma distinta, una fila `success = false` o una pendiente con número menor que la última aplicada; imprime solo nombres de migración
- [X] T028 [US2] Crear `deploy/scripts/sarc-post-checks.sh` según la tabla «Comprobaciones posteriores» de [deploy-interface.md](contracts/deploy-interface.md): disponibilidad (`/` y readiness), base de datos (readiness), ingreso con la cuenta de verificación (credencial leída de `/etc/sarc/verificacion.env`; cookie y CSRF como la SPA), una lectura por módulo permitida a ese rol, solicitud de enlace firmado del **reporte de verificación** cuyo identificador está en `SARC_VERIFY_REPORT_ID` y descarga del archivo, y versión leída de `META-INF/build-info.properties` del JAR enlazado por `current` (`unzip -p`) y de `manifest.json`, sin usar `/actuator/info` (exige ADMINISTRADOR); acepta `--solo-basicas` para la primera instalación de [deploy-interface.md](contracts/deploy-interface.md#primera-instalación): solo exige disponibilidad, base de datos y versión, deja ingreso, usuarios, módulos y archivos como `PENDIENTE` y su veredicto es `EN_CURSO` (nunca `COMPLETADA`); sin esa opción exige todas, y sin la credencial de la cuenta de verificación o sin `SARC_VERIFY_REPORT_ID` el veredicto es `FALLIDA` (código 21); veredicto en < 10 min (SC-011)
- [X] T029 [US2] Crear `deploy/scripts/sarc-alerts-gate.sh` (paso 2b de [deploy-interface.md](contracts/deploy-interface.md)): lee `/var/lib/sarc/prueba-recepcion.ok` (fecha y huella SHA-256 de `/etc/sarc/avisos.conf`, que lista los destinatarios y canales vigentes) y falla con 18 si el archivo no existe, si la huella no coincide con la de `avisos.conf` actual o si falta la confirmación de alguno de los dos responsables; no imprime direcciones de correo (FR-038a)
- [X] T030 [US2] Crear `deploy/scripts/sarc-deploy.sh` que implementa los pasos 0–11 de [deploy-interface.md](contracts/deploy-interface.md) usando `common.sh`, T027, T028, T029, el script de copia de US4 (`sarc-backup.sh previa`) y la comprobación de certificado de US5, más el modo `--primera-instalacion` (sin los pasos 4 y 6 —recibo de copia local y copia previa— ni comprobación de esquema anterior, porque la base está vacía y no hay datos que perder; se niega si la base ya tiene tablas de SARC; ejecuta `sarc-post-checks.sh --solo-basicas` y, si pasa, registra `EN_CURSO`, retira el mantenimiento, libera el bloqueo y termina con 0) y el modo `--verificar` (solo si la última línea es `EN_CURSO` para esa versión y `current` apunta a ella; ejecuta `sarc-post-checks.sh` completo sin reinicio y registra `COMPLETADA`; si falla, activa el mantenimiento, registra `FALLIDA` y devuelve 21); una publicación normal se rechaza con 10 mientras la última esté `EN_CURSO`; instala en `/opt/sarc/releases/<version>/`, cambia el enlace `current` de forma atómica, conserva las 5 últimas versiones y escribe `/var/lib/sarc/deploy-history.jsonl` con `migrationsApplied[]` (filas nuevas de `flyway_schema_history`), que también imprime al terminar para el flujo `publicar`; antes de cualquier paso vuelve a validar sus argumentos con el mismo patrón que T032 y termina con código 2 sin repetir la entrada si no coinciden con una de las tres formas (hace pasar T023–T026)
- [X] T031 [P] [US2] Crear `deploy/nginx/maintenance.html` estático, en español, sin recursos externos, que indique «SARC está en mantenimiento» y sin detalles técnicos; el script activa/desactiva el mantenimiento con un archivo indicador que lee Nginx (T058)
- [X] T032 [P] [US2] Crear `deploy/scripts/sarc-deploy-ssh-wrapper.sh` para el comando forzado de `authorized_keys`: acepta solo `SSH_ORIGINAL_COMMAND` con la forma `deploy <version>`, `deploy --primera-instalacion <version>` o `deploy --verificar <version>`, donde `<version>` cumple `^[0-9]{4}\.[0-9]{2}\.[0-9]{2}-[0-9]+-[0-9a-f]{7}$`; cualquier otra entrada se rechaza y se registra sin repetir el texto recibido (lo prueba T023); las formas válidas se ejecutan como `sudo -n /usr/local/sbin/sarc-deploy <argumentos>`; crear también `deploy/sudoers/sarc-deploy` con la única regla `sarc-deploy ALL=(root) NOPASSWD: /usr/local/sbin/sarc-deploy`, validada en CI con `visudo -c -f` dentro de un contenedor desechable
- [X] T033 [US2] Crear `.github/workflows/publicar.yml` según [deploy-interface.md](contracts/deploy-interface.md): `workflow_dispatch` con entradas `version` y `modo` (`normal` por omisión, `primera-instalacion` o `verificar`); trabajo `preparar` sin secretos (Release existe, CI de su commit exitoso, `SHA256SUMS` correcto); trabajo `desplegar` con `environment: production`, `concurrency: { group: production, cancel-in-progress: false }`, SSH con `secrets.DEPLOY_SSH_KEY` y `secrets.DEPLOY_KNOWN_HOSTS` hacia `deploy <version>`, `deploy --primera-instalacion <version>` o `deploy --verificar <version>` según `modo` (los tres con la misma aprobación del entorno); trabajo `avisar` con `if: failure() || cancelled()` que abre o comenta el *issue* `alerta-operativa` mencionando y asignando a responsable y suplente (logins en las variables de repositorio `SARC_RESPONSABLE` y `SARC_SUPLENTE`; el trabajo `avisar` no declara `environment`, para no pedir otra aprobación) y envía `/fail` a `secrets.HC_URL_PUBLICACION`; si una publicación en `modo: normal` queda `COMPLETADA` y la salida de `sarc-deploy` informa `migrationsApplied[]` no vacío (la primera instalación no cuenta como cambio crítico), abre un *issue* `ensayo-restauracion` con etiqueta `pendiente` (cambio crítico, FR-042); resumen con versión, modo, aprobador y resultado (`EN_CURSO` se muestra como «pendiente de verificación»)
- [X] T034 [US2] Documentar en `docs/operacion/publicar.md`: solicitar, aprobar (quién, suplente, «prevent self-review» según `decisiones-previas.md`), leer el resultado, códigos 2 y 10–21 y qué hacer en cada uno (incluido 2: revisar la entrada `modo`/`version` del flujo; y 18: repetir la prueba de recepción), cómo distinguir producción sin cambios (2 y 10–18) de producción en mantenimiento (20–21), el modo `primera-instalacion` con la definición posterior del reporte de verificación y el modo `verificar` que cierra la publicación, y cómo cambiar al responsable o al suplente: revisores del entorno `production`, variables `SARC_RESPONSABLE`/`SARC_SUPLENTE`, destinatarios de Healthchecks.io, `/etc/sarc/avisos.conf` y nueva prueba de recepción, exigida por el código 18 (FR-012, FR-038a)

**Checkpoint**: US2 completa en la VM de ensayo (quickstart 4–9, 5b y 8b) con evidencia en `specs/007-despliegue-seguro-cicd/evidence/`.

---

## Phase 5: User Story 3 - Configuración y secretos separados por entorno (Priority: P1)

**Goal**: producción no arranca con configuración incompleta, cada entorno tiene base, archivos y credenciales propias, y ningún secreto vive en el repositorio, artefactos o registros.

**Independent Test**: el arranque `prod` sin `CLOUDINARY_*` falla nombrando la variable sin valores; los escaneos de T016 sobre repositorio, artefactos y registros dan 0 hallazgos; un ensayo completo deja iguales los conteos de la base local (quickstart 7, 15 y SC-006).

### Tests for User Story 3

- [X] T035 [P] [US3] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/shared/storage/StorageConfigTest.java` con `ApplicationContextRunner`: con `sarc.storage.cloudinary.required=true` y credenciales vacías el contexto falla y el mensaje nombra las propiedades ausentes sin incluir ningún valor; con `required=false` (por omisión) arranca y devuelve el almacenamiento deshabilitado; con credenciales completas devuelve `CloudinaryFileStorage`
- [X] T036 [P] [US3] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/shared/ProductionProfileConfigTest.java` que carga `application-prod.yaml` y comprueba: `sarc.storage.cloudinary.required=true`, Swagger y api-docs desactivados, `forward-headers-strategy: native` y ausencia de valores por omisión para `SARC_DB_*`

### Implementation for User Story 3

- [X] T037 [US3] Añadir `@DefaultValue("false") boolean required` a `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/storage/StorageProperties.java` y, en `StorageConfig.java`, lanzar `IllegalStateException` al crear el bean cuando `required` y no `isConfigured()`, con mensaje que nombra `sarc.storage.cloudinary.cloud-name/api-key/api-secret` ausentes sin valores (FR-025); `dev` y pruebas sin cambios (hace pasar T035)
- [X] T038 [US3] Añadir `sarc.storage.cloudinary.required: true` a `sarc-bakend/src/main/resources/application-prod.yaml` (hace pasar T036)
- [X] T039 [P] [US3] Crear `deploy/systemd/sarc.service`: usuario y grupo `sarc` sin shell, `EnvironmentFile=/etc/sarc/sarc.env`, `ExecStart=/usr/bin/java -jar /opt/sarc/current/backend/sarc-backend.jar`, `WorkingDirectory=/opt/sarc/current`, `Restart=on-failure`, endurecimiento (`NoNewPrivileges`, `ProtectSystem=strict`, `ProtectHome`, `PrivateTmp`, `ReadWritePaths=/var/lib/sarc`), salida a journald
- [X] T040 [P] [US3] Crear `deploy/compose/compose.prod.yaml`: `postgres:18`, `ports: ["127.0.0.1:5432:5432"]`, volumen externo `sarc_pgdata` montado en `/var/lib/postgresql`, `POSTGRES_PASSWORD` interpolada desde `/etc/sarc/sarc.env` con `docker compose --env-file` (nunca en el archivo; así el contenedor no recibe el resto de secretos, como los de Cloudinary), `healthcheck` con `pg_isready`, `restart: unless-stopped`, sin otros servicios
- [X] T041 [P] [US3] Crear `deploy/scripts/sarc-local-db-snapshot.sh` para ensayos (SC-006): imprime conteos por tabla de una base indicada por argumento y se niega a ejecutarse si la URL apunta a un host distinto de `localhost`/`127.0.0.1`; nunca escribe
- [X] T042 [US3] Documentar en `docs/operacion/entornos-y-secretos.md`: tabla de entornos de [plan.md §2](plan.md#2-arquitectura-de-producción) con carpetas Cloudinary `sarc-prod`/`sarc-test` y claves distintas; permisos de `/etc/sarc/sarc.env` (0640 root:sarc) y de `/etc/sarc/verificacion.env` (0600, solo la cuenta que ejecuta `sarc-deploy`; `sarc.service` no lo carga); secretos del entorno `production` en GitHub; admin inicial (definir `SARC_BOOTSTRAP_ADMIN_*`, primer arranque, cambio obligatorio de clave, borrar ambas variables y reiniciar, FR-028); rotación y secreto expuesto (revocar, rotar, confirmar que el anterior falla, revisar registros, FR-029)

**Checkpoint**: US3 completa; `./mvnw -B verify` y frontend en verde sin cambios de comportamiento.

---

## Phase 6: User Story 4 - Respaldos independientes y recuperación comprobada (Priority: P1)

**Goal**: copia lógica diaria y previa, cifrada, descargada y verificada por el computador local, con antigüedad vigilada, Cloudinary protegido y ensayo de restauración repetible.

**Independent Test**: en la VM de ensayo, la copia nocturna produce `.dump.age`, `.sha256` y `.meta.json`; el computador local la descarga, verifica y deja recibo; sin descarga llega el aviso y `sarc-deploy` falla con 14; el ensayo restaura en un PG18 desechable y pasa el 100 % de las comprobaciones (quickstart 8, 10–13).

### Tests for User Story 4

- [X] T043 [P] [US4] Crear `deploy/tests/sarc-backup.bats`: genera los tres archivos del contrato [backup-artifacts.md](contracts/backup-artifacts.md) con nombre `sarc-<AAAAMMDDThhmmZ>-<kind>.dump.age`; `.meta.json` contiene `createdAt`, `kind`, `pgVersion`, `latestMigration`, `tableCounts` y huella de clave, y ningún dato de filas; la retención borra solo copias de más de 7 días; un fallo de `pg_dump` envía `/fail` y devuelve distinto de cero
- [X] T044 [P] [US4] Crear `deploy/tests/backup-freshness.bats`: el comprobador de recibos devuelve 14 si el recibo verificado más reciente tiene 24 h o más, o si no hay recibo, y 0 si tiene menos

### Implementation for User Story 4

- [X] T045 [US4] Crear `deploy/scripts/sarc-backup.sh <nocturna|previa|manual>`: `docker compose exec -T postgres pg_dump -Fc` de la base `sarc`, cifrado con `age -r` usando la clave pública en `/etc/sarc/backup-recipient.pub`, `.sha256` y `.meta.json` según el contrato; permisos de lectura solo para `sarc-backup`; retención 7 días; `ping_check copia-nocturna` en `nocturna` (hace pasar T043)
- [X] T046 [US4] Crear `deploy/scripts/sarc-backup-freshness.sh` que revisa `received/*.ok` y devuelve 14 si no hay recibo verificado de menos de 24 h; lo usa `sarc-deploy.sh` en el paso 4 (hace pasar T044)
- [X] T047 [P] [US4] Crear `deploy/systemd/sarc-backup.service` y `deploy/systemd/sarc-backup.timer` (diario 02:00 `America/Guayaquil`, `Persistent=true`); el servicio con `User=root` (necesita `docker` y leer `/etc/sarc/sarc.env`), `ExecStart=/usr/local/sbin/sarc-backup nocturna`, `NoNewPrivileges=true`, `PrivateTmp=true`, `ProtectHome=true`, `ProtectSystem=full` y `ReadWritePaths=/var/backups/sarc`
- [X] T048 [P] [US4] Crear `deploy/computador-local/Descargar-CopiaSarc.ps1`: SFTP con la clave de `sarc-backup`; descarga copias nuevas; verifica `.sha256`; descifra en un temporal con la clave privada `age`; ejecuta `pg_restore --list` con un contenedor `postgres:18` desechable; borra el temporal; guarda la copia cifrada con retención 30 diarias + 12 mensuales; sube `received/<archivo>.ok` con fecha y suma; envía latido `descarga-local` o `/fail`; no registra rutas ni salidas con datos
- [X] T049 [P] [US4] Crear `deploy/computador-local/Registrar-Tarea.ps1` que registra la tarea programada diaria y «al iniciar sesión si se perdió la anterior» para T048, con el usuario restringido del computador
- [X] T050 [P] [US4] Crear `deploy/computador-local/Exportar-Cloudinary.ps1`: con la clave de API dedicada lista la carpeta `SARC_STORAGE_CLOUDINARY_ROOT_FOLDER` (`image` y `raw`), descarga de forma incremental por `public_id` + `version` mediante URL firmadas y escribe `export-<fecha>.json` con `public_id`, `resource_type`, `version`, `bytes` y `sha256`; solo lectura sobre Cloudinary
- [X] T051 [US4] Crear `deploy/computador-local/Ensayar-Restauracion.ps1` según «Ensayo de restauración» de [backup-artifacts.md](contracts/backup-artifacts.md): **antes de restaurar**, comprobación de aislamiento que aborta si la base de destino no es un contenedor creado por el propio script en `localhost`, si existe alguna variable `CLOUDINARY_*` o `SARC_DB_*` en el entorno, o si el puerto elegido ya está en uso (para no tocar `pg_sarc` ni producción), y registra el resultado de esa comprobación en el informe; luego PG18 desechable, `pg_restore`, comparación de `latestMigration` y `tableCounts` con `.meta.json`, presencia de auditoría e historiales de importación, muestra de referencias de archivos presentes en la última exportación; si arranca el JAR, lo hace **sin** variables `CLOUDINARY_*`; mide el tiempo, imprime el informe para el *issue* `ensayo-restauracion` y destruye el entorno
- [X] T052 [P] [US4] Crear `.github/workflows/control-ensayos.yml`: `schedule` diario y `workflow_dispatch`, permisos mínimos (`issues: write`); busca el último *issue* `ensayo-restauracion` cerrado con etiqueta `aprobado` y, si tiene 28 días o más, abre o comenta el *issue* `alerta-operativa` «Ensayo de restauración por vencer» mencionando a responsable y suplente (variables de repositorio); a los 31 días, «Ensayo de restauración vencido»; también avisa si un *issue* `ensayo-restauracion` con etiqueta `pendiente` lleva más de 7 días abierto; los umbrales (28, 31 y 7 días) solo se pueden reducir mediante entradas de `workflow_dispatch` para el ensayo, nunca en la ejecución programada; si aún no hay ningún ensayo aprobado, cuenta desde la variable de repositorio `SARC_PRODUCCION_DESDE` y, si no está definida (antes de producción), no avisa; no lee datos de SARC (FR-042, SC-018)

**Checkpoint**: US4 completa en la VM de ensayo y en un computador local de prueba con carpeta `sarc-test`.

---

## Phase 7: User Story 5 - Servicio expuesto de forma mínima y observable (Priority: P2)

**Goal**: solo HTTPS (y el desafío ACME en 80) desde Internet, base y servicios internos cerrados, salud pública mínima, registros con `traceId` y avisos que llegan a ambos responsables, incluida la caída total del VPS.

**Independent Test**: en la VM, escaneo externo muestra solo 22/80/443, `/actuator/**` responde 404 salvo readiness (`UP`/`DOWN` sin detalles), y detener el servicio dispara el aviso de la comprobación de prueba (quickstart 14; certificado y caída real en Fase 9).

### Tests for User Story 5

- [X] T053 [P] [US5] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/shared/HealthReadinessTest.java` (MockMvc + Testcontainers): `GET /actuator/health/readiness` sin sesión devuelve `UP` con solo el campo `status`; con la base detenida o simulada caída devuelve `DOWN`; `GET /actuator/info` sin sesión devuelve 401/403
- [X] T054 [P] [US5] Crear `deploy/tests/checks.bats`: `sarc-check-cert.sh` falla si quedan < 72 h, si el archivo no existe o si la cadena no valida contra el almacén de confianza del sistema, y envía `/fail` a `certificado`; con `SARC_DEPLOY_ENV=ensayo` y `SARC_TEST_CA_FILE` valida contra esa CA de prueba; con `SARC_DEPLOY_ENV=produccion` (o sin definir) ignora `SARC_TEST_CA_FILE` y falla si está definida; `sarc-check-disk.sh` falla con uso > 80 % y envía `/fail` a `disco`; `sarc-heartbeat.sh` envía `vps-latido` siempre y `servicio` solo si readiness local es `UP`
- [X] T055 [P] [US5] Crear `deploy/tests/nginx-config.bats` que valida la plantilla renderizada con `nginx -t` en un contenedor `nginx` desechable y comprueba con `curl` local: 80 redirige a 443 salvo `/.well-known/acme-challenge/`; `/actuator/health/readiness` se reenvía; cualquier otro `/actuator/` responde 404; el indicador de mantenimiento sirve `maintenance.html` con 503

### Implementation for User Story 5

- [X] T056 [US5] En `sarc-bakend/src/main/resources/application-prod.yaml`, incluir la base de datos en el grupo *readiness* (`management.endpoint.health.group.readiness.include` con `readinessState` y `db`), manteniendo `show-details: never`; verificar el nombre exacto del indicador en Spring Boot 4.1 (hace pasar T053)
- [X] T057 [US5] En `sarc-bakend/src/main/resources/application.yaml`, definir `logging.pattern.console` con fecha, nivel, `%X{traceId}`, logger abreviado y mensaje, sin cambiar qué se registra; comprobar que `RequestTraceFilter` sigue cubriendo todas las peticiones (FR-036)
- [X] T058 [US5] Crear `deploy/nginx/sarc.conf.template` con marcadores `<IP_PUBLICA>`: servidor 80 con `/.well-known/acme-challenge/` desde `/var/www/certbot` y 301 para lo demás; servidor 443 con certificado en `/etc/letsencrypt/live/<IP_PUBLICA>/`, HSTS sin `includeSubDomains`, cabeceras de seguridad, `root /opt/sarc/current/frontend` con `try_files $uri /index.html`, proxy `/api/` a `127.0.0.1:8080` con `X-Forwarded-For/Proto`, `location = /actuator/health/readiness` reenviado, `location /actuator/` 404, límite de peticiones en `/api/v1/auth/login` (ADR 0001), `client_max_body_size 21m`, mantenimiento por archivo indicador, sin registrar `Cookie`/`Authorization` (hace pasar T055)
- [X] T059 [P] [US5] Crear `deploy/scripts/sarc-check-cert.sh` (vigencia con `openssl x509 -checkend 259200`, cadena con `openssl verify` contra el almacén del sistema; modo de ensayo solo con `SARC_DEPLOY_ENV=ensayo` y una CA de prueba generada para la VM, nunca en producción; `ping_check certificado`), `deploy/scripts/sarc-check-disk.sh` (umbral 80 %, `ping_check disco`) y `deploy/scripts/sarc-heartbeat.sh` (`vps-latido` y `servicio`) (hace pasar T054)
- [X] T060 [P] [US5] Crear `deploy/systemd/sarc-heartbeat.timer` (5 min), `deploy/systemd/sarc-check-disk.timer` (1 h), `deploy/systemd/sarc-check-cert.timer` (diario) con sus `.service`: `User=root` (leen `/etc/sarc/sarc.env` y, el del certificado, `/etc/letsencrypt`), `ExecStart=/usr/local/sbin/<nombre>`, `NoNewPrivileges=true`, `PrivateTmp=true`, `ProtectHome=true` y `ProtectSystem=strict` (no escriben en disco)
- [X] T061 [P] [US5] Crear `deploy/scripts/certbot-deploy-hook.sh` que ejecuta `nginx -t && systemctl reload nginx` y `sarc-check-cert.sh`; documentar en `docs/operacion/certificado.md` la emisión con Certbot ≥ 5.4 (snap) `certonly --webroot -w /var/www/certbot --ip-address <IP_PUBLICA> --preferred-profile shortlived` primero con `--staging`, la renovación automática, el `--deploy-hook`, el aviso a 72 h, el bloqueo de publicación y cómo incorporar un dominio después (FR-033a)
- [X] T062 [US5] Crear `.github/workflows/sonda-externa.yml`: `schedule` cada 15 min y `workflow_dispatch`; `curl -fsS --max-time 20` a `https://${{ vars.SARC_PUBLIC_URL }}/actuator/health/readiness` validando TLS; si falla, abre o comenta el *issue* `alerta-operativa` mencionando a ambos y envía `/fail` a `secrets.HC_URL_SONDA`; permisos mínimos (`issues: write`)
- [X] T063 [US5] Documentar en `docs/operacion/acceso-y-exposicion.md`: UFW (entrada 22/80/443), Docker sin publicar puertos fuera de `127.0.0.1`, SSH solo con claves y sin root, cuentas personales con `sudo` para responsable y suplente, `sarc-deploy` sin privilegios con comando forzado y la regla `sudoers` de T032 (scripts en `/usr/local/sbin/` root:root 0755, no modificables por esa cuenta), `sarc-backup` solo SFTP enjaulado con escritura solo en `received/`, alta/baja/rotación de claves, `fail2ban`, `unattended-upgrades`; registros en journald 30 días y `logrotate` 30 días para Nginx; la auditoría de SARC no se rota (FR-030–FR-032, FR-037)
- [X] T064 [US5] Documentar en `docs/operacion/avisos.md` la tabla de [health-and-alerts.md](contracts/health-and-alerts.md) (latidos `vps-latido` y `servicio` con periodo 5 min y gracia 5 min, para que el aviso salga como máximo 10 min después del último latido y llegue antes de 15 min), cómo configurar responsable y suplente en cada comprobación de Healthchecks.io y el correo *participating* en GitHub, `/etc/sarc/avisos.conf` (destinatarios y canales, fuera del repositorio), la prueba de recepción (*issue* `prueba-recepcion`, archivo `/var/lib/sarc/prueba-recepcion.ok` con la huella de `avisos.conf`, bloqueo con código 18 si alguien no confirma o cambian los destinatarios, FR-038a), la revisión mensual de que los flujos programados `sonda-externa` y `control-ensayos` siguen activos (GitHub los desactiva tras 60 días sin actividad en el repositorio) y la revisión semanal del panel de Auto Backup

**Checkpoint**: US5 completa en la VM de ensayo, salvo certificado y avisos reales (Fase 9).

---

## Phase 8: User Story 6 - Un operador sin contexto puede preparar, verificar y recuperar (Priority: P2)

**Goal**: guía de operación completa, comprensible sin esta conversación, que distingue ensayo de restauración real y lista lo que depende de recursos reales.

**Independent Test**: una persona ajena, solo con `docs/operacion/`, completa en la VM de ensayo los escenarios 5, 9 y 12 de [quickstart.md](quickstart.md) en una jornada (SC-012).

### Implementation for User Story 6

- [X] T065 [US6] Crear `docs/operacion/README.md` como índice: qué es cada entorno, a quién llamar, enlaces a todos los procedimientos y glosario (versión candidata, publicación, copia previa, recibo, ensayo, restauración real)
- [X] T066 [P] [US6] Crear `docs/operacion/recuperar.md`: síntomas por tipo de fallo (publicación 2 y 10–21, incluido un `verificar` fallido tras la primera instalación, que deja el mantenimiento activo, migración, servicio caído, VPS caído, almacenamiento no disponible, certificado), criterio corregir hacia adelante o recuperar (si la publicación registró `migrationsApplied[]` vacío: volver a `current` anterior; si aplicó migraciones: corrección o copia previa), y la regla de que con datos nuevos aceptados no se revierte ni restaura sin decisión registrada que declare la pérdida (FR-044); primera instalación fallida: procedimiento manual de [deploy-interface.md](contracts/deploy-interface.md#primera-instalación) para recrear `sarc_pgdata` (con nueva definición de `SARC_BOOTSTRAP_ADMIN_*` y registro, sin borrado, de un reporte huérfano en `sarc-prod`), solo si el responsable confirma por escrito que no hay datos reales y nunca tras una publicación `COMPLETADA`
- [X] T067 [P] [US6] Crear `docs/operacion/restaurar.md` con dos secciones nombradas por separado: «Ensayo de restauración (aislado, mensual y tras cambios críticos)» usando T051, con el criterio para cerrar el *issue* `ensayo-restauracion` con etiqueta `aprobado` solo si pasa el 100 % de las comprobaciones (si no, queda abierto con `fallido` y se repite tras corregir; un `pendiente` se cierra con el ensayo que lo resuelve), y «Restauración real en producción (excepcional, con autorización y registro)» con copia del estado actual, mantenimiento, restauración, comprobaciones posteriores y registro; identificación de los datos posteriores a la copia para volver a cargarlos (comparar la copia del estado actual con la restaurada: historiales de importación de consumo, inventario y uptime y reportes emitidos con fecha posterior a `createdAt` de la copia, con la ubicación de sus originales en Cloudinary o en la última exportación) (FR-040); incluye uso del Auto Backup de Contabo (sobrescribe el VPS) y de Backup de Cloudinary (restaurar eliminados) (FR-045, FR-046)
- [X] T068 [P] [US6] Crear `docs/operacion/mantener.md`: actualizaciones del sistema, Java, Docker/PostgreSQL menor y dependencias; verificación posterior; recuperación si fallan; cambio de IP o incorporación de dominio; disco lleno (qué se puede liberar sin tocar datos: copias del VPS ya recibidas por el computador local, registros rotados, versiones antiguas más allá de las 5 últimas, imágenes de Docker sin uso; qué nunca se borra: volumen `sarc_pgdata`, `/etc/sarc`, copias sin recibo); reemplazo del computador local y revocación del anterior, abriendo un *issue* `ensayo-restauracion` `pendiente` (cambio crítico); custodia offline de la clave privada `age` (FR-049)
- [X] T069 [US6] Crear `docs/operacion/pendientes-recursos-reales.md` con la tabla «Verificaciones pendientes de recursos reales» de [plan.md](plan.md#verificaciones-pendientes-de-recursos-reales-fr-048-sc-014), responsable y condición de cierre de cada una (FR-048, SC-014)
- [X] T070 [US6] Revisar toda `docs/operacion/` contra FR-047: cada procedimiento tiene requisitos previos, pasos numerados, resultado esperado, qué hacer si falla y responsable; sin valores reales ni referencias a esta conversación

**Checkpoint**: guía completa; ensayo con persona ajena registrado en `specs/007-despliegue-seguro-cicd/evidence/`.

---

## Phase 9: Polish, validación y producción real

**Purpose**: validación integral en sandbox (T071–T076) y, **solo con autorización explícita posterior**, la etapa E7 en el VPS real (T077–T086). Las tareas E7 no forman parte de la implementación que puede ejecutarse sin esa decisión.

### Validación en sandbox

- [ ] T071 Ejecutar en una VM Ubuntu desechable los escenarios 1–16 (incluidos 2b, 5b, 8b y 13b) de [quickstart.md](quickstart.md) y guardar la evidencia (sin secretos) en `specs/007-despliegue-seguro-cicd/evidence/sandbox.md`
- [X] T072 Ejecutar `./mvnw -B verify` en `sarc-bakend` y `npm run lint`, `npm run format:check`, `npm test -- --watch=false`, `npm run build` en `sarc-frontend`; confirmar 0 regresiones y `docs/api/openapi.json` sin cambios (SC-015)
- [X] T073 [P] Ejecutar el escaneo de secretos sobre el repositorio, un Release de ensayo y los registros de las ejecuciones de ensayo; registrar 0 hallazgos en `specs/007-despliegue-seguro-cicd/evidence/secretos.md` (SC-005)
- [X] T074 [P] Comparar los conteos de la base local `pg_sarc` antes y después de los ensayos con `deploy/scripts/sarc-local-db-snapshot.sh` y registrar el resultado en `specs/007-despliegue-seguro-cicd/evidence/base-local.md` (SC-006)
- [X] T075 [P] Añadir a `docs/operacion/version-candidata.md` cómo se deriva el estado de una versión sin mantenerlo a mano: `candidata` = Release pre-release sin publicación; `publicada` = tiene al menos una línea `COMPLETADA` en `deploy-history.jsonl`; `retirada` = hubo después otra publicación `COMPLETADA` (ver [data-model.md](data-model.md#versión-candidata))
- [X] T076 Actualizar la matriz de trazabilidad de [plan.md](plan.md#matriz-de-trazabilidad) con el estado real (verificado en sandbox / pendiente R) de cada requisito

### Producción real (E7) — bloqueada hasta autorización explícita

- [ ] T077 Registrar en `docs/operacion/decisiones-previas.md` la aprobación escrita del responsable del producto para iniciar E7 y las decisiones de E0 resueltas; sin ella no se ejecuta ninguna tarea siguiente
- [ ] T078 Configurar en GitHub (con autorización): protección de `main` con todas las comprobaciones de CI obligatorias; entorno `production` con revisores responsable y suplente, sin *bypass* de administradores, ramas/tags de versión, secretos del entorno `DEPLOY_SSH_KEY` y `DEPLOY_KNOWN_HOSTS`, y secretos **de repositorio** `HC_URL_PUBLICACION` y `HC_URL_SONDA` (los trabajos `avisar` y `sonda-externa` no declaran entorno, así que no ven los secretos del entorno; la credencial de la cuenta de verificación no se guarda en GitHub); variables de repositorio `SARC_RESPONSABLE`, `SARC_SUPLENTE` y `SARC_PUBLIC_URL` (`SARC_PRODUCCION_DESDE` se define en T084); *secret scanning* y *push protection*; protección de **Releases inmutables** del repositorio (comprobar que está disponible para la cuenta; si no lo está, regla de protección de tags `*.*.*-*` que impida borrarlos o moverlos y registrar la limitación en `docs/operacion/version-candidata.md`) (US1-4)
- [ ] T079 Contratar VPS y Auto Backup (USD 4/mes), endurecer acceso según `docs/operacion/acceso-y-exposicion.md`, instalar Nginx, Docker, Temurin 25, `age` y Certbot ≥ 5.4, y crear `/etc/sarc/sarc.env` desde `deploy/env/sarc.env.example`; instalar cada `deploy/scripts/<nombre>.sh` como `/usr/local/sbin/<nombre>` (sin extensión, root:root 0755) y `deploy/scripts/lib/common.sh` como `/usr/local/lib/sarc/common.sh` (root:root 0644) y `deploy/sudoers/sarc-deploy` en `/etc/sudoers.d/` tras validarlo con `visudo -c -f`
- [ ] T080 Emitir el certificado para la IP primero con `--staging` y después el reconocido; forzar una renovación; comprobar en navegadores actuales que la conexión es segura sin advertencias (SC-016)
- [ ] T081 Escanear puertos desde una red externa: solo 22/80/443; 5432 y 8080 rechazados; `/actuator/**` 404 salvo readiness (SC-007)
- [ ] T082 Crear las comprobaciones de Healthchecks.io con responsable y suplente, crear `/etc/sarc/avisos.conf`, ejecutar la prueba de recepción de cada tipo de aviso (GitHub y correo), registrar las confirmaciones de ambos en el *issue* `prueba-recepcion` y escribir `/var/lib/sarc/prueba-recepcion.ok` con la fecha y la huella de `avisos.conf` (FR-038a, SC-017)
- [ ] T083 Confirmar y activar Backup de Cloudinary para `sarc-prod` tras medir el efecto en la cuota de la cuenta compartida; crear la clave de API dedicada a la exportación (FR-041)
- [ ] T084 Primera publicación aprobada con `publicar` en `modo: primera-instalacion` (`sarc-deploy --primera-instalacion`), que queda `EN_CURSO` sin mantenimiento; primer arranque con admin inicial, cambio de clave obligatorio, retiro de `SARC_BOOTSTRAP_ADMIN_*` y reinicio; creación de la cuenta de verificación con el rol decidido en E0 y carga de su credencial en `/etc/sarc/verificacion.env` (0600); emisión, con las funciones normales de SARC, de un reporte de verificación sin datos reales sensibles cuyo identificador se guarda en `SARC_VERIFY_REPORT_ID`; nueva ejecución aprobada de `publicar` en `modo: verificar` hasta `COMPLETADA`; definir la variable de repositorio `SARC_PRODUCCION_DESDE` con esa fecha (base de `control-ensayos` hasta el primer ensayo aprobado); antes de la segunda publicación, activar `sarc-backup.timer` y la tarea del computador local para que exista un recibo de menos de 24 h (paso 4) (FR-015, FR-016, FR-028, FR-043)
- [ ] T085 Comprobar manualmente en un navegador actual el ingreso, un recorrido por cada módulo y la descarga de un archivo (sustituye la prueba de humo de navegador del TRD, fuera del alcance automatizado de esta feature); después detener el servicio y, en ventana acordada, apagar el VPS para comprobar avisos en < 15 min (SC-017)
- [ ] T086 Siete días seguidos de recibos de descarga del computador local real y primer ensayo de restauración con registro `ensayo-restauracion` y tiempo medido frente a 4 h (SC-009, SC-013, SC-018)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Fase 1)**: sin dependencias.
- **Foundational (Fase 2)**: depende de Fase 1; bloquea todas las historias.
- **US1 (Fase 3)**: depende de Fase 2. Es el MVP.
- **US3 (Fase 5)**: depende de Fase 2; independiente de US1 (puede ir en paralelo).
- **US4 (Fase 6)**: depende de Fase 2; independiente de US1/US3.
- **US5 (Fase 7)**: depende de Fase 2; T056–T057 tocan `application*.yaml` después de T038 (mismo archivo `application-prod.yaml`).
- **US2 (Fase 4)**: depende de US1 (Release y manifiesto), US3 (unidad systemd y Compose), US4 (T045, T046) y US5 (T058, T059). Se implementa después de ellas aunque su prioridad sea P1.
- **US6 (Fase 8)**: depende de US2–US5 (documenta sus procedimientos).
- **Fase 9**: T071–T076 dependen de todas las historias; T077–T086 dependen de T071–T076 **y** de autorización explícita.

### Orden recomendado

Fase 1 → Fase 2 → (US1 ∥ US3 ∥ US4 ∥ US5) → US2 → US6 → Fase 9.

### Within Each User Story

- Pruebas primero y en rojo; luego implementación.
- Scripts de biblioteca antes que scripts que los usan; flujos de GitHub después de los scripts que invocan.
- Documentación al cerrar la historia.

### Parallel Opportunities

- Fase 1: T002, T003, T004.
- Fase 2: T006, T007, T008, T009 tras T005.
- US1: T010, T011, T012 y T019 en paralelo; T014–T018 son el mismo archivo (`ci.yml`) y van en secuencia.
- US2: T023–T026 (pruebas) en paralelo; T031 y T032 en paralelo con los scripts.
- US3: T035, T036, T039, T040, T041 en paralelo.
- US4: T043, T044, T047–T050 y T052 en paralelo.
- US5: T053–T055, T059–T061 en paralelo.
- US6: T066–T068 en paralelo.
- Fase 9 (sandbox): T073, T074 y T075 en paralelo tras T071–T072.

---

## Parallel Example: User Story 4

```text
Task: "T043 Crear deploy/tests/sarc-backup.bats"
Task: "T044 Crear deploy/tests/backup-freshness.bats"
Task: "T048 Crear deploy/computador-local/Descargar-CopiaSarc.ps1"
Task: "T050 Crear deploy/computador-local/Exportar-Cloudinary.ps1"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Fase 1 y Fase 2.
2. US1: CI endurecido y versión candidata.
3. **Validar**: quickstart 1–3. Ya aporta valor sin servidor: ninguna versión defectuosa queda marcada como lista y cada versión es trazable.

### Incremental Delivery

1. MVP (US1).
2. US3 + US4 + US5 en paralelo (configuración, respaldos, exposición).
3. US2 (publicación controlada) cuando sus piezas existan; ensayo en VM.
4. US6 (guía) y ensayo con persona ajena.
5. Validación en sandbox (T071–T076).
6. E7 solo con autorización explícita (T077–T086).

---

## Notes

- [P] = archivos distintos y sin dependencias pendientes.
- Cada tarea cita su archivo; las de Fase 9 E7 actúan sobre recursos externos y requieren autorización.
- No hacer commits, merges ni pushes sin que el responsable lo pida.
