# Tasks: Vinculación Enerlink y sesiones incompletas

**Input**: documentos de `specs/002-vinculacion-enerlink-sesiones/`.
**Prerequisites**: spec.md, plan.md, research.md, data-model.md, contracts/api-changes.md y quickstart.md.
**Tests**: incluidos por petición explícita del usuario en planificación y por `sarc-bakend/CLAUDE.md`. Dentro de cada
historia se escriben primero y deben fallar antes de implementar.
**Organization**: cuatro historias P1. US1 permite demostrar la corrección del incidente de identidades.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: paralelizable: archivos distintos y sin dependencias incompletas.
- **[Story]**: historia de spec.md.

## Path Conventions

- `BE/` = `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/`
- `BT/` = `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/`
- `MIG/` = `sarc-bakend/src/main/resources/db/migration/`
- `FE/` = `sarc-frontend/src/app/`

Reglas generales: Java con tabulaciones y records `from(...)`; sin MapStruct ni Spring Batch. Textos de interfaz en
español con tildes. No editar V001–V008. Tras cambiar DTO: `./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true`
y `npm run api:types`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar punto de partida; el proyecto ya existe.

- [X] T001 Rama de trabajo decidida por el usuario el 2026-10-30: `002-correcion-comparacion-nombres-plataforma-consumo`, aunque su nombre difiera de la feature resuelta en `.specify/feature.json`
- [X] T002 Ejecutar la línea base y registrar fallos previos con `./mvnw test` en `sarc-bakend/` y `npm run build` más `npm test` en `sarc-frontend/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema y comparación común.
**Gate**: completar antes de cualquier historia. No cambia comportamiento visible.

- [X] T003 Crear `MIG/V009__vinculacion_enerlink_sesiones.sql`: quitar `NOT NULL` de `ended_at` y `energy_kwh` en `energy_session` y `energy_session_revision`; añadir `energy_session.link_issue VARCHAR(30)` con CHECK `IDENTITY_INCOMPLETE`, `IDENTITY_NOT_REGISTERED`, `IDENTITY_NOT_VERIFIED`, `IDENTITY_AMBIGUOUS`; CHECK de `link_issue` nulo si `equipment_id` no es nulo; `energy_import_row` clasificación con `LINKABLE` y resultado con `LINKED`; `energy_import_batch.linkable_rows INTEGER NOT NULL DEFAULT 0`; `asset_import_row` clasificación con `IDENTITY_COMPLETED`, `ck_asset_import_row_existing` incluyéndola y resultado con `IDENTITY_ADDED`; `asset_import_batch.identity_completed_rows INTEGER NOT NULL DEFAULT 0`. Sin `UPDATE` de datos
- [X] T004 Crear prueba de esquema en `BT/modules/consumption/infrastructure/persistence/EnergySessionSchemaTest.java`, con el patrón de `BT/modules/inventory/infrastructure/persistence/AssignedCodeSchemaTest.java`: nulos admitidos, `ck_energy_session_span` y `ck_energy_session_energy` vigentes, CHECK de nuevos valores y datos V008 intactos, sobre PostgreSQL desechable
- [X] T005 [P] Añadir `isComplete()` a `BE/shared/catalog/ExternalKey.java`: verdadero solo con ámbito e identificador no vacíos
- [X] T006 [P] Crear `BE/shared/catalog/IdentityMatch.java`: record con `status` (`UNIQUE`, `AMBIGUOUS`, `NOT_VERIFIED`, `NOT_REGISTERED`) y `EquipmentReference equipment`, sin dependencias de Spring
- [X] T007 Añadir `findByPlatform` a `BE/modules/inventory/domain/repository/ExternalIdentityRepository.java`, `BE/modules/inventory/infrastructure/persistence/ExternalIdentityJpaRepository.java` y `BE/modules/inventory/infrastructure/persistence/ExternalIdentityRepositoryAdapter.java`

**Checkpoint**: la aplicación arranca con `ddl-auto=validate` y T004 pasa.

---

## Phase 3: User Story 1 - Registrar y completar identidades Enerlink (Priority: P1) 🎯 MVP

**Goal**: la plantilla deja identidad verificada y visible sin aprobar fila por fila ni alterar el equipo.
**Independent Test**: importar un equipo nuevo y completar otro existente; comprobar identidad visible, `version` intacta e idempotencia.

### Tests for User Story 1

- [X] T008 [P] [US1] Añadir casos a `BT/modules/inventory/application/service/EquipmentLineInterpreterTest.java`: solo «Enerlink cargador» da `ENERLINK_SCOPE_REQUIRED`, solo «Enerlink activo» da `ENERLINK_CHARGER_REQUIRED`, ambos vacíos sin observación ni identidad
- [X] T009 [US1] Añadir casos a `BT/modules/inventory/api/EquipmentImportApiTest.java` para la tabla de reglas de data-model: `IDENTITY_COMPLETED` sin aprobación; `version`, código, serie y conectores intactos; par equivalente D05 en otro equipo da `EXTERNAL_IDENTITY_IN_USE`; par distinto al verificado da `ENERLINK_IDENTITY_DIFFERS`; conflicto no crea ni actualiza y las demás filas continúan; identidad más otro cambio sigue `MODIFIED` y, sin aprobar, no registra la identidad ni cambia el equipo; repetir el par no duplica; dos filas equivalentes por tildes son `DUPLICATE`
- [X] T010 [P] [US1] Añadir casos a `BT/modules/inventory/api/InventoryApiTest.java`: alta manual Enerlink sin ámbito rechazada; par equivalente D05 en otro equipo rechazado; segundo par en el mismo equipo admitido

### Implementation for User Story 1

- [X] T011 [US1] Añadir `IDENTITY_COMPLETED` a `BE/modules/inventory/domain/model/importbatch/EquipmentRowClassification.java`, incluido en `writesEquipment()`, e `IDENTITY_ADDED` a `BE/modules/inventory/domain/model/importbatch/EquipmentRowOutcome.java`
- [X] T012 [US1] Añadir `identityCompleted` a `BE/modules/inventory/domain/model/importbatch/EquipmentRowCounts.java` y columna `identityCompletedRows` a `BE/modules/inventory/domain/model/importbatch/EquipmentImportBatch.java`
- [X] T013 [US1] Emitir `ENERLINK_SCOPE_REQUIRED` en `BE/modules/inventory/application/service/EquipmentLineInterpreter.java` cuando falte ACTIVO con CARGADOR informado
- [X] T014 [US1] Crear `BE/modules/inventory/application/service/EnerlinkIdentityIndex.java`: identidades por `ExternalKey.comparable()`, construido una vez por clasificación y por tramo, no por fila
- [X] T015 [US1] Aplicar en `BE/modules/inventory/application/service/EquipmentRowEvaluator.java` la tabla de reglas de data-model con el índice, y devolver `IDENTITY_COMPLETED` si no hay cambios de equipo y sí identidades por registrar o verificar
- [X] T016 [US1] Actualizar `BE/modules/inventory/application/service/EquipmentImportProcessor.java`: pasar el índice, comparar Enerlink con `ExternalKey.comparable()` en `firstRepeatedKey`, y en `applyRow` para `IDENTITY_COMPLETED` llamar solo a `registerIdentities`, con resultado `IDENTITY_ADDED`, sin `update` ni `EQUIPMENT_UPDATED`
- [X] T017 [US1] Incluir `IDENTITY_COMPLETED` en `nextToApply` e `IDENTITY_ADDED` en `countWritten` de `BE/modules/inventory/infrastructure/persistence/EquipmentImportRowJpaRepository.java`
- [X] T018 [US1] Exigir ámbito y conflicto por clave comparable para Enerlink en `BE/modules/inventory/application/service/ExternalIdentityService.java`, conservando agregar otro par al mismo equipo
- [X] T019 [US1] Añadir `identityCompletedRows` a `BE/modules/inventory/api/response/EquipmentImportBatchResponse.java` y regenerar `sarc-bakend/docs/api/openapi.json` y `FE/core/api/generated/types.gen.ts`
- [X] T020 [US1] Añadir etiqueta «Completan identidad», pista, resultado «Identidad añadida» y conteo en `FE/features/inventory/domain/equipment-import.ts`
- [X] T021 [US1] Actualizar `CONFIRM_MESSAGE` en `FE/features/inventory/presentation/equipment-import-batch-page/equipment-import-batch-page.ts` para indicar que las identidades se completan al confirmar y que los modificados sin aprobar se omiten con sus identidades
- [X] T022 [US1] Exigir ámbito cuando la plataforma sea Enerlink en `FE/features/inventory/presentation/external-identity-dialog/external-identity-dialog.ts`, con mensaje en español
- [X] T023 [P] [US1] Crear `FE/features/inventory/domain/equipment-import.spec.ts` con etiquetas, pistas y conteo

**Checkpoint**: con la plantilla real, las filas de solo identidad quedan visibles en «Identidades externas» sin aprobar.

---

## Phase 4: User Story 2 - Vincular sesiones por identidad confiable (Priority: P1)

**Goal**: coincidencia única vincula sola; el resto queda pendiente con motivo específico.
**Independent Test**: con identidades preparadas, importar cinco sesiones y casos sin coincidencia, sin verificar, ambiguos e incompletos.

### Tests for User Story 2

- [X] T024 [P] [US2] Añadir a `BT/modules/consumption/infrastructure/parser/SessionParsersTest.java`: Enerlink sin CARGADOR o sin ambos no es inválida y su clave no es completa; Wallbox sin referencia sigue con `EQUIPMENT_REFERENCE_REQUIRED`
- [X] T025 [US2] Añadir a `BT/modules/consumption/api/ConsumptionApiTest.java`: cinco sesiones al mismo equipo; variantes D05; cada motivo `IDENTITY_*`; dos equipos candidatos por fixture SQL quedan `IDENTITY_AMBIGUOUS`; vincular pendientes tras verificar respeta ambiguas y fuente; `LINKABLE` al reimportar sin cambios; inventario cambiado entre revisión y confirmación no aplica un vínculo obsoleto; auditoría `ENERGY_SESSION_LINKED` por sesión

### Implementation for User Story 2

- [X] T026 [US2] Declarar `matchIdentities(platform, keys)` en `BE/shared/catalog/EquipmentDirectory.java` e implementarlo en `BE/modules/inventory/application/service/EquipmentDirectoryService.java`: agrupar por clave comparable; verificada en un equipo `UNIQUE`, en dos o más `AMBIGUOUS`, existente sin verificar `NOT_VERIFIED`, sin registro `NOT_REGISTERED`. `findByVerifiedIdentities` no cambia para Wallbox
- [X] T027 [US2] Hacer que `BE/modules/consumption/infrastructure/parser/EnerlinkSessionParser.java` emita siempre `EquipmentLookup.ByIdentity` y que `BE/modules/consumption/infrastructure/parser/TabularSessionParser.java` solo exija referencia si la fuente la provee
- [X] T028 [US2] Crear `BE/modules/consumption/domain/model/session/LinkIssue.java` con los cuatro valores y mapear `linkIssue` en `BE/modules/consumption/domain/model/session/EnergySession.java`, nulo fuera de pendientes Enerlink
- [X] T029 [US2] Hacer que `BE/modules/consumption/application/service/EquipmentResolver.java` devuelva equipo o `LinkIssue`, con clave incompleta como `IDENTITY_INCOMPLETE` sin consultar; Wallbox por `BySerial` sin cambios
- [X] T030 [US2] Añadir `LINKABLE` a `BE/modules/consumption/domain/model/batch/RowClassification.java` y `writesSession()`; `LINKED` a `BE/modules/consumption/domain/model/batch/RowOutcome.java`; `linkable` a `BE/modules/consumption/domain/model/batch/RowCounts.java` y `BE/modules/consumption/domain/model/batch/ImportBatch.java`, sin cambiar el campo `pendingLink`
- [X] T031 [US2] Aplicar en `BE/modules/consumption/application/service/ImportProcessor.java` la tabla de clasificación de data-model para sesión sin equipo: `LINKABLE` si lectura igual y resuelve, `IDENTICAL` si no; `EQUIPMENT_CHANGED` solo con equipo; códigos `IDENTITY_*` en vez de `EQUIPMENT_NOT_IDENTIFIED` en Enerlink
- [X] T032 [US2] Cambiar `link` en `BE/modules/consumption/domain/model/session/EnergySession.java` a `link(equipment, sourceApproved)`, limpiando `linkIssue`, ; añadir `markUnlinkable(LinkIssue)`, que guarda el motivo tras `record` o `reassign` si la sesión queda sin equipo; vincular lo limpia
- [X] T033 [US2] Añadir a `applyNextChunk` de `BE/modules/consumption/application/service/ImportProcessor.java` la nueva resolución por tramo: si el equipo revisado dejó de ser la coincidencia única, guardar pendiente con motivo y detalle; si una pendiente resuelve, vincular
- [X] T034 [US2] Añadir a `applyRow` de `BE/modules/consumption/application/service/ImportProcessor.java`, antes de comparar `hasSameReading`, la rama `LINKABLE`: comprobar la revisión, llamar a `link(equipment, row.isApproved())`, devolver `LINKED` y auditar `ENERGY_SESSION_LINKED` con equipo, par y lote
- [X] T035 [US2] Actualizar `linkPending` en `BE/modules/consumption/application/service/SessionService.java`: recalcular motivo y auditar `ENERGY_SESSION_LINKED` por sesión con equipo, par y origen, conservando el agregado
- [X] T036 [US2] Incluir `LINKED` en `countWritten` de `BE/modules/consumption/infrastructure/persistence/ImportRowJpaRepository.java` y comprobar que `nextToApply` coincide con `ImportRow.isApplicable()`
- [X] T037 [US2] Añadir `linkIssue` a `BE/modules/consumption/api/response/SessionResponse.java`, `linkableRows` a `BE/modules/consumption/api/response/ImportBatchResponse.java` y regenerar contrato y tipos
- [X] T038 [US2] Añadir etiquetas de `LINKABLE`, `LINKED` y conteo en `FE/features/consumption/domain/energy-import.ts`, y textos de `linkIssue` («Identidad Enerlink incompleta», «no registrada», «no verificada», «ambigua») en `FE/features/consumption/domain/consumption.ts`
- [X] T039 [US2] Mostrar el motivo en `FE/features/consumption/presentation/sessions-page/sessions-page.html` y añadir la clasificación en `FE/features/consumption/presentation/import-batch-page/import-batch-page.ts`
- [X] T040 [P] [US2] Crear `FE/features/consumption/domain/energy-import.spec.ts` con etiquetas y conteos

**Checkpoint**: la matriz de vinculación pasa con fixtures propios.

---

## Phase 5: User Story 3 - Contabilizar sesiones iniciadas con datos incompletos (Priority: P1)

**Goal**: energía y término ausentes llegan intactos a consulta e informes, sin confundirse con cero.
**Independent Test**: importar la matriz A–E: 5 sesiones, 3,750 kWh conocidos, 2 sin energía, iguales en tablero, histórico e informes.

### Tests for User Story 3

- [X] T041 [P] [US3] Añadir a `BT/modules/consumption/infrastructure/parser/SessionParsersTest.java`: vacío o solo espacios admitido en Enerlink; texto, negativos y fecha ilegible siguen inválidos; inicio ausente inválido; Wallbox con energía o término vacíos sigue inválido
- [X] T042 [P] [US3] Añadir a `BT/modules/consumption/domain/model/EnergySessionTest.java`: registrar sin energía ni término; `sameAs` con nulos; 0 distinto de nulo; HALF_UP; término igual al inicio válido; término anterior inválido
- [X] T043 [US3] Añadir a `BT/modules/consumption/api/ConsumptionApiTest.java`: matriz A–E con Testcontainers; período solo con ausentes da `energyKwh` nulo y `sessionsWithoutEnergy`; período sin sesiones sigue en 0; promedio entre sesiones con lectura; ranking excluye sin lectura e informa `equipmentWithoutEnergy`; equipo con 0 kWh participa; PDF y Excel se generan y coinciden con tablero
- [X] T044 [P] [US3] Crear `FE/features/consumption/application/sessions-facade.spec.ts`: el resumen del histórico usa período y filtros de la lista, sin estado ni texto; distingue cero, «Sin datos» y sesiones sin energía

### Implementation for User Story 3

- [X] T045 [US3] Añadir a `BE/modules/consumption/infrastructure/parser/TabularSessionParser.java` un método protegido que declare si vacío es admisible, verdadero solo en `BE/modules/consumption/infrastructure/parser/EnerlinkSessionParser.java`; texto inválido sigue siendo error
- [X] T046 [US3] Hacer que `check` de `BE/modules/consumption/domain/policy/SessionRules.java` evalúe `END_BEFORE_START` solo con término, `NEGATIVE_ENERGY` y `ENERGY_OUT_OF_RANGE` solo con energía, y que `requireValid` solo exija inicio
- [X] T047 [US3] Quitar `nullable = false` de `endedAt` y `energyKwh` en `BE/modules/consumption/domain/model/session/EnergySession.java` y `BE/modules/consumption/domain/model/session/SessionRevision.java`; no sustituir ausencias
- [X] T048 [US3] Hacer `sameAs` seguro con nulos en `BE/modules/consumption/domain/model/session/SessionReading.java`: nulo solo es igual a nulo
- [X] T049 [P] [US3] Añadir `sessionsWithoutEnergy` a `BE/modules/consumption/domain/repository/EnergyTotals.java` y `BE/modules/consumption/domain/repository/DailyEnergy.java`: `energyKwh` nulo si hay sesiones sin lecturas, 0 si no hay sesiones
- [X] T050 [US3] Cambiar en `BE/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java`: `sum` sin `coalesce`, `count(energyKwh)` y `having count(energyKwh) > 0` en `byEquipment`, sin tocar el desempate
- [X] T051 [US3] Actualizar `BE/modules/consumption/application/service/ConsumptionCalculator.java` y `BE/modules/consumption/application/result/ConsumptionSummary.java`: promedio entre sesiones con lectura, nulo sin lecturas; `equipmentWithoutEnergy`; `fillDays` mantiene 0 en días sin sesiones
- [X] T052 [US3] Admitir nulos en `BE/modules/consumption/infrastructure/report/ConsumptionLabels.java`: `Sin datos` y `No informado`, sin fallar `percent`
- [X] T053 [US3] Actualizar `BE/modules/consumption/infrastructure/report/ConsumptionExcelRenderer.java`: filas sin `List.of` con nulos, «Energía conocida», sesiones sin energía, nota de ranking
- [X] T054 [US3] Actualizar `BE/modules/consumption/infrastructure/report/ConsumptionPdfRenderer.java`: KPI, cobertura, nota de excluidos y metodología
- [X] T055 [US3] Actualizar `BE/modules/consumption/api/response/SessionResponse.java`, `BE/modules/consumption/api/response/ImportRowResponse.java` y `BE/modules/consumption/api/response/ConsumptionSummaryResponse.java` con `@Schema(nullable = true)` y campos del contrato; regenerar contrato y tipos
- [X] T056 [P] [US3] Añadir a `FE/shared/format/quantity-format.ts` una variante para nulos que devuelva «Sin datos», sin alterar `kwh`
- [X] T057 [US3] Actualizar `FE/features/consumption/presentation/dashboard-page/dashboard-page.html` y `FE/features/consumption/presentation/dashboard-page/dashboard-page.ts`: «Energía conocida», «Sin datos» y sesiones sin energía; un nulo se dibuja como 0 solo en gráficos
- [X] T058 [P] [US3] Mostrar nota de equipos excluidos en `FE/features/consumption/presentation/top-equipment/top-equipment.ts`
- [X] T059 [P] [US3] Admitir nulos en `FE/features/consumption/presentation/share-chart/share-chart.ts` y `FE/features/consumption/presentation/daily-table/daily-table.ts`
- [X] T060 [US3] Mostrar «No informado» en `FE/features/consumption/presentation/sessions-page/sessions-page.html` y `FE/features/consumption/presentation/import-batch-page/import-batch-page.html`, sin rango horario incompleto ni presentar la sesión como activa
- [X] T061 [US3] Añadir a `FE/features/consumption/application/sessions-facade.ts` un `httpResource` con `ConsumptionApi.summaryRequest`, usando período y filtros de la lista, sin estado ni texto
- [X] T062 [US3] Mostrar en `FE/features/consumption/presentation/sessions-page/sessions-page.html` el resumen oficial del período: sesiones, «Energía conocida» o «Sin datos», y sesiones sin energía
- [X] T063 [P] [US3] Crear `FE/shared/format/quantity-format.spec.ts` con cero, nulo y valor

**Checkpoint**: SC-003 y SC-006 pasan.

---

## Phase 6: User Story 4 - Reimportar y corregir con trazabilidad (Priority: P1)

**Goal**: completar sin duplicar, sin cambios silenciosos de equipo y con versión anterior.
**Independent Test**: reimportar la matriz, completar una sesión, vaciar un dato conocido, intentar cambio de cargador y revisión obsoleta.

### Tests for User Story 4

- [X] T064 [P] [US4] Añadir a `BT/modules/consumption/domain/model/EnergySessionTest.java`: `correct` a nulo conserva revisión; sesión sin equipo admite cambio de par; `identifiesSameCharger` con variantes D05 en Enerlink y regla actual en Wallbox
- [X] T065 [US4] Añadir a `BT/modules/consumption/api/ConsumptionApiTest.java`: reimportar dos veces sin cambios; completar pendiente de aprobación; `SESSION_VALUE_REMOVED`; otro par con equipo exige reasignación; pendiente con lectura distinta y par resoluble se corrige y vincula; revisión obsoleta; solo completar identidad no toca históricos

### Implementation for User Story 4

- [X] T066 [US4] Actualizar `BE/modules/consumption/domain/model/session/EnergySession.java`: `identifiesSameCharger` con `ExternalKey.comparable()` en Enerlink y `correct` sin exigir mismo cargador si no hay equipo
- [X] T067 [US4] Actualizar `BE/modules/consumption/application/service/ImportProcessor.java`: `MODIFIED` sobre sesión pendiente que resuelve vincula tras corregir y audita `ENERGY_SESSION_LINKED`, y `SESSION_VALUE_REMOVED` nombra el campo
- [X] T068 [P] [US4] Mostrar `SESSION_VALUE_REMOVED` destacado en `FE/features/consumption/presentation/import-batch-page/import-batch-page.html`

**Checkpoint**: SC-004 y SC-005 pasan.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: verificación global y documentación.

- [X] T069 [P] Crear `sarc-bakend/docs/adr/0009-sesiones-enerlink-incompletas.md` con ausencia de energía o término, `IDENTITY_COMPLETED` y `LINKABLE`
- [X] T070 [P] Corregir Javadoc que afirme que toda sesión tiene energía o término en `BE/modules/consumption/domain/model/session/SessionReading.java`, `BE/modules/consumption/application/service/ConsumptionCalculator.java` y `BE/modules/consumption/domain/policy/SessionRules.java`
- [X] T071 [P] Añadir a `BT/shared/importing/reading/TabularReaderTest.java` una prueba con libro de dos hojas: solo se leen filas de la primera (D09, FR-020)
- [X] T072 Crear prueba SC-007 con un archivo sintético de la misma forma que el incidente en `BT/modules/consumption/api/ConsumptionApiTest.java`, sin incorporar datos reales al repositorio
- [X] T073 Ejecutar `./mvnw test` en `sarc-bakend/`, incluidos `BT/architecture/ArchitectureTest.java` y `BT/architecture/OpenApiContractTest.java`, y corregir regresiones
- [X] T074 Ejecutar `npm run build` y `npm test` en `sarc-frontend/`, comprobando el presupuesto de 500 kB
- [ ] T075 Ejecutar el recorrido de `specs/002-vinculacion-enerlink-sesiones/quickstart.md`, incluido descartar el lote `READY`, reimportar plantilla y sesiones, y revisar las filas que sigan `MODIFIED`
- [X] T076 Detener lo levantado por las pruebas en 8080, 4200 y 5432

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**: sin dependencias.
- **Foundational**: tras Setup; bloquea todas las historias.
- **US1 a US4**: tras Foundational.
- **Polish**: tras todas las historias.

### User Story Dependencies

- **US1**: sin dependencia de otras historias.
- **US2**: independiente con fixtures; con datos reales necesita US1.
- **US3**: independiente. Comparte `ImportProcessor`, `SessionResponse`, `sessions-page` y `SessionParsersTest` con US2.
- **US4**: necesita T031 de US2 y que los nulos de US3 existan para sus casos de vaciar y completar. Hacer tras ambas.

### Within Each User Story

Pruebas, enums y modelo, repositorios, servicios, DTO y contrato, frontend. No editar el mismo archivo en paralelo.

### Parallel Opportunities

- Fase 2: T005 y T006.
- US1: T008 y T010; T023 tras T020.
- US2: T024; T040 tras T038.
- US3: T041, T042 y T044; T049; T056; T058, T059 y T063 tras T056.
- US4: T064; T068.
- Polish: T069, T070 y T071.
- US1 puede avanzar junto a US2 o US3 con dos personas: toca inventario, no consumo, salvo `openapi.json`.

## Parallel Example: User Story 3

```text
T041 SessionParsersTest.java
T042 EnergySessionTest.java
```

Tras T055:

```text
T058 top-equipment.ts
T059 share-chart.ts y daily-table.ts
```

## Implementation Strategy

### MVP First

1. Fases 1 y 2.
2. US1. Reimportar la plantilla ya repara «Identidades externas» y resuelve las pendientes con `Vincular pendientes`.
3. Validar y detenerse si se desea entregar en pequeño.

### Incremental Delivery

1. US1: identidades.
2. US2: motivos y vinculación segura.
3. US3: sesiones incompletas.
4. US4: reimportación completa.

Cada paso deja pruebas pasando. La feature se considera completa solo con las cuatro: las 83 inválidas dependen de US3.

## Notes

- Spec 003 toca las mismas clases y usa V010; debe implementarse después.
- No corregir aquí D10 ni D14.
- Commit solo cuando el usuario lo pida.
