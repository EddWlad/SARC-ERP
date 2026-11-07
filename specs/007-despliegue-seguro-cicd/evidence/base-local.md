# Evidencia SC-006: la base local de desarrollo no se modificó

**Fecha**: 2026-11-07. **Base**: contenedor `sarc-bakend-postgres-1` (`pg_sarc`, PostgreSQL 18, Compose local de desarrollo).

## Qué se comprobó

Todos los ensayos de esta feature usaron **contenedores desechables y datos sintéticos** (Testcontainers `postgres:18` para las pruebas del backend, contenedores `postgres:18` y `nginx` creados y destruidos por cada prueba). Ninguno se conectó a la base local.

| Evidencia | Valor |
|---|---|
| Última ejecución del contenedor local antes de las pruebas (`docker inspect`) | Iniciado `2026-11-07T00:50:15Z`, detenido `2026-11-07T00:54:24Z`: **estaba detenido durante toda la implementación y los ensayos** |
| Conexiones de los ensayos a `localhost:5432` | Ninguna: los ensayos usan puertos aleatorios o contenedores propios; el único contenedor con puerto 5432 es esta base, que estaba apagada |
| Instantánea de solo lectura tomada después de los ensayos | Ver tabla; se obtuvo con `deploy/scripts/sarc-local-db-snapshot.sh` ejecutado dentro del propio contenedor (arrancado solo para esa lectura y detenido después) |

**Limitación declarada**: no se tomó una instantánea *antes* de los ensayos; la garantía de que no hubo cambios es que el contenedor no estaba en ejecución y que ningún ensayo apunta a esa base (el script de instantánea se niega a usar un host que no sea local y las pruebas usan sus propios contenedores). Esta instantánea queda como línea base para futuros ensayos.

## Conteos por tabla (solo cifras; sin datos de filas)

| Tabla | Filas |
|---|---|
| `asset_connector` | 263 |
| `asset_connector_type` | 8 |
| `asset_equipment` | 234 |
| `asset_external_identity` | 103 |
| `asset_import_batch` | 14 |
| `asset_import_row` | 2479 |
| `asset_schedule_exception` | 1 |
| `asset_schedule_interval` | 7 |
| `audit_event` | 475 |
| `auth_role` | 5 |
| `auth_user` | 3 |
| `auth_user_role` | 3 |
| `energy_import_batch` | 14 |
| `energy_import_row` | 25077 |
| `energy_session` | 11682 |
| `energy_session_review` | 0 |
| `energy_session_revision` | 7 |
| `flyway_schema_history` | 16 |
| `report_issued` | 19 |
| `spring_session` | 1 |
| `spring_session_attributes` | 1 |
| `upt_connector_alias` | 0 |
| `upt_import_batch` | 0 |
| `upt_import_row` | 0 |
| `upt_measurement` | 1 |
| `upt_measurement_exclusion` | 0 |
| `upt_measurement_revision` | 1 |
| `upt_measurement_temporal_change` | 0 |
| `upt_temporal_transition_work` | 0 |
