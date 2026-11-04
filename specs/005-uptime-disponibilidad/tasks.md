---


description: "Tareas de implementación de la feature 005"
---

> **Cierre técnico 2026-11-04:** este listado corresponde únicamente a Feature-005. T124 permanece pendiente de medición humana. Las decisiones posteriores de captura semanal y pasteles se preservan como borradores locales para Feature-006 y no forman parte de estos commits.

# Tasks: Uptime y disponibilidad, con ajustes de presentación en Consumo

**Feature**: `005-uptime-disponibilidad` | **Fecha**: 2026-11-01
**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [uptime-api.md](contracts/uptime-api.md), [reports-and-consumption.md](contracts/reports-and-consumption.md), [quickstart.md](quickstart.md)
**Rama**: `codex/005-uptime-disponibilidad`; commit/merge/push autorizados por el usuario el 2026-11-04. T124 conserva su estado pendiente.

**Tests**: incluidos porque la solicitud del plan y los criterios SC-001 a SC-012 exigen pruebas unitarias, de integración, seguridad por rol, importación, reportes, interfaz y regresión. Escribir cada prueba primero y comprobar que falla antes de implementar.

**Formato**: `- [ ] Tnnn [P?] [USn?] Acción y ruta`. IDs en orden de ejecución. Rutas relativas al repositorio, con la grafía real `sarc-bakend`. `[P]`: archivos distintos y sin dependencia de tareas incompletas. `[BLOQUEADA DP-04b]`: no ejecutar hasta recibir la muestra real.

**Límites**: no modificar V001–V012, PRD, TRD, Constitución ni D01–D14. Sin dependencias nuevas. Sin MapStruct, ECharts ni Spring Batch. Pruebas de migración solo con Testcontainers o PostgreSQL desechable. No imprimir secretos. Cerrar puertos propios (8080, 4200, 5432 y arnés) tras cada ejecución.

## Phase 1: Setup

**Objetivo**: registrar la base antes de cambiar nada.

- [X] T001 [P] Registrar commit base, hashes de V001–V012, versiones y resultado de `./mvnw test`, `npm test`, `npm run lint`, `npm run format:check` y `npm run build` en `specs/005-uptime-disponibilidad/evidence/baseline.md`, sin alterar los cambios locales ajenos ya presentes
- [X] T002 [P] Con `Feature004BrowserSandbox`, sin tocar la base de Compose, emitir PDF y Excel de Consumo como `GERENTE` y `ANALISTA` con datos y filtros fijos, guardar totales, sesiones, ranking y conteos, y capturar el dashboard a 360, 390, 768, 1024 y 1440 en ambos temas, conforme a «Línea base de Consumo» de quickstart, en `specs/005-uptime-disponibilidad/evidence/consumption-baseline.md`
- [X] T003 [P] Crear la matriz de pantallas, estados, cinco anchos y dos temas, adaptando el enfoque de `specs/004-visibilidad-ux-reportes/visual-audit-plan.md`, en `specs/005-uptime-disponibilidad/visual-audit-plan.md`
- [X] T004 [P] Crear la hoja de contraste de DP-04b y DP-05 con columnas, hoja, zona, unidad, precisión, etiqueta de conector y origen del archivo por completar, en `specs/005-uptime-disponibilidad/evidence/enerlink-sample.md`

---

## Phase 2: Foundational

**Objetivo**: esquema, puerto de inventario, vocabulario, permisos y esqueleto. **Ninguna historia empieza antes de completar esta fase.**

- [X] T005 Redactar ADR del módulo, con adaptaciones al TRD (nombres de tabla, cabecera y revisiones, tipos de período, alias, reportes de un solo perfil) en `sarc-bakend/docs/adr/0011-modulo-uptime.md`
- [X] T006 Escribir prueba de esquema sobre PostgreSQL 18 con Testcontainers: aplica V001–V013, unicidades, FK y `CHECK` de data-model, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/persistence/UptimeMigrationTest.java`
- [X] T007 Crear `sarc-bakend/src/main/resources/db/migration/V013__uptime_disponibilidad.sql` conforme a data-model: `upt_measurement` con `source CHECK IN ('ENERLINK')`, `CHECK period_end > period_start`, `CHECK (effective_revision IS NULL OR effective_revision <= latest_revision)`, `uq_upt_measurement_identity UNIQUE (connector_id, source, period_start, period_end)`, `ix_upt_measurement_connector_end (connector_id, period_end DESC)`, `ix_upt_measurement_period_end (period_end DESC)`; `upt_measurement_revision` con `UNIQUE (measurement_id, revision)`, `reading_class` en `MEASURED, NO_DATA, OUT_OF_SCOPE, RETIRED`, `period_type` en `ROLLING_24H, ROLLING_7D, ROLLING_30D, EXACT_WEEK`, `review_state` en `PENDING, VALIDATED, SUPERSEDED`, `origin` en `MANUAL, BLOCK, FILE, PASTE`, minutos `INTEGER NULL` con `>= 0`, `reported_percent NUMERIC(12,4)`, `justification VARCHAR(500)`, `equipment_label`, `equipment_code` y `connector_type_label` con la longitud de sus columnas de origen (D12), clase especial con minutos y porcentaje nulos, `EXACT_WEEK` con confirmación, `VALIDATED` con `validated_at`, `observations` arreglo JSONB; `upt_measurement_exclusion` con `action` en `EXCLUDED, RESTORED` y `UNIQUE (measurement_id, measurement_version)`; `upt_import_batch` con `declared_period_type` NOT NULL en `ROLLING_24H, ROLLING_7D, ROLLING_30D`, `upt_import_row` con `UNIQUE (batch_id, row_number)`, clasificación `NEW, IDENTICAL, CONFLICT, INVALID, PENDING_LINK`, `outcome` `APPLIED, SKIPPED, FAILED, PENDING_LINK, LINKED` e índice parcial `ix_upt_import_row_pending`; `upt_connector_alias` con `label_original VARCHAR(150)` y `uq_upt_connector_alias UNIQUE (source, equipment_id, label_key)`
- [X] T008 [P] Crear puerto libre de Spring con conector, posición, tipo, activo, equipo, nombre, código, red, tipo de infraestructura, marca como texto y estado administrativo, sin UID/PUK ni OCPP, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/catalog/ConnectorDirectory.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/catalog/MeasurableConnector.java`
- [X] T009 Escribir prueba de filtros, búsqueda, conectores inactivos y equipos `DELETED` e `INACTIVE` en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/application/service/ConnectorDirectoryServiceTest.java`
- [X] T010 Implementar el puerto, siguiendo `EquipmentDirectoryService`, sin cambiar el dominio de inventario, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/ConnectorDirectoryService.java`
- [X] T011 [P] Crear vocabulario `ReadingClass`, `ReviewState`, `PeriodType` (con duración nominal), `MeasurementOrigin` y `Observation` (código y validable, tabla de data-model §3) en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/model/measurement/`
- [X] T012 [P] Implementar FR-001 con prueba: KIA solo electrolineras; JAC y CONECTZ ambos tipos, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeScope.java` y `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeScopeTest.java`
- [X] T013 [P] Crear `EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')"` y `canReadOperational()` con `CurrentActor`, sin renombrar `ConsumptionReadAccess`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimePermissions.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeReadAccess.java`
- [X] T014 [P] Crear datos de prueba: cinco roles y combinaciones, redes, tipos, marcas, equipos de 1 a 3 conectores, inactivos y eliminados, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/support/Feature005Fixtures.java`
- [X] T015 Crear fixture opt-in con PostgreSQL 18, puerto aleatorio, almacenamiento de prueba y datos de `Feature005Fixtures`, siguiendo `Feature004BrowserSandbox`, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/Feature005BrowserSandbox.java`
- [X] T016 [P] Crear `UPTIME_EDITORS` y rótulos de tipo, estado y observación en `sarc-frontend/src/app/features/uptime/domain/uptime-permissions.ts` y `sarc-frontend/src/app/features/uptime/domain/uptime-labels.ts`
- [X] T017 Crear archivo de rutas solo con el contenedor y pestañas por rol, sin referenciar páginas aún inexistentes (cada historia registra las suyas), en `sarc-frontend/src/app/features/uptime/uptime.routes.ts` y `sarc-frontend/src/app/features/uptime/presentation/uptime-page/uptime-page.ts`
- [X] T018 Registrar `/uptime` y entrada de menú visible a todos los roles en `sarc-frontend/src/app/app.routes.ts` y `sarc-frontend/src/app/layout/navigation.ts`

**Checkpoint**: V013 aplica sobre base desechable, ArchUnit pasa, regresiones intactas.

---

## Phase 3: User Story 1 — Registrar la medición de un conector (P1) MVP

**Objetivo**: captura manual por conector, con período real, cálculo y clases de resultado.

**Prueba independiente**: registrar dos conectores de una electrolinera JAC y comprobar 97,29 %, período, tipo e independencia. Punto KIA rechazado.

### Pruebas

- [X] T019 [P] [US1] Probar 1296/105/0/39 = 97,29, 0 %, 100 %, `HALF_UP`, sin `double`, y Ocupado y Con falla sin descontar, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeFormulaTest.java`
- [X] T020 [P] [US1] Probar límites 0, ±1 y ±2 minutos, suma cero, 0,01 y 0,0101 puntos, 9907 %, 86.400 minutos en una semana, valor negativo, 9.57, ventana móvil de duración irregular, y validable o no por código, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/MeasurementRulesTest.java`
- [X] T021 [P] [US1] Probar alineación lunes a lunes en America/Guayaquil, fin exclusivo, fin futuro, fin ≤ inicio, y ventana alineada sin confirmar que sigue provisional, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/PeriodRulesTest.java`
- [X] T022 [P] [US1] Probar con MockMvc `POST /api/v1/uptime/measurements` y `GET /grid`: 201 pendiente, errores 422 del contrato, duración `1h 20m` y porcentaje fuera de rango técnico (`UPTIME_PERCENT_TOO_LARGE`) como error de captura sin crear medición, punto KIA, dos conectores independientes, observadas persistidas con valor original en `source_values` y lo digitado no se altera, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeMeasurementApiTest.java`
- [X] T023 [P] [US1] Probar reinicio entre conectores, «Guardar y siguiente», avisos y conservación de lo digitado ante error, en `sarc-frontend/src/app/features/uptime/application/capture-facade.spec.ts`

### Implementación

- [X] T024 [P] [US1] Implementar fórmula de minutos con `BigDecimal` y dos decimales `HALF_UP`, sin reutilizar D11, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeFormula.java`
- [X] T025 [US1] Implementar la clasificación de research R-06 como política única en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/MeasurementRules.java`
- [X] T026 [P] [US1] Implementar reglas de período, semana exacta y duración nominal en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/PeriodRules.java`
- [X] T027 [US1] Crear entidades con `BaseAuditableEntity` y transiciones de data-model §7 en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/model/measurement/Measurement.java` y `MeasurementRevision.java` de la misma carpeta
- [X] T028 [US1] Crear puerto y adaptador JPA en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/repository/MeasurementRepository.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/persistence/MeasurementRepositoryAdapter.java`
- [X] T029 [US1] Implementar alta con alcance, reglas, conservación de valores crudos también en origen manual, etiqueta de fecha del hecho (equipo, código, tipo de conector; nombre de la fuente si se importó), auditoría `UPTIME_MEASUREMENT_CREATED` y traducción de violación de unicidad, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementService.java`
- [X] T030 [US1] Implementar grilla por `periodStart` y `periodEnd` exactos, con orden estable, situación por identidad exacta y `showInactive`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementGridService.java`
- [X] T031 [US1] Crear DTO `record` con `from` y endpoints de captura con `@PreAuthorize(UptimePermissions.EDITORS)` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeMeasurementController.java`, `api/request/` y `api/response/` del módulo
- [X] T032 [US1] Regenerar `sarc-bakend/docs/api/openapi.json` y los tipos de `sarc-frontend/src/app/core/api/generated`
- [X] T033 [P] [US1] Crear cliente de captura y consulta en `sarc-frontend/src/app/features/uptime/data-access/uptime-api.ts`
- [X] T034 [US1] Implementar estado de grilla y secuencia en `sarc-frontend/src/app/features/uptime/application/capture-facade.ts`
- [X] T035 [US1] Crear formulario con Signal Forms, reinicio por conector, aviso de observación y `guardUnsavedChanges` en `sarc-frontend/src/app/features/uptime/presentation/measurement-form/measurement-form.ts`
- [X] T036 [US1] Crear grilla responsive y «Guardar y siguiente» en `sarc-frontend/src/app/features/uptime/presentation/capture-page/capture-page.ts`, y registrar ruta `captura` con `roleGuard` en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`

**Checkpoint**: US1 funciona sola con medición pendiente; aún sin indicadores.

---

## Phase 4: User Story 2 — Validar, corregir y evitar duplicados (P1)

**Objetivo**: ciclo de calidad, historial y estados especiales.

**Prueba independiente**: registrar, validar, repetir, corregir, excluir y restaurar una medición, revisando estado, historial y auditoría.

### Pruebas

- [X] T037 [P] [US2] Probar idéntica 200, distinta 409 `UPTIME_MEASUREMENT_DIFFERS`, dos originales no enteros distintos 409, corrección con y sin justificación, vigente que sigue hasta validar, confirmación como `EXACT_WEEK` de ventana alineada mediante corrección, validación con observación validable y no validable, `GET /measurements` con `queue` para operativos, y rol de consulta con solo vigentes validadas no excluidas, 404 en las demás y 403 con `queue`, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeValidationApiTest.java`
- [X] T038 [P] [US2] Probar dos correcciones y dos validaciones simultáneas, alta concurrente idéntica, y validación por lote con observadas, versión obsoleta y más de 500 ítems, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeConcurrencyTest.java`
- [X] T039 [P] [US2] Probar continuidad al cambiar tipo, retirar, reincorporar posición y reemplazar equipo, según D06 y D07, que tras renombrar el equipo una corrección conserve la etiqueta de revisión 1, y que el detalle distinga etiqueta de fecha del hecho e identificación vigente (D12), en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeHistoryContinuityTest.java`
- [X] T040 [P] [US2] Probar marcas sobre equipo por conector, sin datos con justificación, exclusión reversible, sin borrado físico y auditoría con valores anteriores y nuevos, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeClassificationApiTest.java`
- [X] T041 [P] [US2] Probar estados de revisión, selección por lote sin observadas, división de más de 500 y resultado de omitidas en `sarc-frontend/src/app/features/uptime/application/review-facade.spec.ts`

### Implementación

- [X] T042 [US2] Añadir reconocimiento de idéntica, comparando también originales que no cupieron en minutos, conflicto y corrección con revisión nueva que copia la etiqueta de fecha del hecho, incluida confirmación de semana exacta con autor y momento, a `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementService.java`
- [X] T043 [US2] Implementar validación individual y por lote, máximo 500, por ítem con omitidas `OBSERVED`, `STALE_VERSION`, `NOT_PENDING`, `NOT_FOUND`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementValidationService.java`
- [X] T044 [US2] Crear entidad y flujo de exclusión y restauración en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/model/measurement/MeasurementExclusion.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementValidationService.java`
- [X] T045 [US2] Implementar clasificaciones por conector o equipo sin sobrescribir mediciones existentes en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementService.java`
- [X] T046 [US2] Exponer correcciones, validación, exclusión, restauración, clasificaciones, `GET /{id}/revisions`, `GET /measurements` con `queue` y `GET /measurements/{id}` con `factLabel`, estas dos sin `EDITORS` y con proyección por rol y `UptimeReadAccess` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/MeasurementReadService.java`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeMeasurementController.java`, y regenerar `sarc-bakend/docs/api/openapi.json`
- [X] T047 [US2] Implementar lista de pendientes y observadas con `queue` y selección por lote, dividiendo selecciones de más de 500, en `sarc-frontend/src/app/features/uptime/application/review-facade.ts` y `sarc-frontend/src/app/features/uptime/presentation/review-page/review-page.ts`, y registrar ruta `revision` con `roleGuard` en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`
- [X] T048 [US2] Crear detalle con versión vigente y pendiente, historial, correcciones, justificaciones y exclusión, mostrando identificación vigente y, si difiere, etiqueta de fecha del hecho, en `sarc-frontend/src/app/features/uptime/presentation/measurement-page/measurement-page.ts`, y registrar ruta `mediciones/:id` en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`
- [X] T049 [US2] Tratar 409 con recarga y oferta de corrección sin perder lo digitado en `sarc-frontend/src/app/features/uptime/application/capture-facade.ts`

**Checkpoint**: US1 y US2 cumplen historial y trazabilidad.

---

## Phase 5: User Story 3 — Consultar disponibilidad en dashboards (P1)

**Objetivo**: indicadores comunes, cobertura y privacidad por rol.

**Prueba independiente**: con datos validados, pendientes, provisionales y exclusiones, filtrar con cada rol y cotejar valores.

### Pruebas

- [X] T050 [P] [US3] Probar agregados por minutos, oficiales y provisionales separados, una por conector, solapes, 0 % incluido, nulo sin datos, equipo evaluado y sin datos, cobertura por equipo en pares conector-semana, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeCalculatorTest.java`
- [X] T051 [P] [US3] Probar denominador con sin datos, sin medición, pendientes, fuera de alcance, retirados, inactivos con y sin medición, conector retirado sin medición listado como Retirado, medición excluida que sigue en denominador, pares conector-semana solo de semanas cerradas, un estado por unidad y denominador cero, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeCoverageTest.java`
- [X] T052 [P] [US3] Probar semana oficial reciente, preferencia de 7 días, ventana más reciente sin 7 días, empate por menor duración, rango de una duración nominal que deja fuera ventanas antiguas, preferencia en bloque provisional junto a oficial sin `periodType`, y sin datos, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/OpeningPeriodPolicyTest.java`
- [X] T053 [P] [US3] Probar empates sobre valor a dos decimales con posición compartida, orden por nombre, todos los conectores evaluados, sin corte Top, y minutos sumados en varias semanas, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeRankingTest.java`
- [X] T054 [P] [US3] Probar filtros combinados, 366 y 367 días, avisos `NO_OFFICIAL_WEEK`, `LATEST_WEEK_MISSING`, `NO_EVALUABLE_CONNECTORS`, total «sin atribuir» invariable y `results/exclusions`, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeSummaryApiTest.java`
- [X] T055 [P] [US3] Probar matriz de cinco roles y combinaciones sobre todas las rutas, incluido acceso directo, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeSecurityTest.java`
- [X] T056 [P] [US3] Probar sobre JSON serializado que un rol de consulta no recibe valores pendientes, observaciones, justificaciones, revisiones ni existencia de no vigentes o excluidas, y que `results/exclusions` solo trae conector y estado, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeReadProjectionTest.java`
- [X] T057 [P] [US3] Probar rótulo y rango junto a la cifra, avisos, tendencia ausente, ocultamiento operativo, refresco y aviso por fallo de actualización automática en `sarc-frontend/src/app/features/uptime/application/dashboard-facade.spec.ts`

### Implementación

- [X] T058 [P] [US3] Implementar regla de apertura aislada en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/OpeningPeriodPolicy.java`
- [X] T059 [US3] Implementar predicados de data-model §8, agregación, cobertura por pares conector-semana de semanas cerradas, rango provisional de FR-032 y ranking como fuente única en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/policy/UptimeCalculator.java`
- [X] T060 [US3] Añadir lectura de vigentes por filtros, conteos de estado y total «sin atribuir» por `period_end` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/persistence/MeasurementRepositoryAdapter.java`
- [X] T061 [US3] Implementar consulta en `REPEATABLE READ` de solo lectura, rango máximo 366 días, proyección por rol y avisos en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeQueryService.java`
- [X] T062 [US3] Exponer `summary`, `results/connectors`, `results/equipment`, `results/exclusions` y `scope/connectors` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeController.java`, y regenerar `sarc-bakend/docs/api/openapi.json`
- [X] T063 [US3] Implementar filtros, período resuelto, `refreshWhileVisible` y aviso por fallo de actualización en `sarc-frontend/src/app/features/uptime/application/dashboard-facade.ts`
- [X] T064 [P] [US3] Crear selector de equipo y conector de dos niveles en `sarc-frontend/src/app/features/uptime/presentation/connector-filter/connector-filter.ts`
- [X] T065 [US3] Crear tablero con KPI rotulados, bloques oficial y provisional, cobertura, cantidades por estado, tendencia con `ChartView`, resultados, ranking y detalle de exclusiones en `sarc-frontend/src/app/features/uptime/presentation/dashboard-page/dashboard-page.ts`, y registrar ruta `tablero` con redirección por defecto en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`
- [X] T066 [US3] Ajustar detalle sin operaciones para roles de consulta en `sarc-frontend/src/app/features/uptime/presentation/measurement-page/measurement-page.ts`

**Checkpoint**: MVP de uptime completo con US1 a US3. Entrega útil sin importación ni reportes.

---

## Phase 6: User Story 4 — Cargar mediciones por pegado o Excel (P2)

**Objetivo**: bloque textual, lote tabular, vinculación y alias.

**Prueba independiente**: pegar bloque válido y ambiguo; cargar filas nuevas, idénticas, en conflicto, inválidas y desconocidas; confirmar solo las admisibles; repetir sin duplicar.

### Pruebas

- [X] T067 [P] [US4] Probar etiquetas en cualquier orden, equivalencia de escritura, porcentaje opcional, incompleto, ambiguo y repetido, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkBlockParserTest.java`
- [X] T068 [P] [US4] Probar con encabezados sintéticos, sin valor de evidencia sobre Enerlink y por pegado, sin carga de archivo: tipo declarado, fila de duración no nominal observada, vista previa, clasificaciones, reimportación, decisión, confirmación, confirmación exacta y etiqueta de fuente en revisión 1, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeImportApiTest.java`
- [X] T069 [P] [US4] Probar con libro sintético de varias hojas, sin valor de evidencia sobre Enerlink, que solo se lee la primera hoja (sustituida por T130 para el informe semanal), en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkUptimeParserTest.java`
- [X] T070 [P] [US4] Probar recuperación tras interrupción y reintento, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/bootstrap/InterruptedUptimeImportRecoveryTest.java`
- [X] T071 [P] [US4] Probar vinculación, aprobación de alias, reimportación por alias, reasignación sin efecto histórico, alias a conector inactivo, número inicial inequívoco, etiqueta de fuente en medición creada, D05 y ambigüedad, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimePendingLinkApiTest.java`
- [X] T072 [P] [US4] Probar interpretación del bloque en formulario sin pérdida de lo digitado, en `sarc-frontend/src/app/features/uptime/application/capture-facade.spec.ts`, y vinculación con `rememberLabel`, en `sarc-frontend/src/app/features/uptime/application/pending-links-facade.spec.ts`

### Implementación

- [X] T073 [US4] Crear puertos para que `api` y `application` no dependan de `infrastructure`, conforme a ArchUnit, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/port/MeasurementBlockParser.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/port/UptimeRowParser.java`
- [X] T074 [US4] Implementar analizador de bloque sin estado y `POST /measurements/parse` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkBlockParser.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeMeasurementController.java`
- [X] T075 [US4] Integrar pegado de bloque con origen `BLOCK` y texto en `sourceText` en `sarc-frontend/src/app/features/uptime/presentation/measurement-form/measurement-form.ts`
- [X] T076 [US4] Crear lote con `declared_period_type` y fila extendiendo `BaseImportBatch` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/model/importing/UptimeImportBatch.java` y `UptimeImportRow.java` de la misma carpeta
- [X] T077 [P] [US4] Crear entidad `ConnectorAlias` con `label_original VARCHAR(150)`, `label_key` de `ExternalKey` y `active` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/model/importing/ConnectorAlias.java`
- [X] T078 [US4] Crear repositorio de importación y alias en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/repository/UptimeImportRepository.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/persistence/UptimeImportRepositoryAdapter.java`
- [X] T079 [US4] Crear analizador tabular con `TabularReader` y `HeaderLayout`, columnas en un solo lugar, marcadas provisionales hasta DP-04b, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkUptimeParser.java`
- [X] T080 [US4] Implementar resolución: identidad con `matchIdentities`, alias activo, número inicial inequívoco; lo demás `PENDING_LINK`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/ConnectorResolver.java`
- [X] T081 [US4] Implementar preparación y confirmación en bloques de 500 filas, `outcome` por fila, reclasificación al confirmar, tipo declarado sin deducir del archivo, exacta solo en filas alineadas y no observadas, etiqueta de fuente, misma comparación de idénticas y reutilización de `MeasurementRules`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeImportProcessor.java`
- [X] T082 [US4] Implementar servicio de lotes con `ImportFiles`, `ImportJobs`, `FileStorage`, `requireVersion` y auditoría en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeImportService.java`
- [X] T083 [US4] Implementar recuperación al iniciar, siguiendo `InterruptedImportRecovery`, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/bootstrap/InterruptedUptimeImportRecovery.java`
- [X] T084 [US4] Implementar vinculación con etiqueta de fuente en la medición creada, y alias, con auditoría, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/PendingLinkService.java` y `ConnectorAliasService.java` de la misma carpeta
- [X] T085 [US4] Exponer importación solo con origen `PASTE`, manteniendo la carga de archivo desactivada hasta T094, pendientes y alias en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeImportController.java` y `UptimePendingLinkController.java` de la misma carpeta, y regenerar `sarc-bakend/docs/api/openapi.json`
- [X] T086 [P] [US4] Crear cliente e historial sobre `ImportHistoryState` en `sarc-frontend/src/app/features/uptime/data-access/uptime-imports-api.ts` y `sarc-frontend/src/app/features/uptime/application/uptime-import-history.ts`
- [X] T087 [P] [US4] Hacer opcional `upload` en `ImportDialogData` y ocultar la opción de archivo cuando falte, sin cambiar Consumo ni Inventario, con prueba, en `sarc-frontend/src/app/shared/imports/import-dialog/import-dialog.ts`, `import-dialog.html` e `import-dialog.spec.ts` de la misma carpeta
- [X] T088 [US4] Crear historial con `shared/imports` e `ImportDialog` solo pegado, con `choice` obligatorio de tipo de ventana, en `sarc-frontend/src/app/features/uptime/presentation/imports-page/imports-page.ts`, y registrar ruta `importaciones` con `roleGuard` en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`
- [X] T089 [US4] Crear vista previa, decisiones, justificación de conflicto y confirmación exacta en `sarc-frontend/src/app/features/uptime/presentation/import-batch-page/import-batch-page.ts`, y registrar ruta `importaciones/:id` en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`
- [X] T090 [US4] Implementar vinculación y gestión de alias en `sarc-frontend/src/app/features/uptime/application/pending-links-facade.ts` y `sarc-frontend/src/app/features/uptime/presentation/pending-links-page/pending-links-page.ts`, y registrar ruta `vinculacion` con `roleGuard` en `sarc-frontend/src/app/features/uptime/uptime.routes.ts`

### Condicionadas a la muestra real

- [X] T091 [US4] Contrastar archivo real y completar columnas, hoja, zona, unidad, precisión, etiqueta de conector y origen, elegir `source_site` o `source_charger` para la etiqueta de D12, sin copiar datos personales, en `specs/005-uptime-disponibilidad/evidence/enerlink-sample.md`
- [X] T092 [US4] Decidir con producto si un equipo de único conector resuelve sin etiqueta, y si minutos o segundos alteran FR-004, y registrarlo en `specs/005-uptime-disponibilidad/spec.md`
- [X] T093 [US4] Ajustar columnas y añadir prueba con muestra anonimizada en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkUptimeParser.java` y `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkUptimeParserTest.java`
- [X] T094 [US4] Activar carga multipart con origen `FILE` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeImportController.java` y su opción en `sarc-frontend/src/app/features/uptime/presentation/imports-page/imports-page.ts`
- [X] T095 [US4] Medir filas observadas por precisión real y confirmar o proponer cambio de tolerancia (DP-05) en `specs/005-uptime-disponibilidad/evidence/enerlink-sample.md`; cualquier cambio de valor pasa por `specs/005-uptime-disponibilidad/spec.md`

**Checkpoint**: bloque, pegado tabular y carga de archivo operativos tras contrastar la muestra real (2026-11-02).

---

## Phase 7: User Story 5 — Descargar reportes de uptime (P2)

**Objetivo**: PDF y Excel coherentes con el dashboard, archivados.

**Prueba independiente**: emitir ambos formatos con filtros conocidos y cotejar con el dashboard del mismo corte.

### Pruebas

- [X] T096 [P] [US5] Probar igualdad de cifras entre dashboard, PDF y Excel en mismo corte, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeReportConsistencyTest.java`
- [X] T097 [P] [US5] Probar secciones, rótulos, sin 0 % por ausencia, nombres actuales y aviso sin oficial en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/report/UptimePdfRendererTest.java` y `UptimeExcelRendererTest.java` de la misma carpeta
- [X] T098 [P] [US5] Probar ausencia de UID/PUK, URL OCPP, observaciones y justificaciones, y contenido igual en cinco roles, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/report/UptimeReportPrivacyTest.java`
- [X] T099 [P] [US5] Probar claves nuevas y regresión de claves vigentes en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/shared/report/ReportPublicationTest.java`
- [X] T100 [P] [US5] Probar emisión, cabeceras, archivo inmutable tras corrección y rango de 367 días en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/api/UptimeReportApiTest.java`
- [X] T101 [P] [US5] Probar etiqueta `UPTIME` y filtro en `sarc-frontend/src/app/features/reports/application/issued-reports-facade.spec.ts`

### Implementación

- [X] T102 [US5] Extender de forma aditiva con `periodKind`, `periodType`, `brand` y `connectorId` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/report/ReportPublication.java`
- [X] T103 [P] [US5] Crear puerto y datos de reporte en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/port/UptimeReportRenderer.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/result/UptimeReport.java`
- [X] T104 [US5] Crear PDF con `CorporatePdf`, según el orden del contrato, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/report/UptimePdfRenderer.java` y `UptimePdfCharts.java` de la misma carpeta
- [X] T105 [P] [US5] Crear Excel con `CorporateWorkbook` y las siete hojas del contrato en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/report/UptimeExcelRenderer.java`
- [X] T106 [US5] Implementar snapshot `REPEATABLE READ`, render fuera de transacción, `ReportArchive` kind `UPTIME`, perfil `OFFICIAL_ONLY` y auditoría en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/application/service/UptimeReportService.java`
- [X] T107 [US5] Exponer `GET /report` con `X-Report-Id`, `X-Report-Cut-Off` y `no-store` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/api/controller/UptimeController.java`, y regenerar `sarc-bakend/docs/api/openapi.json`
- [X] T108 [P] [US5] Añadir `UPTIME` a `ReportKind` y `KIND_LABELS`, y comprobar que `managerial-report-dialog` no ofrece opciones de Consumo para ese tipo, en `sarc-frontend/src/app/features/reports/domain/issued-report.ts`
- [X] T109 [US5] Añadir descarga con filtros vigentes en `sarc-frontend/src/app/features/uptime/presentation/dashboard-page/dashboard-page.ts`

**Checkpoint**: criterios CA-004-06, SC-006 y FR-037 a FR-040 verificados.

---

## Phase 8: User Story 6 — Leer mejor Consumo y su PDF gerencial (P3)

**Objetivo**: entrega A, independiente de uptime. Sin migración, API ni dependencias.

**Prueba independiente**: comparar con la línea base de T002 por rol, anchos y temas.

### Pruebas

- [X] T110 [P] [US6] Invertir la expectativa de `GERENTE` en línea 76, a ausencia de «Aprobadas sobre umbral», y conservar líneas 117–118, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionReportPrivacyTest.java`
- [X] T111 [P] [US6] Añadir caso sin acceso operativo y mantener el operativo en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionExcelRendererTest.java`
- [X] T112 [P] [US6] Revisar perfil en línea 62 y añadir caso sin acceso operativo en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionPdfRendererTest.java`
- [X] T113 [P] [US6] Comprobar que líneas 416 y 420 usan actor operativo, y conservarlas, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionApiTest.java`
- [X] T114 [P] [US6] Probar bloques, cinco categorías, cantidades y energía para operativos, ausencia para consulta y nota intacta en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.spec.ts`

### Implementación

- [X] T115 [P] [US6] Mover las KPI de líneas 58–59 dentro de `if (report.operationalAllowed())` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionPdfRenderer.java`
- [X] T116 [P] [US6] Mover las filas de líneas 108–109 dentro de `if (summary.pendingLink() != null)` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionExcelRenderer.java`
- [X] T117 [US6] Reorganizar en dos bloques con patrón `.groups` y marcado único, conservando título, `aria-labelledby`, cinco categorías y nota, en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.html`
- [X] T118 [US6] Retirar reglas de `.excluded ul` ya inaplicables y alinear con `inventory-kpis.scss`, sin tokens nuevos, en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.scss`
- [X] T119 [US6] Comparar con T002 y registrar igualdad de totales, sesiones, ranking y conteos, y ausencia del dato para consulta, en `specs/005-uptime-disponibilidad/evidence/consumption-comparison.md`

**Checkpoint**: SC-010 verificado, entrega A publicable por separado.

---

## Phase 9: Polish & Cross-Cutting

**Objetivo**: verificación final y cierre.

- [X] T120 [P] Ejecutar auditoría de `specs/005-uptime-disponibilidad/visual-audit-plan.md` a 360, 390, 768, 1024 y 1440 en ambos temas, con arnés externo y `Feature005BrowserSandbox`, y registrar en `specs/005-uptime-disponibilidad/evidence/visual-audit.md`
- [X] T121 [P] Verificar WCAG 2.2 AA: teclado, foco, nombres, contraste, errores anunciados y estados sin depender del color, en `specs/005-uptime-disponibilidad/evidence/accessibility.md`
- [X] T122 Aplicar Impeccable (critique, audit, polish) a Consumo y pantallas nuevas, dentro del Design System y sin ampliar alcance; registrar defectos y ajustes en `specs/005-uptime-disponibilidad/evidence/impeccable.md`
- [X] T123 [P] Medir con datos sintéticos de R-18 dashboard, grilla, ranking, vista previa, confirmación y emisión, y registrar entorno y resultado sin declarar capacidades no medidas, en `specs/005-uptime-disponibilidad/evidence/performance.md`
- [ ] T124 [P] Medir tiempo de SC-001 y lectura de SC-008 con recorrido real y registrar en `specs/005-uptime-disponibilidad/evidence/usability.md`
- [X] T125 Ejecutar los 48 escenarios de quickstart y registrar resultado en `specs/005-uptime-disponibilidad/evidence/quickstart-results.md`
- [X] T126 Ejecutar `./mvnw test`, `npm test`, `npm run lint`, `npm run format:check` y `npm run build`; comprobar presupuesto de 500 kB y hashes V001–V012 frente a T001, y registrar en `specs/005-uptime-disponibilidad/evidence/final-gates.md`
- [X] T127 Actualizar reglas y estado del módulo en `sarc-bakend/docs/adr/0011-modulo-uptime.md`, incluidas tareas `[BLOQUEADA DP-04b]` sin resolver y reglas que producto deba llevar al control de cambios del PRD, y notas de despliegue en `specs/005-uptime-disponibilidad/deployment-notes.md`

### Informe semanal tal como se envía (2026-11-02)

- [X] T128 Enmendar D09 a la Constitución 1.2.0 para que el informe semanal lea todas sus hojas, en `.specify/memory/constitution.md`
- [X] T129 Agregar `uptime_basis`, hoja y fila de origen y semana declarada con V014, en `sarc-bakend/src/main/resources/db/migration/V014__uptime_informe_semanal.sql`
- [X] T130 Leer todas las hojas con `TabularReader.readSheets` y analizar el modelo sin Activo ni Cargando, con hoja y fila por fila, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/infrastructure/parser/EnerlinkUptimeParser.java`
- [X] T131 Guardar y agregar el uptime informado (`REPORTED_PERCENT`) en reglas, revisión, hechos y cálculo, en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/domain/`
- [X] T132 Cargar con la semana elegida (`weekStart`, lunes ya terminado) e identificar por Cargador cuando falta Activo, en `UptimeImportService`, `ConnectorResolver` y `EquipmentDirectoryService`
- [X] T133 [P] Elegir la semana al cargar, mostrar hoja y fila, y uptime informado en detalle y corrección, en `sarc-frontend/src/app/features/uptime/` y `sarc-frontend/src/app/shared/imports/import-dialog/`
- [X] T134 Contrastar con el modelo real de 127 filas y registrar en `specs/005-uptime-disponibilidad/evidence/enerlink-sample.md`
- [X] T135 Proponer en Vinculación el único conector activo del mismo tipo cuando el número de la etiqueta contradice el inventario (V015, `ConnectorResolver`, `pending-links-page`); la persona confirma y puede recordar la etiqueta

---

## Dependencies & Execution Order

### Fases

| Fase | Depende de | Bloquea |
|---|---|---|
| Setup | Nada | Foundational; T002 antes de tocar Consumo |
| Foundational | Setup | US1 a US5 |
| US1 | Foundational | US2 |
| US2 | US1 | US3 completo para pendientes y correcciones |
| US3 | US2 | US5 |
| US4 | US1 y US2 | Nada; T091–T095 dependen de la muestra |
| US5 | US3 | Nada |
| US6 | Solo T001 y T002 | Nada |
| Polish | Historias entregadas | Cierre |

US6 no depende de V013 ni del módulo uptime. Puede ejecutarse antes o en paralelo y publicarse aparte.

### Dentro de cada historia

1. Pruebas primero, y comprobar que fallan.
2. Política de dominio, entidades, repositorio.
3. Servicios, controladores, contrato OpenAPI.
4. Tipos generados, datos, facade, presentación.

`openapi.json` es archivo único: las tareas que lo regeneran no van en paralelo entre sí.

### Oportunidades de paralelo

- **Setup**: T001–T004.
- **Foundational**: T008, T011, T012, T013, T014, T016 tras T007.
- **Por historia**: tareas de prueba `[P]` juntas, luego dominio `[P]`.
- **Entre historias**: US6 en paralelo con todo; US4 y US5 en paralelo tras US3, porque tocan archivos distintos salvo `openapi.json`.

### Ejemplo: US3

```text
T050 UptimeCalculatorTest    T051 UptimeCoverageTest
T052 OpeningPeriodPolicyTest T053 UptimeRankingTest
T054 UptimeSummaryApiTest    T055 UptimeSecurityTest
T056 UptimeReadProjectionTest
T057 dashboard-facade.spec
```

### Ejemplo: US6

```text
T110 PrivacyTest  T111 ExcelRendererTest  T112 PdfRendererTest
T113 ApiTest      T114 dashboard-page.spec
Luego: T115 PdfRenderer  T116 ExcelRenderer
```

## Implementation Strategy

**MVP de uptime**: Phases 1, 2 y US1 a US3. Entrega captura, validación, historial, tablero y privacidad, sin importación.

**Entrega A independiente**: T001, T002 y US6, si conviene publicar Consumo primero.

**Incrementos**:

1. MVP, con revisión y regresión.
2. US5: reportes, valor gerencial.
3. US4 sin Excel: bloque, pegado, vinculación y alias.
4. T091–T095 al recibir la muestra.
5. Polish.

US4 y US5 pueden invertirse. El orden de IDs sigue prioridad de spec; el bloque textual (T073–T075) puede adelantarse tras US1, porque no usa lote.

## Cobertura de requisitos

| Requisitos | Tareas |
|---|---|
| FR-001–FR-005 | T007–T012, T024–T036 |
| FR-006–FR-012 | T019–T021, T024–T026, T050 |
| FR-013 | T007 (`CHECK` ampliable), ADR T005 |
| FR-014–FR-018 | T007, T040, T045, T051, T059 |
| FR-019–FR-024 | T037–T046 |
| FR-025–FR-029 | T067–T095 |
| FR-030–FR-036 | T050–T066, T120, T121 |
| FR-037–FR-040 | T096–T109 |
| FR-041, FR-042 | T013, T037, T046, T055, T056, T061, T098 |
| FR-043, FR-044 | T114, T117, T118 |
| FR-045–FR-047 | T110–T116, T119 |
| D12 | T007, T029, T039, T042, T046, T048, T097 |
| SC-001, SC-008 | T124 |
| SC-002–SC-007, SC-009 | Pruebas de cada historia, T125 |
| SC-010 | T119 |
| SC-011 | T120, T121 |
| SC-012 | T126 |
