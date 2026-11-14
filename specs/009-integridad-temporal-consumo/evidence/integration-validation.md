# Validación integrada (T040–T043)

Fecha: 2026-11-14 · Rama `feat/009-integridad-temporal-consumo` (sin commit).

## T040 · Verificaciones de `.github/workflows/ci.yml`, ejecutadas localmente

| Paso del CI | Resultado |
|---|---|
| Backend `./mvnw test`: unidad, ArchUnit, integración con Testcontainers, `OpenApiContractTest`, `MigrationUpgradeTest` | 902/902 correctas |
| Tipos generados al día con el contrato (`npm run api:types` sin diferencias) | Correcto |
| `npm run lint` | Correcto |
| `npm run format:check` | Correcto |
| `npm test` | 90 archivos y 506 pruebas correctas |
| `npm run build` | Correcto; inicial de 347,75 kB (presupuesto 500 kB) |
| `check-migrations-immutable.sh` | Correcto: V001–V019 sin cambios; V020 es nueva |
| Secretos (gitleaks) | No se ejecutó: gitleaks no está instalado en esta máquina. Lo cubre el CI al publicar. |

Cobertura funcional: la conservan RF-001 (identidad y permisos), RF-002 (Inventario, más `trackBy` en la lista) y RF-003 (Consumo), además de la suite de Feature 008, dentro de la suite completa.

### Ensayo de V020 sobre la copia de producción (2026-11-14)

**Copia y entorno.** Se usó la copia nocturna `sarc-20261114T0700Z-nocturna.dump.age` (V019; 16 130 sesiones `VALIDATED` con 310 603,48 kWh, 37 614 filas de importación, 34 lotes, 0 revisiones). La copia se descifró con la identidad age protegida por DPAPI; la identidad y el volcado solo pasaron por memoria. Se restauró con `pg_restore --no-owner --no-acl --exit-on-error` en un contenedor `postgres:18` desechable, sin red ni puertos publicados (`sarc-v020-ensayo`), distinto de la base de Compose. La restauración tardó 0,9 s. Desplazamientos de origen antes de V020: 15 430 sesiones con −300 y 700 con 0, coherente con V019.

**Aplicación.** `V020__consumo_acciones_y_fin_de_origen.sql` se aplicó en una sola transacción, como hace Flyway en PostgreSQL:

| Medida | Resultado |
|---|---|
| Duración total (19 sentencias y `COMMIT`) | ≈ 32 ms. La sentencia más lenta fue `ADD CONSTRAINT ck_energy_session_deletion` (≈ 6 ms, recorre las 16 130 sesiones). `ix_energy_session_deleted` tardó 4 ms. |
| Bloqueos | `AccessExclusiveLock` sobre `energy_session`, `energy_import_row`, `energy_import_batch` y `energy_session_revision` mientras dura la transacción. `sarc-deploy.sh` detiene `sarc` antes de migrar, así que nadie espera esos bloqueos. |
| Datos existentes | Huellas MD5 iguales antes y después de V020 para: sesiones (id, fecha de conteo, instantes, energía, estado, desplazamiento, equipo, versión), clasificaciones de filas y totales diarios oficiales. No cambió ninguna fecha, instante ni estado. |
| Valores nuevos | 0 bajas, 0 fines con desplazamiento propio (nulo = el del inicio), `deleted_rows = 0` en los 34 lotes. |
| Restricciones e índice | Ninguna restricción quedó sin validar; `ix_energy_session_deleted` válido. |
| Esquema resultante | Columnas, restricciones e índices iguales a los de una base nueva migrada de V001 a V020 en el mismo servidor. Las definiciones de las seis restricciones de V020 coinciden exactamente. La única diferencia de texto es preexistente: la forma en que `pg_restore` reescribe el índice parcial `ix_upt_temporal_work_active` de V018, que es equivalente. |

El historial de Flyway del ensayo no registra V020, porque se aplicó con `psql`. El camino con Flyway de V019 a V020, con datos, lo cubre `MigrationUpgradeTest`, que forma parte de la suite completa.

**Planes del predicado de bajas.** Se midieron con `EXPLAIN (ANALYZE, BUFFERS)` sin bajas y con 161 bajas (1 %, en una transacción revertida), comparando cada consulta con y sin `deleted_at IS NULL`:

| Consulta (como la generan `SessionPredicates` y `findPendingLink`) | Plan | Sin bajas | Con 1 % de bajas |
|---|---|---|---|
| Resumen oficial de octubre, con predicado | Seq Scan + Aggregate (octubre son 72 % de las filas) | 4,2 ms | 4,0 ms |
| Mismo resumen sin predicado (como antes de V020) | Igual | 4,7 ms | 3,1 ms |
| Tendencia diaria de octubre | Seq Scan + HashAggregate | 4,3 ms | 4,7 ms |
| Lista sin filtro, primera página (`started_at DESC, id`) | Index Scan `ix_energy_session_started` + Limit | 0,06 ms | 0,03 ms |
| Misma lista sin predicado | Igual | 0,04 ms | 0,04 ms |
| Conteo de la lista | Seq Scan + Aggregate | 3,0 ms | 3,0 ms |
| Lista de bajas (`deleted=true`) | Index Scan `ix_energy_session_deleted` + top-N | 0,02 ms | 0,08 ms |
| Pendientes de vincular | Index Scan `ix_energy_session_pending` | 0,03 ms | 0,01 ms |

El predicado no cambia ningún plan: el filtro se agrega al mismo recorrido, con diferencias por debajo de la variación entre ejecuciones. La lista de bajas usa el índice parcial nuevo y no recorre la tabla. Con el volumen actual, el resumen mensual hace un recorrido secuencial; esto ya pasaba antes de V020.

**Limpieza.** El contenedor `sarc-v020-ensayo` se eliminó al terminar. No quedó ningún volcado descifrado en disco.

## T041 · Versión candidata y recuperación (2026-11-14)

Todo se ensayó en local, sobre el árbol de trabajo sin commit. La versión candidata real la crea el flujo `version-candidata.yml` cuando el cambio entra en `main` con el CI verde. Aquí se repitieron sus pasos.

### Preparación de la versión candidata

| Paso de `version-candidata.yml` | Resultado local |
|---|---|
| Construir una sola vez: `mvnw -DskipTests -Dsarc.version=… package` y `npm run build`, empaquetando `browser/` en `.tar.gz` | Correcto. Versión de ensayo `2026.11.14-0-2dd7dac` (ejecución 0 = local). |
| Manifiesto del candidato anterior | Se descargó el `manifest.json` público de `2026.11.14-41-793e92f`, que termina en V019. |
| `deploy/release/build-manifest.sh` (en WSL, que tiene `jq`) | Correcto: `migrations.latest = V020`, `schemaChanges = true`. |
| `sha256sum -c SHA256SUMS` | `OK` para el JAR, el `.tar.gz` y `manifest.json`. |
| Escaneo de secretos de los artefactos | No había gitleaks local. Una búsqueda de claves age, URL de Cloudinary con credenciales y claves privadas en el contenido del JAR y del `.tar.gz` dio 0 coincidencias. El escaneo con gitleaks lo hace el flujo real. |

### Restauración aislada (`Ensayar-Restauracion.ps1`)

Con la copia `sarc-20261114T0700Z-nocturna.dump.age` el resultado fue `APROBADO (7/7)`, en 0,2 min frente a un objetivo de 4 h. Pasaron: aislamiento, `pg_restore`, V019 igual a los metadatos, conteos de todas las tablas, auditoría (483 eventos) e historiales de importación (36 lotes). La muestra de referencias a archivos quedó «pendiente de ensayar con la exportación», porque no hay índice de exportación de Cloudinary en este computador. El script destruyó el entorno.

### Publicación ensayada y recuperación

Script del ensayo: contenedor `postgres:18` desechable en `127.0.0.1`, con contraseña aleatoria y sin variables `CLOUDINARY_*`, `SARC_DB_*` ni `SARC_BOOTSTRAP_*`. La copia nocturna se restauró en dos bases: `sarc`, la que se publica, y `previa`, la copia a la que se recuperaría.

1. **Respaldo previo a publicar.** La copia nocturna hace el papel de `preDeployBackup`. En la publicación real, `sarc-deploy.sh` crea su propia copia previa con `sarc-backup` antes de migrar, como en el hotfix (`sarc-20261114T0056Z-previa`).
2. **Arranque del JAR candidato contra la copia.** Flyway aplicó `V020` («Successfully applied 1 migration … v020», 67 ms) y la aplicación llegó a `readiness UP` en 15,3 s, sin errores en el registro. El único aviso esperado fue «Cloudinary no configurado». Después de publicar, los conteos de sesiones (16 130), filas (37 614), reportes (3) y auditoría (483) son iguales a los metadatos de la copia.
3. **Escrituras posteriores a la copia.** Se simularon una edición, con su revisión `EDIT`, y una baja, con la misma forma que deja `SessionService`.
4. **Conciliación.** Se aplicó el paso 4 de `restaurar.md` frente a `createdAt` de la copia. Salieron 2 sesiones modificadas, 1 revisión de edición o baja, 1 baja, 0 lotes de importación y 0 reportes posteriores. Esa es la lista que habría que volver a cargar si se restaurara.
5. **Copia de recuperación.** `previa` queda en V019, con 16 130 sesiones y sin columnas de V020. Con migraciones aplicadas, `recuperar.md` descarta volver a la versión anterior sin restaurar. La vía preferente es corregir hacia adelante; restaurar `previa` exige una decisión registrada y perdería las escrituras listadas en el paso 4.

**Respaldo previo a aplicar el histórico.** Feature 009 no trae ninguna aplicación histórica pendiente. La corrección de fechas ya se publicó con V019 en el hotfix, con su copia previa, y V020 no cambia datos existentes (T040). Si más adelante se aprueba una corrección histórica, debe llevar su propia copia, separada de la previa a publicar.

**Limpieza.** Se destruyeron el contenedor y su volumen, el registro de la aplicación y los puertos 55434 y 18080. No quedó ningún volcado descifrado en disco.

**Issue #13.** Cerrado el 2026-11-14 con este informe (`APROBADO (7/7)`) y la etiqueta `aprobado`, con autorización del responsable.

## T043 · Trazabilidad (2026-11-14)

Estados: **Cumple** = verificado con prueba automatizada, ensayo o recorrido registrado. **Cumple con nota** = verificado, con una aclaración del alcance. **Retirado** = fuera de alcance por una aclaración registrada.

### Requisitos funcionales

| Requisito | Verificación | Evidencia | Estado |
|---|---|---|---|
| FR-001 Diagnóstico de la interpretación de fechas | Causa confirmada en `EnergySession.apply`: se usaba la fecha de Ecuador para toda fuente. El parser leía bien el encabezado. Se reprodujo con 1211/1212 y se midió en la copia de producción. | `hotfix.md` (Causa confirmada, H006) | Cumple |
| FR-002 Fecha y hora declaradas por Enerlink, segundos, desplazamiento de cada extremo, sin doble conversión | `SessionParsersTest` 15/15, `EnergySessionTest` 16/16, `SourceOffsetMigrationTest` 3/3, `MigrationUpgradeTest` (V020). Excel de 1211: `11/11/2026 00:30:00 (UTC+00:00)` → `10/11/2026 21:10:05 (UTC-05:00)`. | `us1-validation.md`; V019 publicada (`hotfix.md`, H007) | Cumple |
| FR-003 Wallbox conserva su política | V019 asigna −300 a todo Wallbox; 0 sesiones Wallbox cambiaron de día en el ensayo. W-77 cuenta el 10/11 con su hora de Ecuador. | `hotfix.md` (H006), `us1-validation.md` | Cumple |
| FR-004 Instante, hora de origen, zona y fecha de atribución separados; desplazamiento ausente, inválido o distinto por extremo | Columnas `source_utc_offset_minutes` (V019) y `source_end_utc_offset_minutes` (V020). Un encabezado ilegible, fuera de rango o contradictorio da `SOURCE_OFFSET_INVALID`. Un fin sin declaración usa el desplazamiento del inicio. El orden se valida con los instantes (422 `INVALID_SESSION`). | `us1-validation.md`, `us3-session-actions.md` (paso 5) | Cumple |
| FR-005 Atribución coherente, `[from, to)`, corte propio de cada salida, D13 | `ConsumptionApiTest` (límites de día y mes), `ConsumptionCalculatorTest` (total, tendencia y ranking), `dashboard-page.spec.ts` (hora de cálculo), PDF con corte propio, `period-selection.spec.ts` (D13). | `us1-validation.md` | Cumple |
| FR-006 Consolidado por fecha de inicio de cada fuente; filtro sin mover fechas; rótulo | `ConsumptionApiTest#eachSessionCountsOnTheStartDateDeclaredByItsSource…`, renderizadores PDF y Excel con «Fecha de inicio según la fuente». | `us1-validation.md` | Cumple con nota: no hay desglose heredado porque FR-006a se retiró |
| FR-006a Fecha operativa heredada | No aplica: tras V019 todas las sesiones tienen desplazamiento (15 430 con −300 y 700 con 0 en la copia del 14/11). | `spec.md` (aclaración del 2026-11-13), T040 | Retirado |
| FR-007 Auditoría sobre una copia autorizada antes de publicar el histórico | H006: copia `sarc-20261113T2319Z-manual`; se leyeron los 24 originales Enerlink y se clasificaron por lote (18 UTC-05:00, 6 UTC+00:00), con impacto por día, mes y fuente. | `hotfix.md` (H006), T017–T022 y T042 | Cumple (H006–H007) |
| FR-008 Aprobación explícita antes de la corrección masiva | El responsable aprobó la corrección después de revisar el impacto de H006. La copia previa fue `sarc-20261114T0056Z-previa`. No se construyó la constancia en el sistema: decisión registrada en `CC-PRD-009-20261113`. | `hotfix.md` (H007), `documents/control-cambios-prd.md` | Cumple con nota: aprobación fuera del sistema, por decisión del responsable |
| FR-009 Corrección conservadora e idempotente | V019 dejó iguales los totales mensuales y el general (16 130 sesiones; 310 603,48 kWh) y no modificó instantes. Flyway la aplica una sola vez. | `hotfix.md` (H006) | Cumple (H006–H007) |
| FR-010 Informes ya emitidos intactos; nuevos con su corte | V019 y V020 no tocan `report_issued` ni los artefactos. Se conservan los 3 reportes en el ensayo de T041. Los informes nuevos se identifican con el corte propio. | T018, T040, T041, `us1-validation.md` | Cumple |
| FR-011 Columna de acciones «Ver», «Editar», «Eliminar» según permisos y alcance del detalle | Matriz de permisos probada: el Gerente solo ve las sesiones validadas activas, sin revisiones; una baja da 404. `sessions-page.spec.ts` cubre tabla y tarjeta. | `us3-session-actions.md` | Cumple |
| FR-012 Edición con motivo, confirmación y versiones | `#anEditorEditsWithAReason…`: revisión 2 y revisión 1 consultable, auditoría `ENERGY_SESSION_EDITED`, sin motivo 400. | `us3-session-actions.md` | Cumple |
| FR-013 Reglas vigentes en la edición y aviso de concurrencia | 409 `CONCURRENT_MODIFICATION`, 422 `SESSION_UNCHANGED` y `INVALID_SESSION`. Más de 1 000 kWh pasa a `ENERGY_REVIEW`. Equipo nulo deja `PENDING_LINK`. Energía nula ≠ 0. | `us3-session-actions.md` | Cumple |
| FR-014 Baja lógica con motivo, fuera de cifras, registro conservado | `#aDeletionLeavesTheFigures…`: sale de la lista, el resumen, la tendencia, el ranking y las exportaciones (`SessionPredicates`), y conserva la revisión `DELETION`. Es idempotente. | `us3-session-actions.md`; planes del predicado en T040 | Cumple |
| FR-015 La reimportación respeta identidad, ediciones y bajas | `#aReimportNeitherReactivatesADeletion…`: una baja reimportada queda `DELETED` y no se reactiva. Un lote preparado antes de una edición se omite (`SKIPPED`). | `us3-session-actions.md` | Cumple |
| FR-016 Historial reconstruible y sin acceso de modificación para consulta | Revisiones con autor, momento, motivo y valores; auditoría. El Gerente recibe 403 al editar, eliminar o listar bajas. | `us3-session-actions.md` | Cumple |
| FR-017 Reproducción y causa del parpadeo | Hallazgos confirmados: B1 (filas reemplazadas en Sesiones), B2 (tablero vacío tras un refresco fallido), B3 (lo mismo en Sesiones, por lectura de código). También se registran los casos sin parpadeo. | `us4-baseline.md` | Cumple |
| FR-018 Continuidad visual con datos recientes | `trackBy` conserva 15/15 filas y el foco. `retainedQuery` conserva KPI y 6/6 gráficos tras un refresco fallido. `consumption-dashboard-facade.spec.ts` tiene 6 pruebas. | `us4-results.md` | Cumple |
| FR-019 Vistas equivalentes y coordinación con Feature 008 | Inventario corregido (misma causa). En Uptime e Intermitencias se registró el patrón B2 sin intervenirlo. `chart-view` (Feature 008) no se tocó. | `us4-baseline.md`, `us4-results.md` | Cumple con nota: ver pendientes |
| FR-020 Componentes, accesibilidad y diseño responsivo en ambos temas | Recorridos a 1440, 768 y 360 px en temas claro y oscuro, con menú operable por teclado y foco visible. Armonía de formularios y cabeceras en 17 rutas: contornos de 56 px, etiqueta arriba y acciones a la derecha. | `us4-results.md`, `ui-harmony.md` | Cumple con nota: pendiente menor a 360 px |
| FR-021 Control de cambios del PRD y conciliación | `CC-PRD-009-20261113` registra la decisión A, US2 acreditada por H006–H007 y el alcance de las acciones. La conciliación de PRD, TRD y Feature 002 sigue la matriz del plan. | T003, `documents/control-cambios-prd.md` | Cumple |
| FR-022 Regresiones de RF-001, RF-002, RF-003 y Feature 008 | Backend 902/902 (incluye la suite de Feature 008, contrato OpenAPI y ArchUnit). Frontend 90 archivos y 506 pruebas. Build, lint y formato correctos. V020 sobre la copia de producción conserva las huellas de los datos. | T040, T041 | Cumple |

### Criterios de éxito

| Criterio | Resultado | Evidencia |
|---|---|---|
| SC-001 1211 y 1212 el 11/11 sin cambiar instantes; Wallbox el 10/11 | Cumple en pruebas y en producción. H006 midió 10/11 −2 sesiones y 11/11 +1 con 0 instantes modificados, y se verificó tras publicar el hotfix. | `us1-validation.md`, `hotfix.md` |
| SC-002 100 % del conjunto de aceptación en su período; 5/8/13 kWh; salidas concordantes; corte propio | Cumple: límites de día y mes, 5/8/13 kWh en total, tendencia y ranking, PDF y Excel con el mismo criterio. El caso de 15 kWh con 2 kWh heredados no aplica, porque FR-006a se retiró. La edición entre cortes (13 → 14 kWh) se refleja en el resumen siguiente. | `us1-validation.md`, `us3-session-actions.md` |
| SC-003 Candidatos con evidencia; cero cambios injustificados; cero duplicados; nada sin aprobación | Cumple con nota: 100 % de los 24 lotes Enerlink clasificados, totales sin cambio, sin duplicados (Flyway la aplica una vez) y publicación tras la aprobación del responsable y una copia previa. No hay un rechazo automático por falta de constancia, porque el subsistema no se construyó (decisión registrada). | `hotfix.md` (H006–H007), `control-cambios-prd.md` |
| SC-004 Editores completan acciones; consulta con cero permisos de modificación; historial | Cumple: matriz de roles en API y componente; cada edición o baja deja motivo y revisión. | `us3-session-actions.md` |
| SC-005 Reimportación: cero duplicados, reversiones o reactivaciones; informes archivados intactos | Cumple: pruebas de reimportación de bajas y ediciones; V019 y V020 no tocan los informes archivados. | `us3-session-actions.md`, T018, T040 |
| SC-006 Cero recargas o reinicios innecesarios; datos nuevos se actualizan | Cumple para B1–B3, con medición antes y después en el navegador. Si aparece otro síntoma, se registrará con su pantalla y acción. | `us4-results.md` |
| SC-007 «Ver», «Editar», «Eliminar» desde la fila, en móvil, tablet y escritorio, ambos temas, con teclado | Cumple: recorridos a 1440, 768 y 360 px en ambos temas, y diálogo de edición con contornos de 56 px. La matriz de roles se probó por componente y API, no en el navegador. | `us4-results.md`, `ui-harmony.md` |
| SC-008 Regresiones seleccionadas conservan sus resultados | Cumple: suites completas en verde. Las únicas diferencias de RF-003 son las temporales aprobadas (106 sesiones cambian de día, totales iguales). | T040, `hotfix.md` |

### Pendientes consolidados

Ninguno bloquea el flujo de publicación de Feature 007.

1. **Issue #15** (`ensayo-restauracion`, abierto por la publicación `2026.11.14-43-3549f18`): espera un ensayo de restauración sobre una copia posterior a V020 (por ejemplo `sarc-20261114T1553Z-previa` o la nocturna siguiente). El #13 se cerró con el informe de T041.
2. **gitleaks**: no está instalado en este equipo. Lo ejecuta el CI y `version-candidata.yml` al publicar; la búsqueda manual en los artefactos dio 0 coincidencias.
3. **Referencias a archivos de Cloudinary** en el ensayo de restauración: quedaron «pendiente de ensayar con la exportación». Requiere el índice de exportación dedicado (ya figura en `docs/operacion/pendientes-recursos-reales.md`).
4. **Uptime e Intermitencias**: el patrón B2 (un refresco fallido vacía la vista) quedó registrado y sin intervenir, para coordinarlo con el responsable de Feature 008.
5. **360 px**: el último filtro de las barras con número impar de campos ocupa media fila (`ui-harmony.md`).
6. **Exportación de Enerlink**: conviene exportar siempre con la misma zona. Una sesión de un lote UTC reimportada desde un archivo UTC-05:00 queda como modificada y requiere resolverla (`hotfix.md`).
7. **Publicación**: hecha el 2026-11-14 (ver «Publicación en producción» abajo).

## Publicación en producción (2026-11-14)

Autorizada por el responsable en el chat («Haz todo esto por favor pero bien revisado, la app de produccion debe seguir funcionando»).

**Antes de integrar.** Revisión del diff: sin secretos (las contraseñas que aparecen son de pruebas) y sin códigos reales de puntos de carga. Backend `./mvnw verify` con 904 pruebas, 0 fallos. Frontend: tipos al día con OpenAPI, `lint`, `format:check`, 509 pruebas y build inicial de 347,80 kB.

**Integración.** PR #14 (commit `20d8bb0`, que incluye el registro del hotfix `2dd7dac`) con CI verde en sus 6 trabajos, fusionado en `main` como `3549f18`. Versión candidata `2026.11.14-43-3549f18`: `migrations.latest = V020`, los 6 controles en `success`, `manifest.json` verificado con `SHA256SUMS`.

**Requisitos de la puerta.** Prueba de recepción de avisos vigente (`sarc-alerts-gate` código 0), recibo de copia local de hace 6 h (`sarc-backup-freshness` código 0), certificado con más de 72 h (vence 2026-11-17 22:18 UTC), disco al 3 % y sin errores de la aplicación en 24 h.

**Publicación.** Flujo «Publicar» #5 (ejecución `43611644586`), modo `normal`, aprobado en el entorno `production`. `sarc-deploy`: 15:53:04Z a 15:53:43Z, `COMPLETADA`, `migrationsApplied = [V020]` (183 ms), copia previa `sarc-20261114T1553Z-previa.dump.age`. Las 10 comprobaciones posteriores dieron OK (disponibilidad, base de datos, versión, ingreso, usuarios, inventario, consumo, uptime, reportes, archivos). La aplicación arrancó en 20,8 s, sin reinicios (`NRestarts=0`). El código 143 del registro es la parada planificada de la versión anterior.

**Datos antes y después (solo lectura).** Iguales en todo salvo la migración (19 a 20): 16 537 sesiones `VALIDATED` con 318 452,720 kWh, desplazamientos de origen sin cambio (Enerlink 0: 717, Enerlink −300: 11 300, Wallbox −300: 4 520), la misma huella MD5 de identificador, fecha de inicio e instantes de todas las sesiones, 38 lotes, 38 021 filas, 233 equipos, 4 usuarios y 3 reportes. Después de V020: 0 bajas, 0 fines con desplazamiento propio y `deleted_rows = 0` en los 38 lotes.

**Seguimiento.** El flujo abrió el issue #15 (`ensayo-restauracion` `pendiente`) por la migración aplicada. Se cierra con un ensayo sobre una copia posterior a V020.
