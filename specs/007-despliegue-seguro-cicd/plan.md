# Implementation Plan: Preparación de SARC para despliegue seguro y CI/CD

**Branch**: ninguna creada (trabajo sin commits sobre `main` en `e85c910`, a petición del responsable) | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)
**Input**: especificación aclarada (5 decisiones, sesión 2026-11-06). **Estado**: diseño técnico; nada implementado, desplegado ni configurado. Siguientes pasos: `/speckit-tasks` y `/speckit-analyze`.

## Summary

Llevar SARC del repositorio a un VPS de Contabo de forma verificable, autorizada y recuperable, sin tocar reglas de negocio. El enfoque reutiliza el CI existente y añade: (1) controles que faltan (migraciones inmutables, dependencias, secretos); (2) una **versión candidata** inmutable publicada como GitHub Release; (3) un flujo **publicar** manual con aprobación en el entorno `production` que ejecuta por SSH un script con comprobaciones previas, copia previa, migración controlada y comprobaciones posteriores; (4) un VPS sencillo según el TRD (Nginx con certificado Let's Encrypt para IP, Spring Boot con systemd, PostgreSQL 18 en Docker solo local); (5) copias lógicas diarias cifradas que **descarga un computador local autorizado**, más Auto Backup de Contabo y Backup de Cloudinary; (6) avisos por GitHub (*issues* que mencionan a responsable y suplente) y por correo mediante Healthchecks.io, incluida la detección externa de una caída total del VPS; (7) una guía de operación ejecutable por alguien sin contexto.

Los cambios de código de la aplicación son tres y pequeños (configuración obligatoria de Cloudinary en `prod`, *readiness* con base de datos y registro con `traceId`). No hay cambios de esquema.

## Technical Context

**Language/Version**: Java 25 (Temurin) + Spring Boot 4.1.1; TypeScript/Angular 22; Node 24 en CI; Bash para scripts del servidor; PowerShell para la tarea del computador local (si es Windows).
**Primary Dependencies**: existentes (actuator, Flyway, session-jdbc, cloudinary-http5). Nuevas **solo en infraestructura**: Nginx (Ubuntu), Docker Engine + Compose, Certbot ≥ 5.4 (snap), `age` (cifrado de copias), cliente PostgreSQL 18 en el contenedor; en CI: `actions/dependency-review-action`, escáner de secretos y `npm audit`. Ninguna dependencia nueva en la aplicación.
**Storage**: PostgreSQL 18 en contenedor con volumen persistente; Cloudinary (entrega `authenticated`) para archivos; copias en `/var/backups/sarc` (VPS) y en el computador local.
**Testing**: suites existentes (JUnit, MockMvc, Testcontainers PG18, ArchUnit, `OpenApiContractTest`; Vitest, ESLint, Prettier, build). Nuevas: prueba de inmutabilidad de migraciones, prueba de *upgrade* desde la versión anterior, pruebas de los scripts (`bats` o shellcheck + ensayo en VM desechable), ensayo de restauración.
**Target Platform**: VPS Contabo Ubuntu LTS, 6 vCPU, 12 GB RAM, 200 GB SSD (referencia, sin garantía), IP pública fija; computador local autorizado de la organización.
**Project Type**: aplicación web (monolito modular Spring Boot + SPA Angular) más su infraestructura de entrega.
**Performance Goals**: sin cambios; los objetivos del TRD (30 usuarios, p95 2 s) se miden en el VPS real, no se afirman.
**Constraints**: pérdida máxima de datos 24 h (copia local); recuperación objetivo 4 h (TRD, por medir); certificados de 160 h; publicación solo con aprobación; nada sensible en repositorio público, registros ni avisos; proporcionalidad.
**Scale/Scope**: 1 servidor, 3 entornos (desarrollo local, pruebas efímeras, producción), 2 personas autorizadas, ~20 comprobaciones de aviso.

No quedan `NEEDS CLARIFICATION` técnicos: se resolvieron en [research.md](research.md). Lo que depende de cuentas o del VPS se lista en «Verificaciones pendientes de recursos reales».

## Constitution Check

*GATE: pasa antes de la investigación y se revisa tras el diseño.*

Leídas: Constitución 1.2.0, spec 007 con sus aclaraciones, TRD §17–18 como referencia técnica, ADR 0001.

| Puerta | Pre-investigación | Post-diseño | Control |
|---|---|---|---|
| I. Especificación antes de cambios | PASS | PASS | Spec 007 aclarada; este plan no implementa. Los tres cambios de código están autorizados por FR-025, FR-035 y FR-036 de la spec. |
| II. Reglas de negocio explícitas | PASS | PASS | No se crean ni cambian reglas de negocio (FR-050). El único comportamiento nuevo visible (no arrancar en `prod` sin almacenamiento) es operativo y está en FR-025. |
| III. Migraciones inmutables | PASS | PASS | Sin migraciones nuevas. Se añade una prueba de CI que rechaza modificar `V001`–`V016` o cualquier migración ya en `main`. |
| D01–D14 | PASS | PASS | Ninguna decisión ratificada se toca. La regresión completa (SC-015) es criterio de salida de cada etapa. |
| Jerarquía documental | PASS | PASS | Las adaptaciones del TRD (VPS 6/12, Auto Backup como complemento, copia local, 24 h, sin archivado continuo, salud pública mínima) se registran en un ADR nuevo (`sarc-bakend/docs/adr/0012-despliegue-y-recuperacion.md`), como exige `CLAUDE.md` del backend. |

Complejidad: sin violaciones; no se rellena la tabla de seguimiento.

## 1. Diagnóstico del estado actual

Estado: **V** = verificado en el repositorio; **P** = propuesto por este plan; **R** = solo verificable con VPS, cuentas o servicios reales.

| Ámbito | Existe y se reutiliza (V) | Requiere adaptación (P) | Nuevo (P) | Solo con recursos reales (R) |
|---|---|---|---|---|
| CI | `ci.yml`: verify backend, contrato OpenAPI, tipos, lint, formato, pruebas y build frontend | Añadir trabajos de dependencias, secretos e inmutabilidad de migraciones; marcar todas las comprobaciones como obligatorias en la protección de `main` | — | — |
| Versión | `spring-boot-maven-plugin` (JAR), `ng build` | Activar `build-info` con la versión | Flujo `version-candidata`, manifiesto, `SHA256SUMS`, Release pre-release | — |
| Publicación | — | — | Entorno `production`, flujo `publicar`, script `sarc-deploy`, página de mantenimiento, historial | Aprobación real por responsable/suplente; SSH al VPS |
| Configuración | Perfil `prod` con BD por variables, Swagger desactivado, `.env` ignorado | Cloudinary obligatorio en `prod`; carpeta raíz por entorno (`sarc-prod`, `sarc-test`) | `/etc/sarc/sarc.env` (plantilla sin valores), inventario de variables | Valores reales y su custodia |
| Base de datos | PG18 en Compose local, Flyway V001–V016, `ddl-auto: validate` | Compose de producción (volumen persistente, `127.0.0.1`, sin contraseña en el archivo) | Comprobaciones previas de esquema, prueba de *upgrade* | Primera migración en producción |
| Exposición | Cookie Secure por esquema, cabeceras reenviadas, actuator restringido salvo salud | Salud pública reducida a *readiness* | Nginx (TLS, estáticos, proxy, bloqueo `/actuator`, límite de ingresos), cortafuegos | Puertos alcanzables desde Internet |
| Certificado | — | — | Certbot ≥ 5.4 con perfil `shortlived` para IP, `deploy-hook`, comprobación de vigencia | Emisión y renovación reales (staging y producción) |
| Registros | `RequestTraceFilter` (`traceId` en MDC), errores sin detalle, `logServerErrorDetail: false` | Patrón de registro con `traceId`; journald con retención 30 días | — | Volumen real de registros |
| Respaldos | — | — | `pg_dump` nocturno y previo, cifrado `age`, descarga local, recibos, retención, exportación de Cloudinary | Auto Backup contratado; Backup de Cloudinary activado; computador local designado |
| Avisos | Notificaciones nativas de Actions (solo al que dispara) | — | *Issues* de alerta, comprobaciones Healthchecks.io, sonda HTTPS programada | Recepción real por ambos responsables |
| Documentación | TRD, ADR 0001 | — | Guía de operación (`docs/operacion/`), ADR 0012 | Ensayo con persona ajena (SC-012) |

Clasificación de requisitos: **funcionales** FR-050 (no cambiar comportamiento) y el aviso de almacenamiento no disponible, que ya existe (503); **operativos** FR-010–016, FR-029, FR-032, FR-037–049; **técnicos** FR-001–009, FR-017–028, FR-030–036.

## 2. Arquitectura de producción

```text
Internet ──► :443 Nginx (TLS Let's Encrypt para IP, HSTS)
         │      ├─ /                → /opt/sarc/current/frontend (Angular, fallback index.html)
         │      ├─ /api/            → 127.0.0.1:8080 (Spring Boot)
         │      ├─ /actuator/health/readiness → 127.0.0.1:8080 (solo estado)
         │      └─ /actuator/**     → 404
         └─► :80  Nginx: /.well-known/acme-challenge/ (Certbot webroot); todo lo demás → 301 https
SSH :22 (solo claves; sin root; cuenta admin y cuenta de despliegue con comando forzado; cuenta SFTP de copias enjaulada)

VPS
 ├─ systemd: sarc.service  → java -jar /opt/sarc/current/backend/sarc-backend.jar (usuario sarc, perfil prod, EnvironmentFile=/etc/sarc/sarc.env)
 ├─ docker compose: postgres:18 → 127.0.0.1:5432, volumen sarc_pgdata (fuera de /opt/sarc/releases)
 ├─ timers: copia nocturna, latidos, disco, vigencia de certificado, renovación Certbot
 └─ /var/backups/sarc  (copias cifradas, 7 días)

Fuera del VPS: Auto Backup de Contabo · Cloudinary (archivos + Backup) · Computador local (copias 30d+12m, exportación Cloudinary) · Healthchecks.io · GitHub
```

- **Ejecución**: la aplicación es un único JAR (monolito modular sin cambios); Nginx sirve el frontend como archivos estáticos. PostgreSQL en contenedor con la misma imagen `postgres:18` usada en desarrollo y pruebas.
- **Datos persistentes**: el volumen de PostgreSQL y `/etc/sarc` no forman parte de ninguna versión; `/opt/sarc/releases/<versión>/` contiene solo artefactos; `current` es un enlace que la publicación cambia de forma atómica. Se conservan las 5 últimas versiones.
- **HTTPS y base restringida**: cortafuegos (UFW) con entrada solo a 22, 80 y 443; PostgreSQL publicado únicamente en `127.0.0.1` y Docker configurado para no saltarse el cortafuegos con puertos públicos. HSTS sin `includeSubDomains` (no hay dominio).
- **Separación de entornos**:

| Entorno | Dónde | Base | Archivos | Configuración |
|---|---|---|---|---|
| Desarrollo | Equipo del desarrollador | Compose local `pg_sarc` | Carpeta `sarc` o sin credenciales | `.env` ignorado, perfil `dev` |
| Pruebas | CI (Testcontainers) y VM/contenedores desechables para ensayos | Desechable, datos sintéticos | Sin credenciales o carpeta `sarc-test` con clave propia | Variables del ensayo, nunca las de `prod` |
| Producción | VPS | Volumen `sarc_pgdata` | Carpeta (o entorno de producto) `sarc-prod` con clave propia | `/etc/sarc/sarc.env`, perfil `prod` |

- **Cambios mínimos de aplicación** (justificados por la spec):
  1. `prod`: validar al arrancar que `CLOUDINARY_*` existe y fallar sin mostrar valores (FR-025, US3-3). En `dev`/pruebas se mantiene opcional.
  2. Grupo *readiness* con la comprobación de base de datos y exposición pública limitada a esa ruta (FR-035).
  3. Patrón de registro que incluye `traceId` y nivel, sin cambiar qué se registra (FR-036).

## 3. CI/CD con GitHub Actions

Tres flujos separados; ninguno publica sin aprobación.

| Flujo | Disparo | Hace | No hace |
|---|---|---|---|
| **CI** (`ci.yml`, ampliado) | PR y `push` a `main` | Lo actual + `migraciones-inmutables` (falla si cambia o se borra un `V*.sql` existente en la base de comparación), `dependencias` (`dependency-review-action` en PR; **OSV-Scanner** sobre `pom.xml` y `package-lock.json` en PR y `main`), `secretos` (**gitleaks** con `--redact` sobre el árbol y los commits nuevos), `scripts-despliegue` (shellcheck, bats). Crítico/alto (CVSS ≥ 7.0) bloquea; medio advierte en el resumen. Cada resumen clasifica el fallo como del cambio o de infraestructura con la regla de T017. `avisar-fallo`: si falla en `main`, *issue* `alerta-operativa`; si falla en un PR del propio repositorio, comentario en el PR; ambos mencionan a responsable y suplente. | Generar o publicar nada |
| **Versión candidata** (nuevo) | `workflow_run` de CI en `main` con conclusión `success` | Construye una vez JAR y `dist`, genera `manifest.json` y `SHA256SUMS`, escanea los artefactos en busca de secretos, crea tag y Release **pre-release** `AAAA.MM.DD-<run>-<sha7>` | Tocar el VPS |
| **Publicar** (nuevo) | `workflow_dispatch` con `version` | Trabajo `preparar` (sin secretos): valida que el Release existe, que su CI fue exitoso y que las sumas coinciden. Trabajo `desplegar` con `environment: production` (aprobación obligatoria): SSH con clave de despliegue al comando forzado `sarc-deploy <version>`; registra el resultado. Trabajo `avisar` (`if: failure()`): *issue* `alerta-operativa` mencionando a ambos y señal a Healthchecks.io. | Ejecutarse sin aprobación; reutilizar una aprobación |
| **Sonda externa** (nuevo) | `schedule` cada 15 min | `curl` con validación TLS a `https://<IP>/actuator/health/readiness`; si falla, *issue* + `/fail` | — |
| **Control de ensayos** (nuevo) | `schedule` diario | Revisa la fecha del último *issue* `ensayo-restauracion` aprobado y los `pendiente`; a 28 y 31 días, *issue* `alerta-operativa` a ambos (SC-018) | — |

- **Identidad y trazabilidad**: el manifiesto ([contracts/release-manifest.md](contracts/release-manifest.md)) enlaza versión, commit, ejecución de CI, resultado de cada verificación, migraciones incluidas y sumas. En el VPS cada publicación añade una línea a `/var/lib/sarc/deploy-history.jsonl`; en GitHub queda el historial de despliegues del entorno con el aprobador.
- **Secretos**: en GitHub solo como secretos del entorno `production` (clave SSH de despliegue, `known_hosts` del VPS, URLs de latido). La credencial de la cuenta de verificación vive en el VPS, en `/etc/sarc/verificacion.env`, legible solo por la cuenta que ejecuta `sarc-deploy`; no viaja por SSH ni la carga el servicio. Nunca en variables de repositorio, en el manifiesto ni en registros (los registros de un repositorio público son públicos).
- **Concurrencia**: `concurrency: { group: production, cancel-in-progress: false }` en GitHub y un bloqueo (`flock`) en el script del VPS; una segunda solicitud espera o falla con explicación (edge case de la spec).
- **Protección de `main`**: exigir CI verde y revisión antes de integrar; administradores incluidos.
- **Inmutabilidad de versiones**: protección de Releases inmutables del repositorio o, si no está disponible para la cuenta, regla de protección de tags de versión (US1-4).
- **Primera instalación**: `publicar` con `modo: primera-instalacion` omite el recibo de copia local (paso 4) y la copia previa (paso 6) porque la base está vacía; queda `EN_CURSO`, sin mantenimiento y con solo disponibilidad, base de datos y versión comprobadas, hasta crear la cuenta y el reporte de verificación, y `publicar` con `modo: verificar` la cierra como `COMPLETADA` o `FALLIDA` ([deploy-interface.md](contracts/deploy-interface.md#primera-instalación)).
- **Secuencia y fallos**: ver [contracts/deploy-interface.md](contracts/deploy-interface.md). Resumen: `bloqueo → comprobaciones previas (incluida la prueba de recepción vigente, código 18) → copia previa → mantenimiento → detener → enlazar versión → arrancar (Flyway) → esperar readiness ≤ 3 min → comprobaciones posteriores → fin del mantenimiento → registrar`. Cualquier fallo devuelve un código distinto de cero, deja el mantenimiento activo si la base pudo cambiar y nunca se informa como completado.

## 4. Migraciones y consistencia

- **Antes de aplicar**: el script compara `flyway_schema_history` con las migraciones del manifiesto: todas las aplicadas deben existir con la misma suma; las pendientes deben ser estrictamente posteriores; ninguna fila con `success = false`. Cualquier diferencia detiene la publicación sin tocar nada (FR-013, FR-019).
- **Copia previa obligatoria**: `pg_dump` verificado inmediatamente antes de detener la versión anterior; su nombre se registra en la publicación.
- **Aplicación**: Flyway al arrancar la nueva versión (comportamiento actual, validación de sumas activa). PostgreSQL revierte la migración que falla; las anteriores del mismo lote quedan aplicadas.
- **Si falla**: la aplicación no arranca; el script marca `FALLIDA`, mantiene la página de mantenimiento, informa la última migración aplicada con éxito y la que falló, y avisa. **No** reactiva la versión anterior si alguna migración se aplicó (la anterior podría no entender el esquema). El operador elige con la guía: corregir hacia adelante con una migración nueva o restaurar la copia previa (sin datos nuevos que perder, porque el servicio estaba en mantenimiento).
- **Compatibilidad**: si la publicación que se quiere deshacer registró `migrationsApplied[]` vacío en `deploy-history.jsonl`, volver a la versión anterior es solo cambiar `current`. Si aplicó migraciones, volver atrás exige restaurar y se considera restauración real (autorizada). `schemaChanges` del manifiesto solo informa frente al candidato anterior y no decide.
- **CI**: prueba de inmutabilidad y prueba de *upgrade* (Testcontainers: esquema de la versión anterior + datos sintéticos → migrar a la nueva → `ddl-auto: validate`).
- **Esta feature no necesita migraciones**. Si una feature futura las necesita, entra como `V017+`.

## 5. Salud, seguridad y observabilidad

- **Salud**: público solo `readiness` con `UP`/`DOWN` (incluye base de datos). Restringidos a `localhost`: `liveness`, `info` (versión), `metrics` y demás. Justificación en [research.md R6](research.md#r6-salud-y-exposición) y contrato en [contracts/health-and-alerts.md](contracts/health-and-alerts.md).
- **Acceso administrativo**: SSH solo con claves; `PermitRootLogin no`, `PasswordAuthentication no`; una cuenta personal con `sudo` por persona autorizada (responsable y suplente); cuenta `sarc-deploy` sin shell ni privilegios, con comando forzado que solo puede ejecutar `sudo -n /usr/local/sbin/sarc-deploy …` gracias a una única regla `sudoers` (`sarc-deploy ALL=(root) NOPASSWD: /usr/local/sbin/sarc-deploy`); el script y sus bibliotecas pertenecen a root y la cuenta no puede modificarlos, y `/etc/sarc/verificacion.env` es 0600 root; cuenta `sarc-backup` solo SFTP enjaulada. Alta, baja y rotación de claves documentadas; `fail2ban` o límite de conexiones; actualizaciones de seguridad automáticas (`unattended-upgrades`) y reinicios planificados.
- **Exposición mínima**: 22, 80, 443. Nginx con límite de peticiones al ingreso (ADR 0001), cabeceras de seguridad y tamaño de cuerpo acorde a los 21 MB de la aplicación.
- **Registros**: aplicación a journald con retención de 30 días y tope de tamaño; Nginx con `logrotate` 30 días; sin cuerpos de petición, sin cabeceras `Cookie`/`Authorization`, sin parámetros sensibles. La auditoría funcional (tabla de auditoría) no se rota.
- **Capacidad**: aviso de disco por encima del 80 %; tope de copias locales en el VPS; registro de memoria y CPU para comparar con el TRD.
- **Certificado**: renovación automática, comprobación diaria de vigencia (< 72 h → aviso) y de cadena contra el almacén del sistema, bloqueo de publicación si es inválido o vence en < 72 h, sonda externa con validación TLS. La VM de ensayo no puede obtener un certificado de Let's Encrypt: usa una CA de prueba propia solo con `SARC_DEPLOY_ENV=ensayo`; en producción ese modo se rechaza.
- **Avisos y plazos**: latidos cada 5 min con 5 min de gracia (aviso a ≤ 10 min del último latido, dentro de SC-017); la prueba de recepción vigente es una comprobación previa de la publicación, no solo un procedimiento.
- **Secretos**: `/etc/sarc/sarc.env` 0640 root:sarc; claves de Cloudinary distintas por entorno; clave privada de las copias solo en el computador local; procedimiento de rotación y de secreto expuesto (revocar, rotar, confirmar que el anterior falla, revisar registros).

## 6. Respaldos y recuperación

Diseño detallado y formatos en [contracts/backup-artifacts.md](contracts/backup-artifacts.md); decisiones en [research.md R8–R9](research.md#r8-respaldos-cuatro-mecanismos-distintos).

1. **Auto Backup de Contabo**: contratar y comprobar en el panel que existen puntos diarios. Uso: reconstruir el servidor completo ante desastre. No se usa para recuperar la base (consistencia no documentada) ni cubre Cloudinary ni el computador local. Restaurar sobrescribe el VPS: es una restauración real que requiere autorización.
2. **Copia lógica**: `pg_dump -Fc` nocturno (02:00 America/Guayaquil) y previo a cada publicación, ejecutado dentro del contenedor; cifrado con la clave pública `age` del computador local; `SHA256SUMS`; metadatos con conteos por tabla y última migración. Retención en el VPS: 7 días. Fallo → `/fail` a Healthchecks.io.
3. **Descarga al computador local**: tarea programada diaria (y al iniciar sesión si se perdió la anterior) con la cuenta `sarc-backup` por SFTP; verifica suma, descifra en un temporal, ejecuta `pg_restore --list`, guarda la copia cifrada, deja un recibo en el VPS y envía latido. Retención local 30 diarias + 12 mensuales. Requisitos del computador: designado por el responsable, disco cifrado, usuario restringido, espacio suficiente.
4. **Cloudinary**: activar Backup (tras confirmar alcance y cuota en la cuenta compartida); carpeta o entorno propio `sarc-prod`; exportación incremental mensual y antes de cambios críticos al computador local, con una clave de API dedicada.

**Escenarios de fallo**

| Situación | Detección | Aviso | Acción operativa |
|---|---|---|---|
| Computador local apagado | Falta el latido `descarga-local` (24 h + 2 h de gracia) | Correo a ambos | Encender y forzar descarga; si no es posible en el día, designar otro computador. Mientras tanto la pérdida posible supera 24 h y se declara. |
| Descarga falla (red, suma, descifrado) | Script local envía `/fail` | Correo | Reintentar; si la copia del VPS está dañada, generar una nueva bajo demanda y descargarla. |
| Copia disponible > 24 h | El recibo más reciente tiene más de 24 h | Correo + bloqueo | La comprobación previa de `sarc-deploy` falla con «sin copia local reciente verificable» (FR-043). |
| Copia nocturna falla | `/fail` o falta el latido `copia-nocturna` | Correo | Revisar espacio y contenedor; ejecutar copia manual. |
| Auto Backup no disponible | Revisión semanal del panel (no hay API de aviso documentada) | Lista de mantenimiento | Abrir caso con Contabo; la copia local sigue siendo la fuente independiente. |

**Recuperación ante fallos de publicación**: sin cambios de esquema, volver a la versión anterior (`current`). Con cambios de esquema, corregir hacia adelante o restaurar la copia previa. Si ya se aceptaron datos nuevos después de publicar, **ninguna** reversión o restauración se ejecuta sin una decisión explícita registrada que declare qué datos se perderían (desde la hora de la copia hasta el momento). Preferencia: corregir hacia adelante.

**Ensayo de restauración**: mensual y tras cambios críticos (publicación con migraciones, cambio de computador local o de proveedor), en el computador local o en una máquina desechable, sin credenciales de Cloudinary; resultado como *issue* `ensayo-restauracion` con tiempo medido.

**Restauración real**: solo con autorización y registro; distinta del ensayo y con su propio procedimiento en la guía.

## 7. Pruebas y criterios de salida

| Prueba | Tipo | Dónde | Resultado esperado |
|---|---|---|---|
| CI verde | Automática | GitHub | Se genera versión candidata con manifiesto y sumas |
| CI con fallo (prueba, lint, contrato, migración modificada, dependencia alta, secreto de prueba) | Automática (PR de ensayo) | GitHub | Comprobación roja nombrada; no hay versión (SC-002) |
| Aprobación | Ensayo | GitHub + VM de ensayo | Sin aprobación no corre `desplegar`; con aprobación queda aprobador y versión (SC-004) |
| Concurrencia | Ensayo | VM de ensayo | La segunda publicación espera o se rechaza |
| Configuración ausente/inválida | Automática | CI (prueba de arranque `prod` sin Cloudinary/BD) | No arranca; mensaje con el nombre de la variable, sin valores (SC-005) |
| Migración fallida y recuperación | Ensayo | VM de ensayo con migración de prueba que falla (nunca en `main`) | Publicación `FALLIDA`, mantenimiento activo, estado informado, recuperación con la copia previa (SC-008) |
| Upgrade desde la versión anterior | Automática | CI (Testcontainers) | Migra y valida esquema |
| Certificado para IP (staging y real), renovación forzada, vencimiento simulado | Ensayo | **VPS real** | Emisión, renovación y aviso; publicación bloqueada sin certificado válido (SC-016) |
| Copia, cifrado, descarga, antigüedad, alerta | Ensayo | VM de ensayo + computador local; latidos en una comprobación de prueba | Recibo, latido, aviso al apagar el computador y bloqueo por antigüedad |
| Restauración aislada e integridad | Ensayo | Computador local / máquina desechable | 100 % de comprobaciones (SC-009, SC-013) |
| Archivos de Cloudinary | Ensayo limitado | Carpeta `sarc-test` con clave de ensayo | Exportación y restauración de un eliminado; referencias de muestra resuelven (SC-010). La activación de Backup en `sarc-prod` es **R**. |
| Caída del servicio y del VPS | Ensayo | **VPS real** (detener servicio; apagar VPS en ventana acordada) | Correo y *issue* a ambos en < 15 min (SC-017) |
| Recepción de avisos | Ensayo | **Cuentas reales** | Ambos confirman cada tipo; registro (FR-038a) |
| Exposición | Ensayo | VM de ensayo y **VPS real** | Desde fuera solo 22/80/443; 5432 y 8080 rechazados; `/actuator/**` 404 salvo readiness (SC-007) |
| Regresión funcional | Automática + humo | CI y comprobaciones posteriores | Suites 001–006 sin cambios; ingreso y lectura de usuarios, inventario, consumo, uptime y reportes (SC-011, SC-015) |
| Base local intacta | Ensayo | Equipo del desarrollador | Conteos de la base `pg_sarc` iguales antes y después (SC-006) |

| Sin operaciones destructivas | Automática | CI (`no-destructive.bats`) | Ningún script ni flujo contiene `DROP`, `TRUNCATE`, `pg_restore --clean`, `docker volume rm`, etc. fuera de la lista permitida (FR-021, FR-022) |
| Aislamiento del ensayo | Automática dentro del ensayo | Computador local | El ensayo aborta si la base de destino no es su propio contenedor o hay variables de Cloudinary o de la base de producción |

**Prueba de humo en navegador (TRD)**: fuera del alcance automatizado de esta feature. La cubren las comprobaciones posteriores a nivel de API (ingreso, módulos, archivos) y una comprobación manual en navegador en la primera publicación real (T085). Automatizarla queda como posible feature posterior.

**Sandbox frente a recursos reales**: todo lo de CI y los ensayos en una VM Ubuntu desechable (Hyper-V/WSL2/Multipass) con datos sintéticos se puede probar sin VPS. Requieren recursos reales: certificado (IP pública), puertos desde Internet, Auto Backup, Backup de Cloudinary en producción, recepción real de correos, detección de caída del VPS y rendimiento.

## 8. Plan de ejecución y riesgos

| Etapa | Depende de | Resultado verificable | Criterio de salida |
|---|---|---|---|
| **E0. Decisiones previas** | — | Registro de: repositorio público o privado (R11), computador local designado, cuenta Healthchecks.io, alcance de Backup en la cuenta de Cloudinary, quién es suplente, rol de la cuenta de verificación posterior (menor privilegio, nunca ADMINISTRADOR) | Responsable del producto aprueba por escrito |
| **E1. Endurecer CI** | — | Trabajos de inmutabilidad, dependencias y secretos; protección de `main` | PR de ensayo con cada tipo de fallo queda rojo; PR limpio verde; suites sin cambios |
| **E2. Cambios mínimos de aplicación** | E1 | Cloudinary obligatorio en `prod`, readiness con BD, patrón de registro | Pruebas nuevas en verde; `OpenApiContractTest` sin cambios; ADR 0012 |
| **E3. Versión candidata** | E1 | Flujo, manifiesto, sumas, Release pre-release | Dos versiones seguidas con sumas reproducibles y trazables en < 2 min (SC-003) |
| **E4. Infraestructura como archivos** | E2 | `deploy/` con plantillas de Nginx, systemd, Compose de producción, scripts `sarc-deploy`, copias, latidos; tarea del computador local | `shellcheck` limpio; sin valores reales (escaneo de secretos) |
| **E5. Ensayo completo en VM** | E3, E4 | Publicación, fallo de migración, concurrencia, copia, descarga, restauración, exposición | Escenarios de §7 marcados «sandbox» en verde con evidencia en `evidence/` |
| **E6. Guía de operación** | E5 | `docs/operacion/` (publicar, verificar, fallos, recuperar, rotar, mantener, restaurar, inventario de variables, pendientes R) | Persona ajena completa el ensayo en una jornada (SC-012) |
| **E7. VPS real** (requiere decisión explícita futura) | E0, E6, VPS contratado | Endurecimiento, certificado staging→real, Auto Backup, prueba de avisos, primera publicación y primer ensayo de restauración | Criterios R de §7 en verde |

**Riesgos**

| Riesgo | Tipo | Mitigación | Estado |
|---|---|---|---|
| Repositorio público expone registros, *issues*, Releases y `documents/` | Seguridad | Decisión E0; nada sensible en registros ni avisos; revisar `documents/` | **Bloqueo previo a producción** |
| Certificado de 6 días no se renueva | Disponibilidad | Renovación 2×/día, aviso a 72 h, sonda TLS, bloqueo de publicación | R |
| Let's Encrypt cambia condiciones de certificados para IP | Disponibilidad | Seguir anuncios; alternativa: dominio (FR-033a) | Vigilancia |
| Computador local apagado varios días | Pérdida de datos > 24 h | Latido, aviso, bloqueo de publicación, computador suplente | Depende de operación |
| Clave privada de copias perdida | Pérdida de datos | Copia de la clave en un medio offline custodiado por el responsable | E0/E6 |
| Restaurar base antigua deja referencias a imágenes borradas | Integridad | Backup de Cloudinary antes de producción + exportación local | R |
| Backup de Cloudinary aumenta cuota de la cuenta compartida | Costo | Medir y decidir en E0 | R |
| Migración parcial en producción | Integridad | Copia previa, mantenimiento, sin reactivar versión anterior | Ensayado en E5 |
| Exposición de 5432 por Docker saltándose UFW | Seguridad | Publicar solo en `127.0.0.1`; prueba de puertos desde fuera | E5 + R |
| Sonda programada de GitHub retrasada o desactivada a los 60 días | Detección | Latidos de Healthchecks.io como fuente principal; revisión mensual | Declarado |
| Healthchecks.io gratuito sin SLA o con uso comercial no admitido | Detección | Confirmar al crear cuenta; alternativa de pago | E0 |
| Regresión al tocar configuración | Funcional | Cambios mínimos, suites completas, humo posterior | E2 |

**Costos**: VPS Contabo (según contratación); Auto Backup **USD 4/mes** (aclaración); Healthchecks.io Hobbyist USD 0 (por confirmar); GitHub USD 0 si el repositorio sigue público (GitHub Pro si pasa a privado, por confirmar); Cloudinary sin costo nuevo salvo que Backup supere la cuota (por medir); Let's Encrypt USD 0.

**Diferencias aprobadas respecto al TRD** (a registrar en ADR 0012): VPS 6 vCPU/12 GB como referencia; Auto Backup de Contabo como complemento en lugar de retirarlo; copia independiente en computador local en lugar de almacenamiento externo privado; pérdida máxima 24 h en lugar de 1 h, y por eso copia lógica diaria en lugar de archivado continuo; salud pública reducida a *readiness*; comprobación posterior sin Swagger (desactivado en `prod`).

**Procedimiento posterior (no se ejecuta ahora)**: E7 con autorización explícita: contratar VPS y Auto Backup → endurecer acceso → instalar paquetes → certificado staging y real → Compose y servicio → copia de configuración → pruebas de exposición y avisos → admin inicial y retiro de sus variables → primera publicación aprobada → primer ensayo de restauración. Pasos en [quickstart.md](quickstart.md).

### Verificaciones pendientes de recursos reales (FR-048, SC-014)

| Verificación | Depende de | Responsable | Condición de cierre |
|---|---|---|---|
| Emisión y renovación del certificado para la IP | VPS con IP pública, puerto 80 accesible | Responsable técnico | Certificado reconocido, renovación forzada exitosa |
| Puertos expuestos desde Internet | VPS | Responsable técnico | Escaneo externo: solo 22/80/443 |
| Alcance real del Auto Backup (puntos, restauración) | Contratación | Responsable del producto | Panel muestra puntos diarios |
| Backup de Cloudinary: alcance y cuota en cuenta compartida | Acceso autorizado a Cloudinary | Responsable del producto | Activado y medido |
| Recepción de cada aviso por ambos | Cuentas GitHub y correo | Responsable y suplente | Registro de prueba de recepción |
| Detección de caída total del VPS | VPS + Healthchecks.io | Responsable técnico | Correo recibido en < 15 min |
| Rendimiento con 30 usuarios | VPS | Responsable técnico | Medición registrada |
| Descarga desde el computador local real | Computador designado | Responsable del producto | Siete días seguidos con recibo |

## Matriz de trazabilidad

La última columna refleja el estado real tras la implementación de T001–T076: «Verificado en sandbox» = comprobado con pruebas o ensayos locales; «Implementado» = existe pero su ejecución real depende de GitHub; **(R)** = solo verificable con el VPS, cuentas o servicios reales.

| Requisito | Cambio | Prueba / evidencia | Etapa | Estado (2026-11-07) |
|---|---|---|---|---|
| FR-001, FR-003, SC-001 | CI ampliado, comprobaciones obligatorias en `main` | PR verde y rojo | E1 | Implementado (`ci.yml`); PR verde y rojo pendientes de ejecutar en GitHub |
| FR-002, US1-5 | Reutiliza diff de tipos y `OpenApiContractTest` | PR con contrato desincronizado | E1 | Verificado en sandbox: tipos y `openapi.json` sin cambios; el CI real lo repite |
| FR-004 | Reintento por trabajo; resumen distingue fallo de infraestructura | Reejecución de un trabajo | E1 | Implementado y probado con bats (`classify-failure`); reejecución en GitHub pendiente |
| FR-005, SC-002 | Trabajos de dependencias y secretos con umbrales | PR con dependencia alta y secreto ficticio | E1 | Verificado en sandbox: `osv-gate` y gitleaks probados (0 hallazgos locales); PR de ensayo pendiente (GitHub) |
| FR-006–FR-008, SC-003, US1-1..4 | Flujo versión candidata, manifiesto, sumas, escaneo de artefactos | Dos versiones; comparar sumas | E3 | Verificado en sandbox: manifiesto y sumas con bats; Release real pendiente (GitHub) |
| FR-009 | Historial del entorno + `deploy-history.jsonl` | Consulta tras ensayo | E3/E5 | Verificado en sandbox: `deploy-history.jsonl` con bats; consulta real pendiente (R) |
| FR-010–FR-012, SC-004, US2-1..2 | Entorno `production` con revisores, `workflow_dispatch` | Ensayo sin y con aprobación | E3/E5 | Implementado (`publicar.yml`); aprobación real pendiente (R) |
| FR-013, US2-3 | Comprobaciones previas de `sarc-deploy` | Ensayo con copia vieja, versión no aprobada, esquema distinto | E5 | Verificado en sandbox (bats de comprobaciones previas) |
| FR-014 | `concurrency` + `flock` | Dos publicaciones simultáneas | E5 | Verificado en sandbox el bloqueo `flock` (código 10); `concurrency` de GitHub pendiente |
| FR-015, FR-016, SC-011, US2-6 | Comprobaciones posteriores con cuenta y reporte de verificación; modo de primera instalación | Ensayo, veredicto < 10 min | E5/E7 | Verificado en sandbox con dobles de la aplicación; contra SARC real pendiente (R) |
| US1-4 | Releases inmutables o protección de tags | Intento de mover un tag de versión | E7 | Pendiente R: Releases inmutables o protección de tags en GitHub (T078) |
| FR-017–FR-019, SC-008, US2-4..5 | Secuencia de migración controlada | Migración fallida de ensayo | E5 | Verificado en sandbox con dobles (códigos 20 y 21, mantenimiento, sin reactivar); migración fallida real en VM pendiente |
| FR-018 | Prueba de inmutabilidad | PR que modifica `V001` | E1 | Verificado en sandbox (`check-migrations.bats`, 8/8) |
| FR-020 | `schemaChanges` informativo en el manifiesto; `sarc-deploy` compara con la última migración aplicada y registra `migrationsApplied[]`; prueba de *upgrade* | CI | E1/E3 | Verificado en sandbox: `MigrationUpgradeTest`, `sarc-schema-check` y `flywayChecksum` contrastado con Flyway real |
| FR-021, FR-022 | Sin operaciones destructivas en scripts; copia previa | `no-destructive.bats` en CI + ensayo | E4/E5 | Verificado en sandbox (`no-destructive.bats`); copia previa probada con dobles |
| FR-023, FR-026, FR-027, SC-006, US3-4..5 | Tabla de entornos; carpetas `sarc-prod`/`sarc-test`; claves por entorno | Conteos de base local antes/después | E2/E5 | Verificado en sandbox: tabla de entornos y evidencia de base local (`evidence/base-local.md`); carpetas `sarc-test` pendientes (R) |
| FR-024, FR-029, SC-005, US3-1..2 | `/etc/sarc/sarc.env`, inventario, rotación, escaneo | Escaneo de repositorio, artefactos y registros | E1/E6 | Verificado en sandbox: gitleaks 0 hallazgos (`evidence/secretos.md`); rotación documentada; registros de Actions pendientes |
| FR-025, US3-3 | Validación de Cloudinary en `prod` | Prueba de arranque | E2 | Verificado en sandbox (`StorageConfigTest`, `ProductionProfileConfigTest`) |
| FR-028, US3-6 | Procedimiento del admin inicial | Ensayo en VM | E5/E6 | Documentado (`entornos-y-secretos.md`); ensayo en VM pendiente |
| FR-030, FR-031, SC-007, US5-1..2 | Nginx, UFW, PostgreSQL en `127.0.0.1` | Escaneo de puertos | E5/E7 | Plantilla de Nginx verificada en contenedor (`nginx-config.bats`); escaneo externo de puertos pendiente (R) |
| FR-032, US5-3 | Cuentas SSH y procedimiento de alta/baja | Revisión + ensayo | E4/E6 | Documentado (`acceso-y-exposicion.md`) y regla `sudoers` validada con `visudo`; ensayo en VM pendiente |
| FR-033, FR-033a, SC-016 | Certbot `shortlived` para IP, comprobación de vigencia, bloqueo | Staging y real | E7 | Scripts verificados con CA de prueba (`checks.bats`); emisión y renovación reales pendientes (R) |
| FR-034 | Swagger desactivado (existente) + 404 en `/actuator/**` | Petición externa | E5 | Verificado en sandbox (Nginx 404 y Swagger apagado en `prod`) |
| FR-035, US5-4 | Readiness con BD, solo estado | Petición externa | E2/E5 | Verificado en sandbox (`HealthReadinessTest`); petición externa pendiente (R) |
| FR-036, US5-5 | Patrón con `traceId` | Revisión de registros de ensayo | E2/E5 | Implementado (patrón con `traceId`); revisión de registros de ensayo pendiente |
| FR-037, US5-6 | journald y logrotate 30 días | Configuración revisada | E4 | Documentado (`acceso-y-exposicion.md`); configuración en VM pendiente |
| FR-038, FR-038a, SC-017 | `avisar-fallo` en CI (PR y `main`), *issues* de alerta en publicación y sonda, Healthchecks.io (5 min / 5 min), comprobación previa de prueba de recepción (código 18) | Prueba de recepción; ensayo con `prueba-recepcion.ok` ausente | E1/E5/E7 | Implementado (`ci.yml`, `publicar.yml`, `sonda-externa.yml`, `sarc-alerts-gate`, bats 18); recepción real pendiente (R) |
| FR-039, FR-039a | Auto Backup + copia local cifrada por *pull* | Siete días de recibos | E5/E7 | Implementado (scripts de copia y descarga; PowerShell con sintaxis verificada, sin ejecutar); siete días de recibos pendientes (R) |
| FR-040 | Copia diaria, objetivo 24 h, metadatos de conteos | Ensayo de restauración | E5 | Implementado (metadatos con conteos y ensayo); ensayo real pendiente |
| FR-041, SC-010 | Backup de Cloudinary + exportación local | Ensayo con `sarc-test` | E5/E7 | Implementado (`Exportar-Cloudinary.ps1`, sin ejecutar); Backup de Cloudinary y `sarc-test` pendientes (R) |
| FR-042, SC-009, SC-013, SC-018, US4-3 | Ensayo mensual y tras cada cambio crítico, con registro; flujo `control-ensayos` y *issue* `pendiente` al publicar con `migrationsApplied[]` no vacío | *Issue* `ensayo-restauracion`; ejecución de `control-ensayos` con un ensayo de 31 días | E5+ | Implementado (`control-ensayos.yml`, `publicar.yml`); ejecución real pendiente |
| FR-043, US4-4 | Comprobación de antigüedad en `sarc-deploy` | Ensayo con recibo viejo | E5 | Verificado en sandbox (`backup-freshness.bats`, código 14) |
| FR-044, US4-5 | Regla de reversión/corrección en guía | Revisión de guía | E6 | Documentado (`recuperar.md`) |
| FR-045, FR-046, US4-6 | Procedimientos separados; ensayo sin credenciales de producción | Revisión + ensayo | E5/E6 | Documentado (`restaurar.md`) y aislamiento en `Ensayar-Restauracion.ps1`; ensayo pendiente |
| FR-047, FR-049, SC-012, US6 | Guía de operación | Ensayo con persona ajena | E6 | Guía completa; ensayo con persona ajena pendiente |
| FR-048, SC-014 | Tabla de verificaciones pendientes | Revisión | E6 | Verificado: `docs/operacion/pendientes-recursos-reales.md` |
| FR-050, SC-015 | Cambios mínimos; suites completas | CI | Todas | Verificado en sandbox: `./mvnw verify` (595 pruebas), lint, formato, 225 pruebas y build del frontend sin cambios (`evidence/regresion.md`) |

## Project Structure

### Documentation (this feature)

```text
specs/007-despliegue-seguro-cicd/
├── spec.md
├── plan.md               # este archivo
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── release-manifest.md
│   ├── deploy-interface.md
│   ├── health-and-alerts.md
│   └── backup-artifacts.md
├── checklists/requirements.md
└── tasks.md              # /speckit-tasks (no se crea aquí)
```

### Source Code (repository root)

```text
.github/workflows/
├── ci.yml                       # se amplía (E1)
├── version-candidata.yml        # nuevo (E3)
├── publicar.yml                 # nuevo (E3)
└── sonda-externa.yml            # nuevo (E5)
sarc-bakend/
├── src/main/resources/application-prod.yaml   # readiness, registro (E2)
├── src/main/java/.../shared/storage/          # validación de Cloudinary en prod (E2)
├── src/test/java/...                          # pruebas de arranque prod, inmutabilidad, upgrade (E1/E2)
└── docs/adr/0012-despliegue-y-recuperacion.md # nuevo (E2)
deploy/                          # nuevo (E4): plantillas y scripts, sin valores reales
├── nginx/  systemd/  compose/  scripts/  computador-local/
docs/operacion/                  # nuevo (E6): guía de operación
```

**Structure Decision**: se conserva el monolito (`sarc-bakend`, `sarc-frontend`) sin cambios estructurales. La infraestructura vive en `deploy/` en la raíz y la guía en `docs/operacion/`, separadas del código de la aplicación para que su revisión no se mezcle con cambios funcionales.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
