# Data Model: registros operativos de entrega y recuperación

**Feature**: `007-despliegue-seguro-cicd` | **Plan**: [plan.md](plan.md)

Esta feature **no** añade tablas ni migraciones a la base de SARC. Las entidades de la spec son registros operativos que viven en GitHub, en archivos del VPS o en el computador local. Se describen aquí su ubicación, sus campos y sus estados para que las tareas y las pruebas usen los mismos nombres.

## Versión candidata

- **Ubicación**: GitHub Release *pre-release* + tag; `manifest.json` como asset ([contrato](contracts/release-manifest.md)).
- **Campos**: `version` (`AAAA.MM.DD-<run>-<sha7>`, único e inmutable), `commit`, `ciRunId`, `createdAt` (UTC), `checks[]` (nombre, resultado, URL), `artifacts[]` (nombre, tamaño, `sha256`), `migrations.latest`, `migrations.files[]` (nombre, suma), `schemaChanges` (booleano informativo respecto del Release candidato anterior; las decisiones en producción usan `migrationsApplied[]` del registro de publicación).
- **Reglas**: solo se crea si todas las comprobaciones obligatorias de CI pasaron (FR-003); nunca se modifica; sin secretos (FR-008).
- **Estados** (derivados, no se guardan aparte): `candidata` = Release pre-release sin publicación `COMPLETADA`; `publicada` = al menos una línea `COMPLETADA` en `deploy-history.jsonl`; `retirada` = después hubo otra publicación `COMPLETADA` (sigue consultable).

## Resultado de verificación

- **Ubicación**: comprobaciones de la ejecución de CI; copiadas en `manifest.checks[]`.
- **Campos**: `name`, `conclusion` (`success`/`failure`/`cancelled`), `kind` (`codigo`/`infraestructura`, FR-004), `url`.

## Autorización de publicación

- **Ubicación**: historial de despliegues del entorno `production` de GitHub.
- **Campos**: aprobador, fecha y hora, ejecución del flujo `publicar`, versión (entrada del flujo).
- **Reglas**: una por ejecución, no reutilizable (FR-011); aprobadores: responsable y suplente (FR-012).

## Registro de publicación

- **Ubicación**: `/var/lib/sarc/deploy-history.jsonl` en el VPS (una línea por intento) + resumen de la ejecución en GitHub.
- **Campos**: `version`, `previousVersion`, `runId`, `approvedBy`, `startedAt`, `finishedAt`, `preChecks[]`, `preDeployBackup` (archivo), `migrationsApplied[]`, `postChecks[]`, `result`, `failedStep`, `recovery`.
- **Estados**:

```text
SOLICITADA ─(aprobación)─► EN_CURSO ─► COMPLETADA
     │                        │
     └─(rechazo/tiempo)─► CANCELADA   └─► FALLIDA ─► RECUPERADA (corrección o restauración, con registro)
```

- **Reglas**: `COMPLETADA` solo si todas las comprobaciones posteriores obligatorias pasan (FR-016); `FALLIDA` indica `failedStep` y estado del esquema (FR-019); `migrationsApplied[]` no vacío marca la publicación como cambio crítico (ensayo de restauración pendiente) y obliga a restaurar para volver atrás, salvo la primera instalación, que no cuenta como cambio crítico porque parte de una base vacía; en la primera instalación, `EN_CURSO` queda registrado sin mantenimiento hasta el modo `verificar`, y mientras tanto no se admite otra publicación.

## Entorno

- **Valores**: `desarrollo`, `pruebas`, `produccion` ([tabla en plan §2](plan.md#2-arquitectura-de-producción)).
- **Regla**: cada uno con base, carpeta de archivos y credenciales propias; ninguno apunta a otro por omisión (FR-023).

## Valor de configuración / secreto

- **Ubicación del inventario**: guía de operación (sin valores). **Valores**: `/etc/sarc/sarc.env` (VPS) o secretos del entorno `production` (GitHub).
- **Campos del inventario**: `nombre`, `propósito`, `obligatorio_en_prod`, `dónde_vive`, `responsable`, `rotación`, `cómo_verificar_revocación`.
- **Inventario inicial**: `SPRING_PROFILES_ACTIVE`, `SARC_DB_URL`, `SARC_DB_USER`, `SARC_DB_PASSWORD`, `POSTGRES_PASSWORD`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `SARC_STORAGE_CLOUDINARY_ROOT_FOLDER`, `SARC_DEPLOY_ENV`, `SARC_VERIFY_REPORT_ID`, `SARC_BOOTSTRAP_ADMIN_*` (solo primer arranque), `SARC_VERIFY_USER` y `SARC_VERIFY_PASSWORD` (en `/etc/sarc/verificacion.env`, aparte de `sarc.env`, legibles solo por la cuenta que ejecuta `sarc-deploy` y nunca por el servicio), además del archivo `/etc/sarc/avisos.conf` (destinatarios y canales); en GitHub: clave SSH de despliegue, `known_hosts`, URLs de latido, y las variables `SARC_RESPONSABLE`, `SARC_SUPLENTE`, `SARC_PUBLIC_URL`, `SARC_PRODUCCION_DESDE`; en el computador local: clave privada `age`, clave SSH de `sarc-backup`, clave de API de exportación de Cloudinary.

## Copia de base de datos

- **Ubicación**: `/var/backups/sarc/` (7 días) y computador local (30 diarias + 12 mensuales) ([contrato](contracts/backup-artifacts.md)).
- **Campos**: `fileName`, `kind` (`nocturna`/`previa-publicacion`/`manual`), `createdAt`, `sha256`, `encryptedFor` (huella de la clave pública), `pgVersion`, `latestMigration`, `tableCounts{}`.
- **Estados**: `generada` → `descargada` (recibo del computador local) → `verificada` (suma + `pg_restore --list`) → `ensayada` (usada en una restauración aislada) → `expirada`.
- **Regla de frescura**: la copia verificada más reciente en el computador local debe tener < 24 h para publicar (FR-043).

## Copia de archivos externos

- **Ubicación**: Backup de Cloudinary (versiones dentro de la cuenta) + exportación en el computador local.
- **Campos de la exportación**: `exportedAt`, `folder`, `assets[]` (`public_id`, `resource_type`, `bytes`, `version`, `sha256` local).
- **Regla**: toda referencia guardada en la base de producción debe existir en Cloudinary (actual o restaurable) o en la exportación (FR-041).

## Prueba de restauración

- **Ubicación**: *issue* `ensayo-restauracion` + evidencia en el computador local.
- **Campos**: fecha, copia usada, motivo (`mensual`/`cambio-critico`), entorno aislado, comprobaciones (esquema, conteos, historiales, auditoría, referencias), tiempo medido, punto de datos recuperado, resultado.
- **Regla**: nunca más de 31 días sin un ensayo aprobado (SC-018). Lo vigila el flujo `control-ensayos` (aviso a 28 y 31 días); antes del primer ensayo aprobado cuenta desde la variable de repositorio `SARC_PRODUCCION_DESDE`. Un cambio crítico (publicación con `migrationsApplied[]` no vacío, cambio de computador local o de proveedor) abre un *issue* `ensayo-restauracion` con etiqueta `pendiente`; al aprobarse se cierra con etiqueta `aprobado` (solo con el 100 % de las comprobaciones); un ensayo con fallos queda abierto con `fallido`.

## Aviso

- **Ubicación**: Healthchecks.io (comprobaciones) y *issues* `alerta-operativa` ([contrato](contracts/health-and-alerts.md)).
- **Campos**: `check`, `canal`, `periodo`, `gracia`, `destinatarios` (responsable, suplente), `últimaPruebaRecepción`.
- **Prueba de recepción**: `/var/lib/sarc/prueba-recepcion.ok` con `fecha`, `huellaAvisos` (SHA-256 de `/etc/sarc/avisos.conf`) y confirmación de cada persona; vigente solo si la huella coincide con el archivo actual (FR-038a).
