# Tasks: Código asignado según estado de uso KIA

**Input**: documentos de `specs/001-codigo-uso-kia/`.
**Prerequisites**: spec.md, plan.md, research.md, data-model.md, contracts/inventario.md y quickstart.md.
**Tests**: incluidos por petición explícita del usuario en planificación y por los escenarios de la spec.
**Organization**: tres historias P1; resultados de implementación en quickstart.md.

## Format: `[ID] [P?] [Story] Description`

Cada tarea incluye checkbox, ID secuencial y rutas relativas a la raíz. [US1]–[US3] identifican
historias de la spec. [P] permite trabajo paralelo solo después de cumplir las dependencias
indicadas, con archivos distintos y sin ejecutar suites sobre fuentes que otro trabajo esté cambiando.
Los grupos de rutas indicados en una tarea constituyen una única unidad coordinada.
Los archivos de prueba nuevos se crean en la implementación, no en esta generación de tareas.

## Path Conventions

- Backend: `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/`.
- Pruebas: `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/`.
- Frontend Angular 22: `sarc-frontend/src/app/`.
- Migraciones: `sarc-bakend/src/main/resources/db/migration/`.
- No hay inicialización de proyecto, autenticación o framework nuevos. RF-002 conserva sus
  DTOs y mapeos toCommand/from existentes; MapStruct no se incorpora como refactor lateral.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar contexto y aislamiento reutilizando infraestructura existente.

- [X] T001 Preparar validación aislada con la configuración existente de sarc-bakend/src/test/java/com/tidsec/sarc_bakend/TestcontainersConfiguration.java y documentar comandos/evidencias previstos en specs/001-codigo-uso-kia/quickstart.md; confirmar Docker y FileStorage simulado, sin usar la base local ni crear infraestructura paralela.

- [X] T002 Revisar gates de .specify/memory/constitution.md, specs/001-codigo-uso-kia/spec.md y specs/001-codigo-uso-kia/checklists/requirements.md; registrar en specs/001-codigo-uso-kia/quickstart.md la línea base de suites y hashes V001–V007 antes de modificar fuentes; no confundir resultados previos con aceptación de la feature.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: habilitar persistencia y contrato nullable comunes sin flexibilizar otras redes/estados.
**Gate**: completar esta fase antes de implementar historias. Esquema/contrato no son aún una entrega funcional.

### Tests y contrato antes del cambio

- [X] T003 [P] Crear sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/infrastructure/persistence/AssignedCodeSchemaTest.java con PostgreSQL 18 desechable: migrar hasta V007, sembrar códigos y relaciones previos, aplicar siguiente versión y verificar 'Solo la rama KIA/punto/FREE puede admitir null.', rechazo SQL directo de vacíos/formato/estados ajenos, múltiples NULL, UNIQUE global y datos previos sin cambios.

- [X] T004 [P] Crear sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/EquipmentCodeContractTest.java con comprobaciones de contrato nullable para detalle/listado/referencia de consumo sin depender de nuevas altas; usar fixtures o dobles de lectura. Detectar ausencia en el contrato publicado antes de su regeneración, manteniendo UUID y versión.

### Esquema y contratos compartidos

- [X] T005 Crear sarc-bakend/src/main/resources/db/migration/V008__codigo_kia_segun_uso.sql (siguiente número libre si V008 ya está ocupado), tras T003: retirar NOT NULL universal y reemplazar CHECK con presencia explícita en ramas obligatorias; 'No usar NULLS NOT DISTINCT.' y 'No UPDATE ni backfill: se conservan literalmente los códigos y el contenido histórico.'; mantener UNIQUE/FK y nunca editar V001–V007.

- [X] T006 [P] Ajustar nulabilidad de assignedCode en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Equipment.java, sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/api/response/EquipmentResponse.java, sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/api/response/EquipmentSummaryResponse.java y sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/response/EquipmentRefResponse.java tras T004; verificar sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/catalog/EquipmentReference.java y EquipmentView. Respetar 'DTOs de respuesta y referencia compartida admiten código ausente, sin cambiar id, red o tipo.'; conservar toCommand/from y Jakarta Validation contextual en dominio.

- [X] T007 Regenerar sarc-bakend/docs/api/openapi.json mediante sarc-bakend/src/test/java/com/tidsec/sarc_bakend/architecture/OpenApiContractTest.java y luego sarc-frontend/src/app/core/api/generated/types.gen.ts con npm run api:types, tras T005–T006; comprobar T003–T004 y prueba de contrato sin modo update. No modificar tipos generados manualmente ni introducir MapStruct.

**Checkpoint**: pruebas SQL de la matriz y contrato pasan; ningún código previo cambió.
T003 prueba SQL directamente y T004 usa lecturas preparadas, por lo que no necesitan todavía
que una nueva alta FREE atraviese el dominio.

## Phase 3: User Story 1 - Crear un punto KIA según su estado de uso (Priority: P1)

**Goal**: alta individual FREE sin código, BLOCKED con código obligatorio, valores informados
válidos y únicos, lectura/exportación sin fallos por null.
**Independent Test**: con los demás datos válidos, variar uso/código en creación manual y POST;
dos FREE sin código deben coexistir y un BLOCKED vacío debe rechazarse con mensaje español.
**MVP**: incremento demostrable de alta individual; no equivale a liberar la feature completa.

### Tests for User Story 1

Escribir primero, comprobar fallos atribuibles a las expectativas nuevas y después implementar.
No considerar suficiente un fallo de compilación por cambio de firma.

- [X] T008 [P] [US1] Ampliar sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/domain/policy/EquipmentClassificationTest.java y sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/EquipmentTest.java para altas FREE sin código/null/vacío/espacios, BLOCKED/DISCONNECTED obligatorios y 'Seis dígitos, primero 1–9, único'; conservar pruebas JAC/CONECTZ/estaciones y cambiar firmas de fixtures sin suprimir rechazos.

- [X] T009 [P] [US1] Ampliar sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/InventoryApiTest.java con POST directo: dos FREE sin código, FREE con 100000/999999/654321, inválidos 012345/000000/12345/1234567/12345A, duplicados activos/inactivos/eliminados, auditoría y exportación PDF/Excel del alta sin código; verificar mensajes españoles y ausencia de equipo creado en rechazos.

- [X] T010 [P] [US1] Añadir pruebas de alta y obligatoriedad reactiva en sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form.spec.ts y sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form-dialog.spec.ts: FREE opcional, otros usos obligatorios, formato si hay contenido y payload sin sustitutos; conservar validación de UID/PUK y estado faltante.

### Implementation for User Story 1

- [X] T011 [US1] Extender sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/policy/EquipmentClassification.java para resolver código con uso final y adaptar invocación en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Equipment.java y sus pruebas; validar uso antes de resolver. Cumplir 'El estado de uso sigue obligatorio para puntos e inaplicable para estaciones.' y 'Seis dígitos, primero 1–9, único'; ausencia solo FREE KIA punto, normalizada a null, manteniendo código por serie en los demás equipos.

- [X] T012 [US1] Ajustar sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/infrastructure/persistence/EquipmentRepositoryAdapter.java para omitir predicado de código si falta; conservar sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentIdentifiers.java excluyendo propio UUID y reservas globales. Cumplir 'Varias ausencias sí son válidas.'; no sustituir comparación por IS NULL ni relajar UNIQUE.

- [X] T013 [US1] Hacer tolerante a ausencia la auditoría de alta en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentService.java: evitar Map.of con null omitiendo solo esa entrada; mantener evento, actor, transacción, autorización y campos restantes. Verificar que los DTOs de entrada existentes no incorporen obligatoriedad incondicional.

- [X] T014 [US1] Reutilizar sarc-frontend/src/app/features/inventory/domain/equipment-rules.ts y ajustar required/pattern en sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form-dialog.ts y su .html: campo visible KIA punto, requerido BLOCKED/DISCONNECTED, patrón solo con contenido tras recorte, sin consultas adicionales ni generación de códigos.

- [X] T015 [US1] Adaptar lectura nullable en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/infrastructure/report/InventoryPdfRenderer.java, sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/infrastructure/report/InventoryExcelRenderer.java, sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionLabels.java y sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionReportService.java; usar texto vacío antes de List.of/concatenación, revisar renderers de consumo sin cambiar columnas, métricas ni archivos archivados. Esta compatibilidad de lectura está autorizada por FR-011; no modificar identidad, conciliación, filtros, permisos ni funcionalidades de negocio de consumo.

- [X] T016 [US1] Adaptar presentación nullable en sarc-frontend/src/app/features/consumption/presentation/equipment-filter/equipment-filter.ts, sarc-frontend/src/app/features/consumption/presentation/top-equipment/top-equipment.ts, sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.html y lectores de inventario encontrados desde sarc-frontend/src/app/features/inventory/domain/equipment.ts; evitar null/undefined literales y mantener selección por UUID y alias OpenAPI, sin persistir marcadores visuales; limitar la adaptación RF-003 a lectura, selección y presentación según FR-011.

- [X] T017 [US1] Validar el incremento de alta con T008–T010, incluidas comprobaciones PDF/Excel y auditoría de FREE sin código en sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/InventoryApiTest.java; registrar resultados en specs/001-codigo-uso-kia/quickstart.md. El alta debe poder consultarse y exportarse, no solo pasar el formulario.

**Checkpoint**: US1 pasa de forma independiente después de la base. Incluye consumidores
nullable imprescindibles; las importaciones se completan en US3 antes de publicar toda la feature.

## Phase 4: User Story 2 - Cambiar el estado sin perder datos (Priority: P1)

**Goal**: conservar 654321 al pasar BLOCKED a FREE y exigir código válido en la transición inversa.
**Independent Test**: un equipo BLOCKED con código y otro FREE sin código; ejecutar cada transición
sobre fixtures independientes, comparando propiedades de negocio, rechazo atómico y auditoría.

### Tests for User Story 2

- [X] T018 [P] [US2] Añadir transiciones y borrador draft en sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/EquipmentTest.java: 'BLOCKED con 654321 → FREE: conservar 654321 incluso si no se envía código nuevo.', FREE ausente → BLOCKED sin/con válido y FREE → DISCONNECTED sin código rechazado; comparar demás datos y conectores.

- [X] T019 [P] [US2] Añadir PUT directo en sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/InventoryApiTest.java para omisión/null/vacío/espacios con código anterior, FREE ausente editando nombre, transición con código válido/duplicado y versión obsoleta; incluir corrección explícita ya autorizada del código sin cambiar uso, con valor nuevo válido y duplicado, manteniendo auditoría; comprobar rechazo atómico, mensajes, UUID estable, conectores, ubicación, horario, identidades y auditoría, sin cambios de negocio no solicitados.

- [X] T020 [P] [US2] Añadir en sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form.spec.ts y sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form-dialog.spec.ts pruebas de precarga, BLOCKED → FREE → BLOCKED, conservación 654321 y envío sin reescribirlo; comprobar que alternar uso no borra el modelo.

### Implementation for User Story 2

- [X] T021 [US2] Resolver omisión/vacío del código KIA con el valor existente antes de validar uso final en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/domain/model/equipment/Equipment.java, revisando llamada de sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentService.java; aplicar solo al código, sin convertir PUT en mezcla general de campos. Cumplir 'La eliminación explícita no se añade ni redefine; omisión/vacío no elimina un código KIA existente.'; conforme a FR-012, conservar el tratamiento previo de correcciones explícitas autorizadas con código nuevo, sin ignorarlas por la conservación automática ni habilitar nuevas operaciones; no cambiar generación por serie de otros tipos.

- [X] T022 [US2] Asegurar conservación y conversión coherente del código precargado en sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form.ts y sarc-frontend/src/app/features/inventory/presentation/equipment-form-dialog/equipment-form-dialog.ts; reutilizar initialFormValue/toEquipmentDraft/toUpdateInput y no añadir efectos que limpien el campo; ajustar solo lo necesario para pasar T020.

- [X] T023 [US2] Ejecutar y revisar los casos históricos de T019 en sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/InventoryApiTest.java comparando antes/después de la transición: código 654321, UUID, conectores, serie, UID/PUK, ubicación, horario e identidades externas; verificar auditoría de cambio de uso en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentChanges.java sin exigir igualdad de timestamps/versionado que deben evolucionar.

- [X] T024 [US2] Ejecutar pruebas de transiciones T018–T020/T023 y comprobar los siete escenarios de US2 de specs/001-codigo-uso-kia/spec.md; registrar evidencia en specs/001-codigo-uso-kia/quickstart.md, incluida ausencia de modificación parcial y reutilización del código propio.

**Checkpoint**: US2 se verifica con fixtures propios, sin depender de ejecutar el recorrido de US1.
Reutiliza la base y política común de US1; no añade borrado explícito de códigos.

## Phase 5: User Story 3 - Misma regla en toda operación autorizada (Priority: P1)

**Goal**: equivalencia entre registro, edición e importación; permisos, otras redes e históricos intactos.
**Independent Test**: repetir alta y transición con solicitudes directas y FILE/PASTE, comprobar
preview/confirmación, roles y reportes de un equipo sin código con sesiones de consumo.

### Tests for User Story 3

- [X] T025 [P] [US3] Ampliar sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/EquipmentImportApiTest.java para FILE y PASTE: FREE nuevo sin código, dos ausencias, BLOCKED/DISCONNECTED faltantes, formato/duplicados informados, aprobación de MODIFIED, conservación de 654321 y FREE sin código → BLOCKED; cubrir corrección explícita ya autorizada de código informado con aprobación y validaciones previas; adaptar fixture antes inválido FREE vacío a BLOCKED y recalcular conteos sin perder intención.

- [X] T026 [P] [US3] Ampliar sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/EquipmentCodeContractTest.java con equivalencia de resultados de solicitudes directas y operaciones autorizadas, GET detalle/listado null y errores españoles; cubrir roles de consulta denegados y confirmar que PATCH de estado administrativo no se redefine como cambio de uso.

- [X] T027 [P] [US3] Ampliar sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionApiTest.java con inventario FREE sin código: vincular sesiones por serie/identidad verificada, mantener UUID, kWh y sesiones; generar PDF/Excel, filtrar y verificar históricos/revisiones y copias archivadas inalterados tras cambiar uso del equipo; demostrar US3/escenario 6 sin cambios de conciliación, filtros ni permisos.

### Implementation for User Story 3

- [X] T028 [US3] Eliminar intolerancia a código ausente en auditoría de alta importada en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentImportProcessor.java; omitir solo entrada de código cuando null, conservar evento/importBatch, no generar sustitutos ni añadir claves de deduplicación para ausencia.

- [X] T029 [US3] Integrar y verificar política de estado final en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/EquipmentRowEvaluator.java y sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/application/service/ProposedEquipment.java: reutilizar draft/detailsOver en preview y confirmación, conservar código omitido y aprobación/revisión de MODIFIED; no duplicar la regla ni tratar error de fila como fallo global. Cambiar solo si T025 revela una brecha.

- [X] T030 [US3] Actualizar explicación de obligatoriedad en sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/infrastructure/parser/EquipmentSheetColumns.java y comprobar plantilla generada por sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/inventory/infrastructure/report/XlsxEquipmentImportTemplate.java: mismas columnas/primera hoja, código opcional FREE y requerido otros usos KIA; extender prueba de plantilla en sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/EquipmentImportApiTest.java.

- [X] T031 [US3] Ejecutar T025–T027 y la matriz de tres historias por las vías descritas en specs/001-codigo-uso-kia/contracts/inventario.md; incluir revalidación con inventario cambiado tras preview y reintento sin duplicación, preservando comportamiento existente; registrar resultados por escenario en specs/001-codigo-uso-kia/quickstart.md.

**Checkpoint**: US3 pasa con fixtures propios; ninguna operación permite BLOQUEADO sin código
ni altera la regla de DESCONECTADO. Las modificaciones de lectura en RF-003 no cambian métricas.

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: cierre de concurrencia, contrato, arquitectura, compilación y aceptación de conjunto.

- [X] T032 Añadir y ejecutar carrera de dos altas con mismo código en sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/inventory/api/InventoryApiTest.java: exactamente una confirmada, otra conflicto controlado y sin fuga de datos; comprobar separación frente a dos altas sin código y mantener UNIQUE como cierre final, sin nuevas políticas de bloqueo.

- [X] T033 Ejecutar suites backend, sarc-bakend/src/test/java/com/tidsec/sarc_bakend/architecture/ArchitectureTest.java y sarc-bakend/src/test/java/com/tidsec/sarc_bakend/architecture/OpenApiContractTest.java sin regeneración, y suite completa con comandos de specs/001-codigo-uso-kia/quickstart.md; registrar resultados y resolver solo regresiones atribuibles a esta feature.

- [X] T034 [P] Ejecutar pruebas Angular, lint y build según sarc-frontend/package.json y specs/001-codigo-uso-kia/quickstart.md tras todas las historias; comprobar compilación contra sarc-frontend/src/app/core/api/generated/types.gen.ts, sin editar artefactos generados para ocultar errores.

- [X] T035 Completar validación end-to-end aislada de AC1–AC4 y SC-001–SC-005 y registrar evidencia en specs/001-codigo-uso-kia/quickstart.md: formulario, solicitudes directas, importación, reportes, conservación histórica y roles; comparar valores/archivos previos, sin usar ni limpiar datos de la base local.

- [X] T036 Revisar diff final contra specs/001-codigo-uso-kia/plan.md, specs/001-codigo-uso-kia/data-model.md y .specify/memory/constitution.md; registrar cierre en specs/001-codigo-uso-kia/quickstart.md confirmando hashes de sarc-bakend/src/main/resources/db/migration/V001__init_roles_usuarios.sql hasta V007 intactos, sin cambios ajenos, sin nuevo framework/endpoint y con entrega coordinada del cliente nullable. Marcar tareas solo con evidencia real.

## Dependencies & Execution Order

### Phase Dependencies

```text
T001 → T002
  → (T003 || T004)
  → (T005 después de T003 || T006 después de T004)
  → T007
  → US1: (T008 || T009 || T010) → T011 → T012 → T013 → T014 → T015 → T016 → T017
  → US2: (T018 || T019 || T020) → T021 → T022 → T023 → T024
  → US3: (T025 || T026 || T027) → T028 → T029 → T030 → T031
  → T032 → (T033 || T034) → T035 → T036
```

El símbolo || representa trabajos en archivos distintos; la ejecución de pruebas se coordina
cuando los archivos estén estables. No se exige paralelizar para cumplir la feature.

### User Story Dependencies

Todas las historias tienen prioridad P1. El orden refleja dependencias de archivos y reutilización,
no una degradación de prioridad de US2/US3.

- US1 depende de Setup y Foundational.
- US2 reutiliza la política de US1 y toca Equipment.java/formulario ya modificados: serializar
  integración después de US1; sus escenarios siguen siendo comprobables por separado.
- US3 reutiliza política y conservación de US1/US2; integra importación y no regresión sobre esa base.
- No ejecutar historias enteras en paralelo: comparten InventoryApiTest, Equipment.java y formulario.
- La aceptación completa exige las tres historias y el cierre; el MVP es solo una demostración
  de US1, no autorización para omitir FR-008 de validación consistente en todas las operaciones.

### Within Each User Story

Pruebas nuevas antes de cambios, observar fallo pertinente y llevarlas a verde; dominio antes de
servicios, luego interfaz y validación integrada. Mantener transacciones y autorización existentes.
No crear rutas nuevas ni reinterpretar PATCH administrativo como estado de uso.

## Parallel Example: User Story 1

Después de T007: preparar T008 (política/entidad), T009 (API) y T010 (formulario) en paralelo.
Trabajan sobre archivos diferentes. Unificar fixtures y correr pruebas con fuentes estables.
Implementación T011–T017 secuencial para evitar que auditoría/reportes lean una regla a medio adaptar.

## Parallel Example: User Story 2

Después de T017: T018 (entidad), T019 (PUT/API) y T020 (formulario) pueden prepararse en paralelo.
Integrar T021–T024 en orden; ningún trabajo paralelo debe editar esos archivos de prueba a la vez.

## Parallel Example: User Story 3

Después de T024: T025 (importación), T026 (contrato/autorización) y T027 (consumo) pueden
prepararse en paralelo. T028–T031 serializan cambios y ejecución integrada.
Compartir Testcontainers/FileStorage simulados mediante la infraestructura existente, sin BD local.

## Implementation Strategy

### MVP First (User Story 1 Only)

Completar T001–T017 y demostrar alta individual FREE sin código con consulta y exportación.
Detenerse a comprobar AC1/AC2; no publicar el comportamiento general mientras US2/US3 estén pendientes.

### Incremental Delivery

1. Base: matriz SQL y contrato compatibles con ausencia, sin cambios de datos existentes.
2. US1: alta individual; US2: conservación/transiciones; US3: importación y equivalencia.
3. Cierre: carrera de unicidad, suites, historia y aceptación completa en entorno aislado.
4. Despliegue posterior coordinado backend/frontend: un cliente que exige string no debe quedar
   expuesto a respuestas null sin adaptación. No revertir NOT NULL generando códigos ficticios.

### Trazabilidad de requisitos y resultados

| Requisito / resultado | Tareas principales |
| --- | --- |
| FR-001–FR-003; SC-001–SC-002 | T003, T005, T008–T014, T017, T025–T031 |
| FR-004–FR-005; SC-003 | T018–T024 |
| FR-006 | T003, T005, T008–T012, T025, T032 |
| FR-007 | T011, T018–T021, T029 |
| FR-008; SC-004 | T009, T019, T025–T031, T035 |
| FR-009–FR-010 | T009, T011, T019, T025–T026 |
| FR-011; SC-005 | T003, T015–T016, T023, T026–T027, T033–T036 |
| FR-012 | T018–T024 (incluida corrección explícita T019/T021), T025, T029 |
| AC1 / AC2 / AC3 / AC4 | T009 / T009 / T018–T024 / T018–T024 |

## Notes

- Total: 36 tareas; Setup 2, Foundational 5, US1 10, US2 7, US3 7, cierre 5.
- Las restricciones entre comillas se trasladan desde data-model.md para evitar decisiones
  implícitas durante implementación. Aplicar la matriz completa, no solo el ejemplo citado.
- No añadir extensiones PostgreSQL, cambios de ENUM, RLS ni frameworks por disponibilidad de
  una skill: esta feature solo requiere revisar CHECK, NULL, UNIQUE y preservación.
- Java: revisar Map.of/List.of, null en comparaciones, rollback y cierre de unicidad concurrente.
  Angular: mantener Signals/Signal Forms y helpers reutilizables; backend sigue siendo autoridad.
- Las casillas completadas se respaldan con las ejecuciones y evidencias registradas en quickstart.md.
