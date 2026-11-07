# Contrato: artefactos de copia y recuperación

## Copia de PostgreSQL (VPS)

| Elemento | Valor |
|---|---|
| Archivo | `/var/backups/sarc/sarc-<AAAAMMDDThhmmZ>-<kind>.dump.age` (`kind`: `nocturna`, `previa`, `manual`) |
| Contenido | `pg_dump -Fc` de la base `sarc`, ejecutado dentro del contenedor PG18 |
| Cifrado | `age` con la clave **pública** del computador local; el VPS no puede descifrar |
| Integridad | `<archivo>.sha256` |
| Metadatos | `<archivo>.meta.json`: `createdAt`, `kind`, `pgVersion`, `latestMigration`, `tableCounts{}`, huella de la clave pública. Sin datos de filas. |
| Permisos | `sarc-backup` solo lectura; resto sin acceso |
| Retención | 7 días en el VPS |
| Aviso | `copia-nocturna` (latido o `/fail`) |

## Descarga al computador local

| Elemento | Valor |
|---|---|
| Iniciativa | Computador local (*pull*), diario y al iniciar sesión si se perdió el anterior |
| Acceso | SFTP con `sarc-backup` (clave propia, enjaulado, sin shell) |
| Verificación | Suma, descifrado en temporal, `pg_restore --list`; borrar el temporal |
| Recibo | Archivo `received/<archivo>.ok` en el VPS con fecha y suma (único permiso de escritura de `sarc-backup`) |
| Retención | 30 diarias + 12 mensuales (primera copia de cada mes) |
| Aviso | `descarga-local` |
| Computador | Designado y autorizado; disco cifrado; usuario restringido; clave privada `age` con respaldo offline custodiado |

## Exportación de Cloudinary

| Elemento | Valor |
|---|---|
| Alcance | Carpeta o entorno de producto `sarc-prod`: `image` (equipos) y `raw` (originales de importación y reportes emitidos) |
| Frecuencia | Mensual y antes de cambios críticos; incremental por `public_id` + `version` |
| Acceso | Clave de API dedicada, solo en el computador local |
| Salida | Archivos + índice `export-<fecha>.json` con `public_id`, `resource_type`, `version`, `bytes`, `sha256` |
| Complemento | Backup de Cloudinary activado (restaurar eliminados y versiones) |

## Ensayo de restauración

1. Elegir la copia verificada más reciente (o la indicada por el cambio crítico).
2. **Comprobación de aislamiento** (aborta el ensayo si falla y queda en el informe): la base de destino es un contenedor creado por el propio ensayo en `localhost`; no existe ninguna variable `CLOUDINARY_*` ni `SARC_DB_*` en el entorno; el puerto elegido estaba libre.
3. PostgreSQL 18 desechable en el computador local o en una máquina aislada; nunca con credenciales de producción.
4. `pg_restore` y comprobaciones: `latestMigration`, `tableCounts` iguales a los metadatos, auditoría presente, historiales de importación, muestra de referencias a archivos presentes en la exportación (o restaurables en Cloudinary).
5. Opcional: arrancar el JAR de la versión publicada contra la base restaurada **sin** credenciales de Cloudinary y comprobar ingreso.
6. Registrar tiempo total y punto de datos en el *issue* `ensayo-restauracion`; destruir el entorno.

## Restauración real (excepcional)

Solo con autorización explícita registrada, declarando qué datos posteriores a la copia se perderán. Pasos en la guía: mantenimiento, copia del estado actual, restauración, lista de datos posteriores a la copia (importaciones y reportes con fecha posterior a `createdAt`, con la ubicación de sus originales) para volver a cargarlos, comprobaciones posteriores de publicación, retirada del mantenimiento, registro.
