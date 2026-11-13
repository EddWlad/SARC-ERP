---
description: "Lista de tareas de Feature 008: intermitencias de puntos de carga KIA"
---

# Tasks: Intermitencias de puntos de carga KIA

**Input**: documentos de diseño en `specs/008-intermitencias-kia/`: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/) y [quickstart.md](quickstart.md).

**Tests**: se incluyen. El plan define una estrategia de pruebas obligatoria (plan.md, «Estrategia de pruebas»), y la constitución y las features anteriores la exigen. En cada historia, las pruebas se escriben primero y deben fallar antes de implementar.

**Organization**: las tareas se agrupan por historia de usuario para que cada una se pueda implementar y probar por separado.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos y sin dependencias pendientes).
- **[Story]**: la historia a la que pertenece (US1 a US6).

## Path Conventions

| Alias | Ruta |
|-------|------|
| `{BE}` | `sarc-bakend/src/main/java/com/tidsec/sarc_bakend` |
| `{IT}` | `{BE}/modules/intermittency` |
| `{BT}` | `sarc-bakend/src/test/java/com/tidsec/sarc_bakend` |
| `{ITT}` | `{BT}/modules/intermittency` |
| `{MIG}` | `sarc-bakend/src/main/resources/db/migration` |
| `{FE}` | `sarc-frontend/src/app` |
| `{FI}` | `{FE}/features/intermittency` |

Reglas que valen para todas las tareas:
- Repositorio público: ningún dato real, Código asignado real ni secreto en el código, los fixtures, los registros o los mensajes. Los fixtures usan códigos ficticios `9xxxxx` (research R15).
- No se leen `sarc-bakend/.env`, `documents/Usuario y contraseña de SARC_Servidor.txt` ni `documents/infraestructura-local/`.
- Las migraciones V001 a V017 no se tocan. V018 se ensaya en un PostgreSQL desechable, nunca sobre la base de compose. T006 pasó el 2026-11-13. Hasta publicar, V018 solo se aplica en bases desechables (Testcontainers o el PostgreSQL de ensayo): no se arranca el backend contra la base de compose ni contra otra base persistente, porque Flyway aplicaría V018 y ya no podría editarse (principio III). Desde ahora cualquier cambio a V018 va en una migración nueva (V019).
- La UI solo usa `design-system/_tokens.scss` y los estilos compartidos de `shared/ui`. No se crean colores ni estilos propios (FR-048).
- Al terminar una prueba local, se cierran los puertos 8080, 4200 y 5432 que se hayan abierto.

---

## Phase 1: Setup (infraestructura compartida)

**Purpose**: decisiones documentadas y datos de prueba sintéticos que usan todas las historias.

- [X] T001 [P] Redactar el ADR del módulo en `sarc-bakend/docs/adr/0013-modulo-intermitencias.md`. Debe cubrir: el módulo `intermittency`, el puerto `KiaPointDirectory` (y por qué no se amplía `EquipmentDirectory`), la exclusión `btree_gist` con su alternativa si la extensión no se puede crear (research R2), el bloqueo `pg_advisory_xact_lock` por equipo y la constante de permiso `AVAILABILITY`, separada de `EDITORS`. Seguir el formato de `0011-modulo-uptime.md`.
- [X] T002 [P] Crear `{BT}/support/Feature008Fixtures.java`, siguiendo el patrón de `Feature006Fixtures`. Debe sembrar en Testcontainers estos puntos de carga KIA, todos con códigos `9xxxxx` y nombres genéricos:
  - activos BLOQUEADO, activos LIBRE sin código, un punto DESCONECTADO, uno INACTIVO y uno ELIMINADO;
  - ningún par de equipos con el mismo Código asignado: V004 impone `UNIQUE (assigned_code)` y V008 no lo retira, así que `AMBIGUOUS_CODE` se prueba con un `KiaPointDirectory` simulado que devuelve dos puntos para un código (T038 y T044);
  - una electrolinera KIA y un punto JAC, para `NOT_KIA_CHARGING_POINT`;
  - un punto «PRUEBAS CDA».

  Debe incluir usuarios con estos roles: ADMINISTRADOR, ANALISTA, GERENTE, SUPERVISOR, ASISTENTE, y las combinaciones Analista+Gerente, Administrador+Asistente y Gerente+Supervisor+Asistente.
- [X] T003 [P] Crear `{ITT}/support/SyntheticOperationalLog.java`. Debe generar, a partir de las mismas filas, un XLSX (Apache POI, con horas como fracción del día y como celda con formato de hora), un CSV con `,`, un CSV con `;` y un texto pegado con tabuladores (con y sin fila de encabezado).

  Las filas reproducen las horas y los totales de las filas reales 3, 57, 61, 94, 99, 117, 155, 163, 228 y 240 (tabla de hallazgos de spec.md), con códigos `9xxxxx`. Se agregan también:
  - una fila con 7 pares de Inicio y Fin;
  - una fila con segundos ambiguos;
  - una fila con fecha `dd/MM/yyyy` y otra con fecha inválida;
  - una variante sin la columna «Código cargador», para `IMPORT_HEADER_MISSING`.

  No debe leer `documents/plantillas/`.

---

## Phase 2: Foundational (prerrequisitos bloqueantes)

**Purpose**: esquema, identidad de puntos, entidad de ventana, políticas de tiempo, permisos y la barrera de escritura que usan todas las historias.

**⚠️ CRITICAL**: ninguna historia puede empezar hasta terminar esta fase, salvo T006, que no bloqueaba las historias y se cerró el 2026-11-13 (ver T006).

### Backend

- [X] T004 Crear `{MIG}/V018__intermitencias_kia.sql` con `CREATE EXTENSION IF NOT EXISTS btree_gist;` y las seis tablas de data-model.md.
  - **`itm_window`** con las columnas de auditoría de `BaseAuditableEntity`:
    - `point_label VARCHAR(200) NOT NULL`;
    - `start_at TIMESTAMPTZ NOT NULL` con CHECK de segundos = 0;
    - `end_at TIMESTAMPTZ NULL`, con CHECK de segundos = 0 si existe;
    - `duration_minutes INTEGER NULL` con `CHECK ≥ 1` e igual a (end_at − start_at)/60;
    - `state VARCHAR(20) NOT NULL` limitado a OPEN, REVIEW, CONFIRMED, TEST o ANNULLED;
    - `review_reasons JSONB NOT NULL DEFAULT '[]'`;
    - `comment VARCHAR(500) NULL`;
    - `source VARCHAR(10) NOT NULL` limitado a MANUAL, XLSX, CSV o PASTE;
    - `batch_id`, `row_number` y `pair_number` NULL;
    - `current_revision INTEGER NOT NULL`;
    - una ventana OPEN no tiene `end_at`, y solo una OPEN o ANNULLED puede no tenerlo (una abierta que se anula conserva `end_at` NULL).
  - **Invariantes**:
    - `EXCLUDE USING gist (equipment_id WITH =, tstzrange(start_at, coalesce(end_at, 'infinity'), '[)') WITH &&) WHERE (state <> 'ANNULLED')`;
    - `UNIQUE (equipment_id, start_at, end_at) WHERE state <> 'ANNULLED'`;
    - índices `(equipment_id, start_at)`, `(start_at)`, `(end_at)` y `(state)`.
  - **`itm_window_revision`** con `UNIQUE (window_id, revision)` y `change` limitado a CREATED, CORRECTED, CLOSED, REVIEW_CONFIRMED, MARKED_TEST, UNMARKED_TEST, ANNULLED o REPLACED_BY_IMPORT. Lleva `justification VARCHAR(500)`, obligatoria salvo en CREATED y CLOSED (cerrar una ventana abierta es el flujo normal de captura), y `actor_id` y `at` NOT NULL.
  - **`itm_import_batch`**: las columnas de `BaseImportBatch`, como en `V013__uptime_disponibilidad.sql`, más `format`, `total_rows`, `total_windows`, los ocho conteos por clasificación y `earliest_day`/`latest_day DATE`.
  - **`itm_import_row`**: `UNIQUE (batch_id, row_number)`; `raw`, `edits` e `interpretation` JSONB; `equipment_id` NULL con `linked_by` y `linked_at`; `classification`; `decision` limitado a APPLY, SKIP o KEEP_PENDING; `skip_reason`; `resolved_by` y `resolved_at` NULL; `touched_days DATE[]`, con índice GIN; `version`.
  - **`itm_reviewed_day`**: `day DATE PK`, `active BOOLEAN NOT NULL`, `marked_by`, `marked_at` y `version`.
  - **`itm_reviewed_day_event`**: `action` limitado a MARKED o WITHDRAWN, `justification` obligatoria con WITHDRAWN, `actor_id`, `at` y `pending_batches_warned`.

  No hay `ALTER` sobre tablas existentes ni carga de datos.
- [X] T005 Crear `{ITT}/infrastructure/persistence/IntermittencySchemaTest.java`, siguiendo el patrón de `UptimeMigrationTest`. Debe comprobar sobre PostgreSQL 18 en Testcontainers:
  - V018 se aplica sobre V017;
  - existe la extensión `btree_gist`;
  - se rechaza un solapamiento entre dos estados no ANNULLED;
  - se admite un solapamiento con una ventana ANNULLED;
  - se rechazan un duplicado, una ventana OPEN con `end_at`, segundos distintos de cero y `duration_minutes = 0`;
  - las tablas existentes no cambian (se comparan columnas de `upt_*`, `cns_*` y `asset_*`).
- [X] T006 Lo antes posible después de T004 y T005, ensayar V018 en un PostgreSQL 18 desechable (nunca la base de compose), restaurado desde la última copia cifrada con el procedimiento de `docs/operacion/restaurar.md`. Comprobar que se puede crear `btree_gist` con el rol de la aplicación (que debe ser propietario de la base), que la migración tarda menos de 5 s y que las tablas existentes quedan sin cambios. Si `btree_gist` no está disponible, aplicar la alternativa de research R2 (editar V018 solo si nunca se aplicó en una base persistente; si no, crear V019) y actualizar el ADR 0013 antes de seguir construyendo sobre la restricción de exclusión. Registrar la evidencia, sin secretos, en quickstart.md. **Avance**: `IntermittencyMigrationRehearsalTest` ensayó V018 con un rol no superusuario en un PostgreSQL 18 desechable (menos de 5 s, `btree_gist` creada, tablas existentes intactas) y la restauración de un volcado con datos de intermitencias. **Cerrada el 2026-11-13**: se restauró la última copia cifrada de producción (V017, 29 tablas con el mismo conteo que sus metadatos) en un PostgreSQL 18 desechable con un rol de aplicación no superusuario propietario de su base; V018 se aplicó en menos de 1 s, creó `btree_gist` y las seis tablas `itm_`, no cambió ninguna columna existente y no alteró ningún conteo. Esta tarea **no bloqueaba** el desarrollo local de las historias, porque Testcontainers con PostgreSQL 18 ya trae `btree_gist`. **Sí bloquea** la primera versión candidata: T131 no puede empezar mientras esta tarea siga abierta, y todo cambio de V018 que resulte necesario después de las historias va en V019 (principio III). La copia y su clave las facilitó el responsable el 2026-11-13.
- [X] T007 [P] Crear `{BE}/shared/catalog/KiaPointReference.java`, un record con `id`, `name`, `assignedCode`, `usageStatus`, `adminStatus`, `inScope` y, desde US6, `createdAt` (fecha de alta), y `{BE}/shared/catalog/KiaPointDirectory.java` con `findByAssignedCodes(Collection<String>)` → `Map<String, List<KiaPointReference>>`, `findByIds(Collection<UUID>)`, `search(String text, int limit)`, `inScope(Instant at)` y, desde US6, `allKiaPoints()` (todos los puntos KIA, también inactivos y eliminados, para listar las exclusiones de la disponibilidad; research R1).
- [X] T008 Implementar `{BE}/modules/inventory/application/service/KiaPointDirectoryService.java`, de solo lectura, con el patrón de `EquipmentDirectoryService`:
  - `inScope` es `true` solo para la red KIA y el tipo CHARGING_POINT;
  - el código se compara en forma literal, sin espacios exteriores (política D04 de FR-003, equivalente a la normalización de D05 para el formato de seis dígitos de V008);
  - la búsqueda es por código o nombre, sin distinguir mayúsculas ni tildes;
  - los equipos ELIMINADO se devuelven con su `adminStatus`, para que Intermitencias clasifique `DELETED_EQUIPMENT`.

  Agregar la prueba `{BT}/modules/inventory/application/service/KiaPointDirectoryServiceTest.java`, que cubra códigos ambiguos, electrolineras, JAC, LIBRE sin código e INACTIVO.
- [X] T009 [P] Crear los enums de dominio en `{IT}/domain/model/window/`: `WindowState.java` (OPEN, REVIEW, CONFIRMED, TEST, ANNULLED), `WindowSource.java` (MANUAL, XLSX, CSV, PASTE), `WindowChange.java` (los 8 cambios de T004) y `ReviewReason.java` (solo LONG_WINDOW: `TOTAL_MISMATCH` es una observación de fila de importación, `IssueCode`, no una razón de la ventana).
- [X] T010 Crear las entidades JPA de dominio `{IT}/domain/model/window/IntermittencyWindow.java`, que extiende `BaseAuditableEntity`, y `{IT}/domain/model/window/WindowRevision.java`, con sus columnas en `updatable = false`, como `upt_measurement_revision`.
  - `IntermittencyWindow` no expone setters públicos. Expone métodos de transición que aplican la tabla «Transiciones de estado de una ventana» de data-model.md y devuelven la revisión creada.
  - `duration_minutes` se deriva siempre de los instantes.
  - `point_label` se fija al crear.
- [X] T011 Crear el puerto `{IT}/domain/repository/WindowRepository.java` y su implementación:
  - `{IT}/infrastructure/persistence/WindowJpaRepository.java`, `WindowRevisionJpaRepository.java` y `WindowRepositoryAdapter.java`;
  - consulta del rango `start_at < :to AND coalesce(end_at, :cutOff) > :from AND equipment_id IN (:ids)`;
  - búsqueda de ventanas no anuladas que se solapan o son idénticas para un punto;
  - `lockEquipment(UUID)`, con `pg_advisory_xact_lock(hashtext('itm:' || id))`.
- [X] T012 [P] Crear `{IT}/domain/policy/PeriodSlicer.java`, puro y sin Spring. Debe dividir una ventana `[inicio, fin)` por día local de `America/Guayaquil` y calcular su intersección con `[desde, hasta)`. Una ventana abierta usa `min(corte, hasta)`. Agregar `{ITT}/domain/policy/PeriodSlicerTest.java`, que cubra:
  - 23:50–00:20, con 10 + 20 minutos;
  - la fila 163, con 237 + 742 minutos;
  - el cruce de quincena 15→16, el fin de mes y un año bisiesto;
  - una ventana de 3 días;
  - una ventana abierta cortada en el corte.
- [X] T013 [P] Crear las políticas de `{IT}/domain/policy/`:
  - `FutureInstantPolicy.java`, que considera futuro un `instante > truncar_a_minuto(ahora) + 59 s`;
  - `LongWindowPolicy.java`, que marca REVIEW con `LONG_WINDOW` si `duration_minutes ≥ 240`;
  - `DurationText.java`, que da h:mm con horas acumuladas, por ejemplo 36:42.

  Agregar `{ITT}/domain/policy/WindowPoliciesTest.java`, que cubra el minuto en curso frente al siguiente, 239 frente a 240 minutos y 2.202 minutos = 36:42.
- [X] T014 [P] Crear `{IT}/application/service/IntermittencyPermissions.java` con `EDITORS = "hasAnyRole('ADMINISTRADOR','ANALISTA')"` y `AVAILABILITY` con la misma expresión, pero en una constante aparte. Crear también `{IT}/application/service/IntermittencyReadAccess.java` (en `application`, no en `infrastructure`: los servicios de `application` la usan y ArchUnit les prohíbe depender de `infrastructure`) con `canReadOperational()` y `canReadAvailability()`. Se evalúan con los roles vigentes de cada petición (FR-007) y siguen el patrón de `ConsumptionReadAccess` y `UptimeReadAccess`.
- [X] T015 Crear `{IT}/application/service/WindowWriteGuard.java`. Debe hacer lo siguiente:
  1. Bloquear los equipos en orden ascendente de `equipment_id` (research R7).
  2. Validar el alcance con `KiaPointDirectory` (`NOT_KIA_CHARGING_POINT`) y el instante futuro (`WINDOW_FUTURE_INSTANT`).
  3. Detectar duplicados (`WINDOW_DUPLICATE`, 409) y solapamientos (`WINDOW_OVERLAP`, 409) contra ventanas no anuladas.
  4. Traducir una violación de la exclusión o del índice único a esos mismos códigos.

  Usar `ConflictException` y `BusinessRuleException` de `{BE}/shared/error/`. Esta es la única vía de escritura de ventanas para captura, cierre, corrección e importación.
- [X] T016 [P] Ampliar `{BT}/architecture/ArchitectureTest.java`:
  - `modules.intermittency` no importa ninguna clase de otro `modules.*`; solo `shared.*` y los puertos de `shared.catalog`;
  - su `domain` no depende de Spring Web ni de la infraestructura;
  - `modules.uptime` no importa `modules.intermittency` ni al revés (FR-047).
- [X] T017 [P] Crear `{ITT}/api/IntermittencyApiSupport.java`, con el patrón de `UptimeApiSupport`: inicio de sesión por rol y por combinación (T002), aserciones de `problem+json` con `code` y ayudas para crear ventanas directamente por servicio.

### Frontend

- [X] T018 [P] Crear `{FI}/domain/intermittency-permissions.ts`, con `INTERMITTENCY_EDITORS` y `INTERMITTENCY_AVAILABILITY` como listas separadas (patrón de `features/uptime/domain/uptime-permissions.ts`), y su spec.
- [X] T019 [P] Crear `{FI}/domain/intermittency-time.ts` y su spec:
  - h:mm acumulado;
  - minutos;
  - rangos de período: quincenas `[1,16)` y `[16, día 1 del mes siguiente)`, últimos 15 días `[hoy−14, hoy+1)` y rango personalizado de hasta 366 días;
  - el rótulo «tipo · desde–hasta (fin exclusivo)».

  Usar la zona America/Guayaquil, como `features/uptime/domain/uptime-time.ts`.
- [X] T020 [P] Crear `{FI}/domain/intermittency-labels.ts` con etiquetas en español para los estados (Confirmada, Abierta, Por revisar, Pendiente de vincular, Prueba, Anulada), las clasificaciones de FR-025 (`IDENTICAL` se rotula «Duplicada (ya registrada)» y el código `DUPLICATE_IN_BATCH`, dentro de En conflicto, «Duplicada en el lote»), los códigos de motivo de data-model.md y las franjas («Menos de 5 min», «5 a 14 min», «15 a 59 min», «1 h a menos de 4 h», «4 h o más»). Agregar su spec.
- [X] T021 Crear el armazón de rutas:
  - `{FI}/intermittency.routes.ts`, con `tablero` (redirección por defecto), `ventanas`, `registrar`, `revision`, `dias-revisados`, `importaciones` e `importaciones/:id`. Todas salvo `tablero` y `ventanas` llevan `canActivate: [roleGuard(...INTERMITTENCY_EDITORS)]`, como `features/uptime/uptime.routes.ts`.
  - `{FI}/presentation/intermittency-page/intermittency-page.ts`, con pestañas de sección como `uptime-page`, usando `shared/ui/_section-tabs.scss`. Las pestañas Registrar, Revisión, Días revisados e Importaciones solo se muestran si el usuario tiene algún rol de `INTERMITTENCY_EDITORS`; Tablero y Ventanas se muestran a todos. La spec comprueba ambas vistas.
  - Registrar la ruta `intermitencias` en `{FE}/app.routes.ts`.
- [X] T022 Agregar la entrada `{ label: 'Intermitencias', path: '/intermitencias', icon: LucideZapOff.icon }` en `{FE}/layout/navigation.ts`, después de Uptime, y actualizar su spec si comprueba la lista.

**Checkpoint**: la base está lista; las historias pueden empezar.

---

## Phase 3: User Story 1 - Registrar manualmente las ventanas de un punto KIA (Priority: P1) 🎯 MVP

**Goal**: un editor elige un punto KIA y registra cualquier cantidad de ventanas del día (cerradas, que cruzan la medianoche o abiertas), ve su duración y el total diario, y puede cerrar después una ventana abierta.

**Independent Test**: registrar seis ventanas de un punto en un día, una que cruce la medianoche y otra abierta. Comprobar los minutos y el h:mm de cada una, el total diario de cada fecha y que la abierta no suma al tiempo confirmado. Escenarios 7, 12, 13 y 14 de quickstart.md.

### Tests for User Story 1 ⚠️

- [X] T023 [P] [US1] Crear `{ITT}/domain/model/window/WindowTransitionsTest.java`. Debe probar: creación CONFIRMED con menos de 240 minutos, REVIEW con 240 o más, OPEN sin fin, cierre de OPEN a CONFIRMED o REVIEW, fin anterior o igual al inicio rechazado y cierre con instante futuro rechazado.
- [X] T024 [P] [US1] Crear `{ITT}/api/WindowCaptureApiTest.java`. Debe probar:
  - 6 ventanas guardadas en una sola petición;
  - 23:50–00:20;
  - una ventana abierta y su cierre;
  - el cierre de una abierta de 4 h o más, que queda en REVIEW;
  - `WINDOW_OVERLAP`, `WINDOW_DUPLICATE`, `WINDOW_END_BEFORE_START` y `WINDOW_FUTURE_INSTANT`, aceptando el minuto en curso;
  - `WINDOW_OVERLAP` también contra ventanas REVIEW y TEST, y contra una OPEN: mientras está abierta, una ventana del mismo punto que empieza después de su inicio se rechaza; tras cerrarla con un fin anterior, se acepta; una ANNULLED no bloquea (FR-014);
  - `NOT_KIA_CHARGING_POINT` con una electrolinera y con JAC;
  - la atomicidad: si una ventana falla no se guarda ninguna, y la respuesta lista el error de cada una;
  - un INACTIVO con fecha pasada, aceptado con observación;
  - `GET /points` devuelve solo puntos KIA y responde 200 a todos los roles autenticados, porque lo usa el filtro del dashboard (FR-032);
  - 403 en `POST /windows` y `close` para Gerente, Supervisor, Asistente y su combinación.
- [X] T025 [P] [US1] Crear `{ITT}/api/WindowCaptureConcurrencyTest.java`: dos hilos crean ventanas solapadas del mismo punto; solo una se guarda y la otra recibe 409 `WINDOW_OVERLAP`.

### Implementation for User Story 1

- [X] T026 [P] [US1] Crear los comandos `{IT}/application/command/CaptureWindowsCommand.java` (equipmentId y una lista de `{startAt, endAt?, comment?}`, con `comment` de hasta 500 caracteres) y `CloseWindowCommand.java` (`version`, `endAt`).
- [X] T027 [US1] Crear `{IT}/application/service/WindowService.java` con `capture` y `close`, anotado con `@PreAuthorize(IntermittencyPermissions.EDITORS)` y `@Transactional`.
  - Usa `WindowWriteGuard`, `LongWindowPolicy` y `FutureInstantPolicy`.
  - Crea la revisión CREATED o CLOSED.
  - Registra `AuditTrail.record` sin secretos (FR-052).
  - `point_label` es el nombre y el código del punto en la fecha del hecho.
  - Devuelve las ventanas, su estado y el total por día local, calculado con `PeriodSlicer`.
- [X] T028 [P] [US1] Crear `{IT}/application/service/KiaPointQueryService.java`, con `search(text, limit)` sobre `KiaPointDirectory` que devuelve solo `inScope`. Sin `@PreAuthorize` de editores: lo puede usar cualquier usuario autenticado, porque también alimenta el filtro del dashboard (FR-032).
- [X] T029 [P] [US1] Crear los DTO en `{IT}/api/request/` (`CaptureWindowsRequest.java`, `CloseWindowRequest.java`) y `{IT}/api/response/` (`WindowResponse.java`, `CaptureResultResponse.java`, `KiaPointResponse.java`), con Jakarta Validation y `@Schema(nullable = true)` en los opcionales.
- [X] T030 [US1] Crear `{IT}/api/controller/IntermittencyWindowController.java` (`POST /api/v1/intermittency/windows` y `POST /windows/{id}/close`) e `IntermittencyPointController.java` (`GET /points`), según contracts/intermittency-api.md.
- [X] T031 [US1] Regenerar el contrato con `./mvnw.cmd test "-Dtest=OpenApiContractTest" "-Dopenapi.update=true"` en `sarc-bakend`. Después ejecutar `npm run api:types` en `sarc-frontend`.
- [X] T032 [P] [US1] Crear `{FI}/data-access/intermittency-api.ts` con `searchPoints`, `captureWindows` y `closeWindow`, tipados con los tipos generados.
- [X] T033 [US1] Crear `{FI}/application/capture-facade.ts` y su spec, con señales para el punto, la fecha, las filas de ventana (agregar y quitar sin límite), `endsNextDay`, el fin con fecha y hora completas, la duración en vivo de cada fila, el total del día, los errores por fila y «guardar y registrar otro» conservando la fecha (FR-019 a FR-021). Patrón: `features/uptime/application/capture-facade.ts`.
- [X] T034 [US1] Crear `{FI}/presentation/capture-page/capture-page.ts`, `.html` y `.scss`, con la misma estructura de formulario, campos de Angular Material, botones y estilos de lista que `features/uptime/presentation/capture-page`. Debe incluir:
  - un autocompletado de puntos por código o nombre;
  - filas dinámicas;
  - la marca «termina el día siguiente»;
  - mensajes en español junto a cada campo;
  - el total del día en h:mm y en minutos.

  Agregar su spec.
- [X] T035 [US1] Crear `{FI}/presentation/open-windows-panel/open-windows-panel.ts` (con `.html` y spec; las plantillas van en línea, como en Uptime), integrado en la página de captura. Lista las ventanas OPEN del punto elegido (con `GET /windows?equipmentIds&states=OPEN`, cuyo listado básico se adelantó a US1 en `WindowReadService.list`; T062 completa el detalle), con su duración provisional rotulada como provisional, y una acción «Registrar fin» (diálogo con fin, validación y 409 de concurrencia), que usa `shared/ui/confirm-dialog`.

**Checkpoint**: US1 funciona y se puede probar sola (es el MVP de captura).

---

## Phase 4: User Story 2 - Importar el registro operativo con vista previa corregible (Priority: P1)

**Goal**: cargar un XLSX, un CSV o un texto pegado; ver cada ventana interpretada con su clasificación y su motivo; editar en la vista previa; decidir y confirmar. Reimportar el mismo contenido no duplica nada.

**Independent Test**: importar el XLSX sintético (T003); comprobar las clasificaciones de las filas de hallazgos; corregir las filas 3, 61 y 228; confirmar; verificar los totales; reimportar y verificar que no se crea ninguna ventana. Escenarios 1 a 11 de quickstart.md.

### Tests for User Story 2 ⚠️

- [X] T036 [P] [US2] Crear `{ITT}/domain/policy/ImportRowInterpreterTest.java` con cada caso de contracts/import-format.md:
  - fila 3: `DUPLICATE_IN_BATCH`, 109 y 83 min, sin `TOTAL_MISMATCH` antes de decidir; después de OMIT, el total es 83 min y aparece `TOTAL_MISMATCH` (83 frente a 109);
  - fila 61: `END_BEFORE_START`;
  - fila 99: `TOTAL_MISMATCH`, 98 frente a 1.331;
  - fila 117: `INCOMPLETE_PAIR` que admite OPEN; con OPEN elegido y una fila del día siguiente del mismo punto, esa fila queda `OVERLAP_IN_BATCH`, y al retirar OPEN vuelve a NEW;
  - fila 163 con `endsNextDay`: 979 min con `LONG_WINDOW`;
  - fila 228: `OVERLAP_IN_BATCH`, y 47 min con MERGE;
  - 7 pares;
  - segundos con ±1 s y `SECONDS_AMBIGUOUS`;
  - fechas nativas, `dd/MM/yyyy`, `yyyy-MM-dd`, número de serie e inválida;
  - fin igual al inicio: `ZERO_DURATION`;
  - Total inactivo vacío: `TOTAL_MISSING`;
  - `FUTURE_INSTANT`;
  - orden de columnas libre;
  - archivo sin encabezado con el orden de la plantilla: 15 columnas (`k = 11`, impar) dan 5 pares y Total; 18 columnas (`k = 14`, par) dan 7 pares sin Total (`TOTAL_MISSING`); 4 columnas (`k = 0`) dan `IMPORT_HEADER_MISSING`.
- [X] T037 [P] [US2] Crear `{ITT}/infrastructure/parser/IntermittencyRowReaderTest.java`: la misma interpretación con XLSX (horas en fracción y en celda con formato de hora), CSV con `,`, CSV con `;` y texto pegado; solo la primera hoja (D09); y `IMPORT_HEADER_MISSING` nombrando la columna.
- [X] T038 [P] [US2] Crear `{ITT}/api/IntermittencyImportApiTest.java`. Debe probar:
  - carga multipart (202 PARSING → READY) y carga por pegado;
  - conteos por clasificación;
  - `PATCH` de fila con reinterpretación y reclasificación de las filas del mismo punto y día;
  - `edits` conserva el original, el autor y el momento;
  - vinculación explícita de un PENDING_LINK;
  - decisiones OMIT, MERGE, OPEN, KEEP_EXISTING, REPLACE (crea la revisión `REPLACED_BY_IMPORT` con justificación) y `acknowledgeTotal`;
  - una ventana marcada como prueba en la vista previa (`target:'test'`) se crea en estado TEST al confirmar y no cuenta en `/summary` oficial (FR-016);
  - marcar como prueba un par incompleto con decisión OPEN, o elegir OPEN para un par marcado como prueba, devuelve 422 `OPEN_TEST_NOT_ALLOWED`, en cualquier orden (FR-016);
  - `IMPORT_UNRESOLVED_ROWS`;
  - una fila con ventanas posteriores al inicio de una ventana OPEN guardada del mismo punto queda CONFLICT `OVERLAPS_EXISTING` (FR-014);
  - elegir OPEN en un par cuyo inicio es anterior a ventanas ya guardadas del mismo punto deja esa fila en CONFLICT `OVERLAPS_EXISTING` en la vista previa, y la confirmación nunca llega a la restricción SQL (FR-014);
  - la regla de instante futuro aplicada de nuevo al confirmar;
  - la reimportación idéntica, con cero ventanas y totales sin cambios (SC-004);
  - que la confirmación no anula ventanas ausentes del archivo;
  - descartar y reintentar;
  - el original guardado en `imports/intermittency` con su SHA-256, mediante un `FileStorage` simulado;
  - 403 para los roles de consulta.
- [X] T039 [P] [US2] Crear `{ITT}/api/IntermittencyImportConcurrencyTest.java`: dos ediciones de la misma fila con la misma `version`, donde la segunda recibe 409 `CONCURRENT_MODIFICATION`; y la confirmación de un lote mientras una captura manual escribe sobre el mismo punto, sin solapamientos.

### Implementation for User Story 2

- [X] T040 [P] [US2] Crear el modelo de importación en `{IT}/domain/model/importing/`:
  - `IntermittencyImportBatch.java`, que extiende `BaseImportBatch` y agrega `format`, los totales, los ocho conteos y `earliest_day`/`latest_day`;
  - `IntermittencyImportRow.java`, con `raw`, `edits` e `interpretation` JSONB, `equipment_id`, `linked_by/linked_at`, `classification`, `decision`, `skip_reason` (obligatorio con SKIP), `resolved_by/resolved_at`, `touched_days` y `version`;
  - el record `RowWindow.java` (`pair`, `startAt`, `endAt`, `minutes`, `classification`, `issues[]` y `decision`);
  - los enums `RowClassification.java` (INVALID > PENDING_LINK > OUT_OF_SCOPE > CONFLICT > REVIEW > IDENTICAL > NEW), `IssueCode.java` (todos los códigos de data-model.md), `RowDecision.java` (APPLY, SKIP, KEEP_PENDING) y `WindowResolution.java` (KEEP_EXISTING, REPLACE, MERGE, OMIT, OPEN).
- [X] T041 [P] [US2] Crear el puerto `{IT}/domain/repository/IntermittencyImportRepository.java` y su implementación en `{IT}/infrastructure/persistence/`: `IntermittencyImportBatchJpaRepository.java`, `IntermittencyImportRowJpaRepository.java` e `IntermittencyImportRepositoryAdapter.java`, con el patrón de `UptimeImportRepositoryAdapter`.
- [X] T042 [US2] Crear `{IT}/domain/policy/ImportRowInterpreter.java`, puro. Convierte una fila cruda y sus `edits` en ventanas interpretadas con sus observaciones, según contracts/import-format.md y research R4. Nunca suma un día de forma automática, y `endsNextDay` y `endAt` completo solo provienen de `edits` (FR-024, FR-026).
- [X] T043 [US2] Crear `{IT}/application/port/IntermittencyRowParser.java` y `{IT}/infrastructure/parser/IntermittencyRowReader.java`. Usan `TabularReader.read` y `readText`, normalizan los encabezados (sin tildes, sin distinguir mayúsculas, `^inicio (\d+)$` y `^fin (\d+)$`) y producen `raw` por columna. Si no hay encabezado, aplican el orden de la plantilla: con `k` columnas después de la cuarta, `k` impar significa que la última es Total inactivo; `k` par, que todas son pares; `k = 0`, `IMPORT_HEADER_MISSING` (contracts/import-format.md).
- [X] T044 [US2] Crear `{IT}/application/service/ImportClassifier.java`. Debe:
  - resolver los códigos con `KiaPointDirectory.findByAssignedCodes` según la tabla de research R3;
  - comparar con lo guardado (IDENTICAL o CONFLICT `OVERLAPS_EXISTING`, research R6);
  - comparar dentro del lote (`DUPLICATE_IN_BATCH` y `OVERLAP_IN_BATCH`), tratando una ventana con decisión `OPEN` como `[inicio, infinito)` frente a las demás del mismo punto, en el lote y ya guardadas, y reclasificar al tomar o retirar esa decisión (FR-014, regla 4 de contracts/import-format.md);
  - marcar REVIEW por `TOTAL_MISMATCH` (total de las ventanas válidas no omitidas frente a Total inactivo, recalculado tras cada edición o decisión, y además con y sin duplicado mientras haya idénticas sin resolver) o por `LONG_WINDOW`, y marcar FUTURE_INSTANT;
  - agregar las observaciones informativas `NAME_DIFFERS` e `INACTIVE_EQUIPMENT`;
  - calcular `touched_days` y la peor clasificación de la fila;
  - recalcular los conteos del lote.
- [X] T045 [US2] Crear `{IT}/application/service/IntermittencyImportProcessor.java`, con la interpretación inicial en segundo plano mediante `ImportJobs`, como `UptimeImportProcessor`. Crear también `{IT}/infrastructure/bootstrap/InterruptedIntermittencyImportRecovery.java`, que marca como FAILED reintentable los lotes en PARSING o CONFIRMING interrumpidos, igual que `InterruptedUptimeImportRecovery`.
- [X] T046 [US2] Crear `{IT}/application/service/IntermittencyImportService.java`, con `@PreAuthorize(EDITORS)`. Debe ofrecer:
  - `upload`, de hasta 20 MiB, con el original en `FileStorage` (carpeta `imports/intermittency`) y su huella `ContentFingerprint`;
  - `paste`, `list`, `get` y `rows` (filtro por clasificación y paginación);
  - `editRow`, con `requireReviewable`, la `version` optimista, el registro de la edición en `edits` y la reinterpretación y reclasificación síncronas (research R5);
  - `linkRow`, solo con un punto `inScope`;
  - `decide`, que rechaza con `OPEN_TEST_NOT_ALLOWED` la combinación OPEN con marca de prueba (la misma validación aplica en `editRow` cuando se marca la prueba sobre un par con OPEN);
  - `confirm`, que bloquea los equipos en orden ascendente, vuelve a validar el instante futuro y los conflictos, crea las ventanas con `WindowWriteGuard` (CONFIRMED, REVIEW, OPEN o, si se marcaron como prueba en la vista previa, TEST conservando sus `review_reasons`; siempre con `source`, `batch_id`, `row_number` y `pair_number`) y aplica REPLACE como revisión justificada;
  - `discard` y `retry`.

  Registrar la auditoría de la carga, las ediciones, las decisiones y la confirmación (FR-030, FR-052).
- [X] T047 [P] [US2] Crear los DTO en `{IT}/api/request/` (`PasteImportRequest.java`, `EditImportRowRequest.java`, `LinkImportRowRequest.java` y `ImportRowDecisionRequest.java`) y `{IT}/api/response/` (`IntermittencyImportBatchResponse.java` e `IntermittencyImportRowResponse.java`). Estos últimos llevan `raw`, `edits`, `interpretation`, `classification`, `issues` en español, `decision`, el archivo, la fila y el par.
- [X] T048 [US2] Crear `{IT}/api/controller/IntermittencyImportController.java` con todas las rutas de «Lotes de importación» de contracts/intermittency-api.md, salvo `pending-rows` y `resolve`, que van en US3.
- [X] T049 [US2] Regenerar OpenAPI (`OpenApiContractTest` con `-Dopenapi.update=true`) y ejecutar `npm run api:types`.
- [X] T050 [P] [US2] Crear `{FI}/data-access/intermittency-imports-api.ts` con la carga, el pegado, el historial, el lote, las filas, la edición, la vinculación, la decisión, la confirmación, el descarte y el reintento. Patrón: `features/uptime/data-access/uptime-imports-api.ts`.
- [X] T051 [US2] Crear `{FI}/application/import-history.ts` y `{FI}/application/import-batch-facade.ts`, con sus specs. Usan `shared/imports/import-batch-state.ts`, `import-history-state.ts`, `import-classification-filter.ts` y `transient-retry.ts`. Manejan las ediciones con `version`, los conteos actualizados después de cada `PATCH`, el bloqueo de la confirmación mientras haya filas sin resolver y los mensajes de conflicto 409.
- [X] T052 [US2] Crear `{FI}/presentation/imports-page/imports-page.ts` (con `.html` y spec), que reutiliza `shared/imports/import-dialog` para el archivo y el texto pegado, `import-status-badge` y el diseño de `features/uptime/presentation/imports-page`.
- [X] T053 [US2] Crear `{FI}/presentation/import-batch-page/import-batch-page.ts` (con `.html`, `.scss` y spec), que reutiliza `shared/imports/import-batch-header`, el filtro por clasificación e `import-confirm-dialog` con `import-confirm-summary`. La tabla muestra la fila, el par, el campo, el valor original, la interpretación (fecha, hora y minutos), la clasificación, el error y el motivo. En móvil usa tarjetas, como Consumo.
- [X] T054 [US2] Crear `{FI}/presentation/import-row-editor/import-row-editor.ts` (diálogo con `.html` y spec). Permite editar la fecha, el código o punto, el inicio, el fin, la marca de día siguiente, el fin con fecha y hora completas, el comentario y la marca de prueba, además de agregar u omitir pares. Muestra el antes y el después y la nueva clasificación tras volver a validar (FR-026).
- [X] T055 [US2] Crear `{FI}/presentation/row-decision-panel/row-decision-panel.ts` (con `.html` y spec). Por ventana ofrece OMIT, MERGE, KEEP_EXISTING, REPLACE con justificación y OPEN. OPEN se deshabilita en una ventana marcada como prueba, con el motivo «Una ventana de prueba debe estar cerrada», y la spec lo comprueba. Por fila, `acknowledgeTotal` (que muestra ambos totales), SKIP con motivo y KEEP_PENDING. Incluye el selector de punto KIA para vincular, que reutiliza `KiaPointQueryService` mediante `searchPoints`, y muestra el nombre del archivo solo como sugerencia.

**Checkpoint**: US1 y US2 funcionan cada una por su lado.

---

## Phase 5: User Story 3 - Revisar, corregir y conservar la trazabilidad (Priority: P1)

**Goal**: los editores revisan lo que está por revisar, lo abierto, lo de prueba y lo pendiente; corrigen, anulan, marcan o quitan la prueba y resuelven pendientes, siempre con justificación e historial. Todos consultan el historial confirmado.

**Independent Test**: corregir una ventana confirmada, anular otra, marcar un punto de prueba y resolver una fila pendiente de vincular de un lote confirmado. Comprobar el historial, los totales y la exclusión de la prueba. Escenarios 9, 10, 14b y 15 de quickstart.md.

### Tests for User Story 3 ⚠️

- [X] T056 [P] [US3] Crear `{ITT}/api/WindowCorrectionApiTest.java`. Debe probar:
  - una corrección con justificación conserva la revisión anterior (autor, momento, motivo y valores) y los totales usan la vigente;
  - una corrección que llega a 240 minutos o más vuelve a REVIEW;
  - `confirm-review` por el mismo autor;
  - `JUSTIFICATION_REQUIRED`;
  - anular sin borrado físico;
  - `test` y `untest`, incluido `test` sobre una ventana REVIEW de 240 minutos o más: pasa a TEST sin confirmarse antes, nunca suma en `/summary` oficial y al quitar la marca vuelve a REVIEW con su `LONG_WINDOW`;
  - `POST /points/{id}/test` y `/untest` sobre un rango: solo afectan las ventanas CONFIRMED o REVIEW que intersecan el rango, devuelven la cantidad afectada y la de OPEN sin marcar, y una ventana registrada después del rango queda CONFIRMED (no hay marca permanente del punto, FR-016);
  - `CONCURRENT_MODIFICATION`;
  - un equipo renombrado conserva `point_label` y la respuesta trae el nombre actual (US3-6);
  - 403 para los roles de consulta y su combinación (US3-7).
- [X] T057 [P] [US3] Crear `{ITT}/api/PendingRowResolutionApiTest.java`. Debe probar:
  - `GET /pending-rows` lista solo filas KEEP_PENDING de lotes COMPLETED;
  - `resolve` con vinculación y APPLY vuelve a validar duplicados y solapamientos y crea las ventanas;
  - SKIP con motivo;
  - quedan `resolved_by` y `resolved_at`;
  - no se puede resolver dos veces;
  - un lote READY rechaza `resolve`.
- [X] T058 [P] [US3] Crear `{ITT}/api/WindowHistoryReadTest.java`: quien no es editor recibe solo CONFIRMED, sin `reviewReasons`, procedencia ni `revisions`; en `GET /windows/{id}` de una ventana OPEN, REVIEW, TEST o ANNULLED recibe 404, con el mismo cuerpo que un id inexistente; un editor recibe todos los estados y las revisiones.

### Implementation for User Story 3

- [X] T059 [P] [US3] Crear los comandos `{IT}/application/command/CorrectWindowCommand.java` (`version`, `startAt?`, `endAt?`, `comment?`, `justification`), `JustifiedTransitionCommand.java` (`version`, `justification` de hasta 500 caracteres), `MarkPointTestCommand.java` (`equipmentId`, `from`, `to`, `justification`) y `ResolvePendingRowCommand.java`.
- [X] T060 [US3] Ampliar `{IT}/application/service/WindowService.java` con `correct`, `confirmReview`, `markTest`, `unmarkTest` y `annul`. Todas usan `WindowWriteGuard` y crean revisiones (CORRECTED, REVIEW_CONFIRMED, MARKED_TEST, UNMARKED_TEST, ANNULLED) con justificación obligatoria y auditoría. Agregar `markPointTest` y `unmarkPointTest`, operaciones en lote sobre un rango: la primera marca como TEST las ventanas CONFIRMED o REVIEW del punto que intersecan el rango (conservando los `review_reasons` de las REVIEW) e informa cuántas OPEN del rango quedaron sin marcar, y la segunda devuelve sus ventanas TEST a CONFIRMED o REVIEW, ambas con una justificación única y una revisión por ventana. No se guarda ninguna marca permanente del punto ni se modifica Inventario (FR-016).
- [X] T061 [US3] Agregar a `{IT}/application/service/IntermittencyImportService.java` `pendingRows(filter)` y `resolve(command)`. Este último solo acepta lotes COMPLETED con filas KEEP_PENDING sin `resolved_at`, hace la reinterpretación y la reclasificación de `ImportClassifier`, toma el bloqueo por equipo y crea las ventanas con `WindowWriteGuard`. Registra `resolved_by`/`resolved_at`, `edits` y la auditoría.
- [X] T062 [US3] Crear `{IT}/application/service/WindowReadService.java`. Ofrece la lista paginada por rango, puntos y estados, y el detalle con revisiones. La proyección por rol usa `IntermittencyReadAccess.canReadOperational()` (FR-005): quien no es editor solo ve CONFIRMED, y el detalle de una ventana en otro estado le responde 404 (`NotFoundException` de `{BE}/shared/error/`), como un id inexistente.
- [X] T063 [P] [US3] Crear los DTO `{IT}/api/request/CorrectWindowRequest.java`, `JustifiedVersionRequest.java`, `MarkPointTestRequest.java` y `ResolvePendingRowRequest.java`, y `{IT}/api/response/WindowDetailResponse.java` (con `revisions` nulo para quien no es editor), `WindowRevisionResponse.java`, `PendingRowResponse.java` y `PointTestResultResponse.java` (`affectedWindows` y `openWindowsNotMarked`, para `/points/{id}/test` y `/untest`).
- [X] T064 [US3] Agregar a `{IT}/api/controller/IntermittencyWindowController.java` `GET /windows`, `GET /windows/{id}`, `PATCH /windows/{id}`, `confirm-review`, `test`, `untest` y `annul`. Agregar `POST /points/{equipmentId}/test` y `POST /points/{equipmentId}/untest` a `IntermittencyPointController.java`, y `GET /pending-rows` y `POST /import-batches/{id}/rows/{rowId}/resolve` a `IntermittencyImportController.java`.
- [X] T065 [US3] Regenerar OpenAPI y ejecutar `npm run api:types`.
- [X] T066 [P] [US3] Agregar a `{FI}/data-access/intermittency-api.ts` e `intermittency-imports-api.ts` los métodos de lista, detalle, corrección, transiciones, prueba de punto, filas pendientes y resolución.
- [X] T067 [US3] Crear `{FI}/application/windows-facade.ts` y `{FI}/application/review-facade.ts`, con sus specs. Manejan los filtros, la paginación, las transiciones con justificación, los 409 y la recarga después de cada cambio.
- [X] T068 [P] [US3] Crear `{FI}/presentation/justification-dialog/justification-dialog.ts` (con `.html` y spec): campo obligatorio de hasta 500 caracteres, con el mismo diseño que `features/uptime/presentation/justification-dialog`.
- [X] T069 [US3] Crear `{FI}/presentation/windows-page/windows-page.ts` (con `.html`, `.scss` y spec). Incluye la lista con insignias de estado (`shared/ui/_badges.scss`), los filtros de rango y punto, la duración en h:mm y minutos, y el detalle con su historial de revisiones solo para editores. Usa `shared/ui/_list-page.scss`, con tarjetas en móvil.
- [X] T070 [US3] Crear `{FI}/presentation/review-page/review-page.ts` (con `.html`, `.scss` y spec), con las secciones Por revisar, Abiertas, Prueba y Pendientes de lotes (PENDING_LINK y CONFLICT). Las acciones son confirmar, corregir (`correction-form`), anular, marcar o quitar la prueba (por ventana, o por punto y rango; antes de confirmar, el diálogo cuenta con `GET /windows?equipmentIds&from&to&states=CONFIRMED,REVIEW,OPEN` las ventanas que se marcarán y las OPEN que quedarán sin marcar, y después muestra las cantidades que devuelve el servidor) y resolver la fila. Cada ventana larga muestra la duración junto a sus instantes completos (FR-021a).
- [X] T071 [US3] Crear `{FI}/presentation/correction-form/correction-form.ts` (con `.html` y spec), con inicio, fin con fecha y hora completas, comentario y justificación, y la duración recalculada en vivo.

**Checkpoint**: US1, US2 y US3 funcionan; las cifras oficiales solo usan datos confirmados.

---

## Phase 6: User Story 4 - Consultar el dashboard de intermitencias (Priority: P2)

**Goal**: un dashboard con los indicadores oficiales, lo que queda fuera de ellos, la tendencia diaria, el Top 10 por tiempo con empates y tres pasteles, para las cuatro opciones de período y el filtro por puntos.

**Independent Test**: con un conjunto calculado a mano (cruces de medianoche y quincena, abiertas, prueba, pendientes y empates), comparar cada indicador, el Top 10 y los gráficos en las cuatro opciones de período. Escenarios 14 y 16 de quickstart.md y SC-005.

### Tests for User Story 4 ⚠️

- [X] T072 [P] [US4] Crear `{ITT}/domain/policy/IntermittencyCalculatorTest.java`. Debe probar:
  - solo cuentan las CONFIRMED con porción mayor que 0;
  - cargadores afectados, ventanas, días-equipo, recurrentes con 3 días frente a 2, y el promedio con 1 decimal;
  - en el Top 10, A con 36:42 en 4 ventanas va por encima de B con 12 ventanas (CA-005-01);
  - tres empatados en el décimo puesto, con `extendedByTies` y el orden ventanas ↓, días ↓, nombre ↑;
  - menos de 10 afectados;
  - los límites de franja 4/5, 14/15, 59/60 y 239/240;
  - una ventana de 30 min que cruza la quincena va en la franja [15,60) en ambas;
  - cada pastel suma el total oficial;
  - en la serie diaria, 23:50–00:20 cuenta 1 ventana el día 15 y 1 el día 16, con `dailyWindowsMayExceedTotal = true`, mientras `official.windows = 1` (FR-035);
  - las ventanas OPEN, REVIEW y TEST quedan fuera de lo oficial y los minutos provisionales de OPEN van aparte.
- [X] T073 [P] [US4] Crear `{ITT}/domain/policy/PeriodPresetsTest.java`: FIRST_HALF y SECOND_HALF de febrero y de un mes de 31 días; LAST_15_DAYS; CUSTOM con 366 días aceptado y 367 rechazado (`PERIOD_TOO_LONG`); y el valor por defecto en la quincena en curso con corte hoy.
- [X] T074 [P] [US4] Crear `{ITT}/api/IntermittencySummaryApiTest.java`. Debe probar:
  - todos los roles autenticados reciben 200;
  - `outsideOfficial.details` solo llega a los editores;
  - la respuesta JSON no contiene ninguna clave de disponibilidad para ningún rol;
  - el filtro `equipmentIds`;
  - el período vacío devuelve conteos sin series falsas;
  - una corrección se refleja en la siguiente consulta, y `generatedAt` y `cutOff` están presentes;
  - las filas PENDING_LINK y CONFLICT de lotes confirmados se cuentan aparte.

### Implementation for User Story 4

- [X] T075 [P] [US4] Crear `{IT}/domain/policy/PeriodPresets.java` y `{IT}/application/command/IntermittencyQuery.java`, que normalizan el preset, el mes, `from/to`, `equipmentIds` y el corte.
- [X] T076 [P] [US4] Crear `{IT}/domain/policy/DurationBand.java`, con `of(totalMinutes)` y las franjas [0,5), [5,15), [15,60), [60,240) y [240,∞).
- [X] T077 [US4] Crear `{IT}/domain/policy/IntermittencyCalculator.java`, puro, según research R9. Calcula los indicadores oficiales, lo no oficial, el Top 10 con umbral y empates, la serie diaria (minutos por porción y ventanas contadas en cada día con porción mayor que 0, hasta el corte, con `dailyWindowsMayExceedTotal`), las franjas por porción, la concentración Top 10 frente al resto y la comparación entre recurrentes y no recurrentes. Crear también `{IT}/application/result/IntermittencySummary.java`.
- [X] T078 [US4] Crear `{IT}/application/service/IntermittencySummaryService.java`. Carga las ventanas del rango con `WindowRepository` y las filas pendientes de lotes confirmados con `IntermittencyImportRepository`, obtiene los nombres actuales con `KiaPointDirectory.findByIds` (D12), aplica `IntermittencyCalculator` y proyecta el detalle no oficial según `canReadOperational()`.
- [X] T079 [P] [US4] Crear `{IT}/api/request/IntermittencyFilterParams.java` y `{IT}/api/response/IntermittencySummaryResponse.java`, con los campos de la tabla de `/summary` de contracts/intermittency-api.md y sin ningún campo de disponibilidad.
- [X] T080 [US4] Crear `{IT}/api/controller/IntermittencyController.java` con `GET /api/v1/intermittency/summary`.
- [X] T081 [US4] Regenerar OpenAPI y ejecutar `npm run api:types`.
- [X] T082 [US4] Trasladar sin cambios de comportamiento `{FE}/features/consumption/presentation/share-chart/` a `{FE}/shared/ui/share-chart/` y `{FE}/features/consumption/presentation/top-equipment/` a `{FE}/shared/ui/top-equipment/`, con sus specs y estilos. Actualizar los imports de Consumo y comprobar que sus specs siguen pasando sin cambios de aserciones (research R12).
- [X] T083 [US4] Si `{FE}/shared/ui/top-equipment/` no admite filas extendidas por empate ni la nota «incluye empates», agregar un input opcional con valor por defecto igual al comportamiento actual de Consumo, con su spec. No se cambian los estilos.
- [X] T084 [P] [US4] Agregar `getSummary(query)` a `{FI}/data-access/intermittency-api.ts`.
- [X] T085 [US4] Crear `{FI}/application/dashboard-facade.ts` y su spec, con los filtros (preset, mes, rango y puntos), la petición, los estados de carga, vacío y error con reintento, y el rótulo de período y corte.
- [X] T086 [US4] Crear `{FI}/presentation/intermittency-filter-bar/intermittency-filter-bar.ts` (con `.html` y spec), con el mismo diseño de barra de filtros que `consumption-filter-bar` y `uptime-filter-bar`: selector de período con primera quincena, segunda quincena, últimos 15 días y personalizado, mes, rango y selección múltiple de puntos KIA.
- [X] T087 [US4] Crear `{FI}/presentation/dashboard-page/dashboard-page.ts` (con `.html`, `.scss` y spec), con `shared/ui/_dashboard.scss`. Contiene:
  - las tarjetas de FR-033;
  - el panel «Fuera de los indicadores oficiales», con cantidades y minutos provisionales rotulados;
  - la línea de horas diarias y las barras de ventanas diarias con `shared/ui/chart/chart-view`, siguiendo «Energía diaria» de Consumo, con la nota «Una ventana que cruza la medianoche cuenta en cada día que toca» cuando `dailyWindowsMayExceedTotal`;
  - los tres pasteles con `shared/ui/share-chart`, con título, unidad, leyenda textual y porcentaje;
  - el Top 10 con `shared/ui/top-equipment`;
  - el estado vacío «No hay intermitencias confirmadas en el período», sin gráficos en cero;
  - la fecha y hora de cálculo.

  Usar el diseño responsive de Consumo, con `handset`.

**Checkpoint**: US4 funciona con datos de US1 a US3 o del fixture.

---

## Phase 7: User Story 5 - Descargar informes gerenciales PDF y Excel (Priority: P2)

**Goal**: un PDF corporativo y un Excel con los mismos filtros y cifras del dashboard, archivados y con las restricciones por rol.

**Independent Test**: para varias selecciones de período y punto, descargar el PDF y el Excel y cotejar el 100 % de las cifras con el dashboard del mismo corte (SC-006). Escenario 19 de quickstart.md.

### Tests for User Story 5 ⚠️

- [X] T088 [P] [US5] Crear `{ITT}/infrastructure/report/IntermittencyPdfRendererTest.java`. Debe comprobar las secciones 1 a 8 de contracts/reports.md (logos, rango, zona, corte, resumen ejecutivo, tarjetas, tendencia, Top 10 con «incluye empates», repartos como barras de participación con leyenda, metodología y fuera de oficiales con su «Total por revisar» y los rótulos del dashboard), la ausencia de UID/PUK y de URL OCPP, y el texto vacío. Usar las muestras de `{ITT}/infrastructure/report/IntermittencyReportSamples.java`.
- [X] T089 [P] [US5] Crear `{ITT}/infrastructure/report/IntermittencyExcelRendererTest.java`. Debe comprobar las hojas Resumen, Por cargador (con el «% de días del período» sobre los días calendario del rango hasta el día del corte, incluido hoy, y sobre todos los días en un período cerrado), Ventanas (solo CONFIRMED, con «minutos dentro del rango» y franja) y Fuera de oficiales (solo cantidades y la fila «Total por revisar», igual al del dashboard, para quien no es editor), y que la suma de la hoja Ventanas sea igual al total de Resumen y de cada cargador.
- [X] T090 [P] [US5] Crear `{ITT}/api/IntermittencyReportConsistencyTest.java`: con iguales filtros y corte, `/summary`, el PDF (texto extraído) y el Excel coinciden en todas las cifras comparadas (SC-006).
- [X] T091 [P] [US5] Crear `{ITT}/api/IntermittencyReportPrivacyTest.java`. Debe comprobar:
  - quien no es editor recibe un informe `OFFICIAL_ONLY` sin detalle operativo;
  - un editor recibe uno `WITH_OPERATIONS`;
  - la descarga de un archivado `WITH_OPERATIONS` por un rol de consulta devuelve 403 `REPORT_OPERATIONAL_CONTENT_RESTRICTED`, con la opción de emitir uno nuevo;
  - un filtro con equipos que no son puntos KIA en alcance (una electrolinera, un punto JAC) nunca los nombra ni muestra su código, ni en el documento ni en el `scope` archivado: salen como «no disponible» (FR-038);
  - las correcciones posteriores no modifican el archivado y se conservan la huella y `criteria` (`from`, `to`, `equipmentIds`, `periodPreset` y `timeZone`).
- [X] T092 [P] [US5] Ampliar la prueba existente de `ReportPublication` (o crear `{BT}/shared/report/ReportPublicationTest.java`): `periodPreset` válido e inválido, `equipmentIds` aceptado, y `PERIOD_TYPES` de Uptime sin cambios. Ampliar `{BT}/modules/reporting/api/IssuedReportAccessApiTest.java` con un informe `INTERMITTENCY` restringido.

### Implementation for User Story 5

- [X] T093 [US5] Ampliar de forma aditiva `{BE}/shared/report/ReportPublication.java`: agregar `periodPreset` y `equipmentIds` a `KEYS`, y validar `periodPreset` contra `Set.of("FIRST_HALF","SECOND_HALF","LAST_15_DAYS","CUSTOM")` en una constante propia, sin tocar `PERIOD_TYPES`.
- [X] T094 [US5] Ampliar `{BE}/modules/reporting/application/service/IssuedReportService.java` (línea de la condición `"CONSUMPTION".equals(report.getKind())`) para que la verificación de contenido restringido también aplique a `"INTERMITTENCY"`. Ajustar del mismo modo `{BE}/modules/reporting/api/response/IssuedReportResponse.java` si deriva `restricted` por tipo. La lista de tipos restringibles vive en `{BE}/modules/reporting/domain/model/IssuedReportKinds.java` (CONSUMPTION e INTERMITTENCY), usada por ambas clases.
- [X] T095 [P] [US5] Crear `{IT}/application/port/IntermittencyReportRenderer.java` y `{IT}/application/result/IntermittencyReport.java` (el resumen, el detalle por cargador, las ventanas con su porción y franja, lo no oficial según el permiso y el corte).
- [X] T096 [P] [US5] Crear `{IT}/infrastructure/report/IntermittencyLabels.java`, `IntermittencyReports.java` (formato de instantes y posiciones con empates, compartido por el PDF y el Excel) e `IntermittencyPdfCharts.java`, este último con el mismo enfoque que `UptimePdfCharts` y `ConsumptionPdfCharts`: línea, barras y, para los tres repartos, barras de participación con leyenda y porcentaje, porque `CorporatePdf` no dibuja gráficos circulares (CC-PRD-008, aclaración 16).
- [X] T097 [US5] Crear `{IT}/infrastructure/report/IntermittencyPdfRenderer.java` sobre `CorporatePdf` y `CorporateBranding`, con las secciones 1 a 8 de contracts/reports.md. El resumen ejecutivo sigue el modelo de la plantilla y usa h:mm acumulado.
- [X] T098 [US5] Crear `{IT}/infrastructure/report/IntermittencyExcelRenderer.java` sobre `CorporateWorkbook`, con las hojas Resumen, Por cargador, Ventanas y Fuera de oficiales de contracts/reports.md. El «% de días del período» es días afectados ÷ días calendario de `[desde, min(hasta, día del corte + 1))` (FR-039).
- [X] T099 [US5] Crear `{IT}/application/service/IntermittencyReportService.java`. Reutiliza `IntermittencySummaryService` con la misma `IntermittencyQuery`, emite el archivo y lo archiva con `ReportArchive.archive(IssuedReportFile(kind="INTERMITTENCY", …))`, con `profile` `WITH_OPERATIONS` u `OFFICIAL_ONLY` según el contenido. Registra la auditoría de la emisión sin valores (FR-040, FR-052). Rechaza con 422 `REPORT_TOO_LARGE` un informe de más de 50.000 ventanas confirmadas (FR-041a),. `{BT}/modules/intermittency/application/service/IntermittencyReportServiceTest.java` prueba el límite exacto de `requireWithinLimit` y que un informe rechazado no se renderiza ni se archiva.
- [X] T100 [US5] Agregar `GET /api/v1/intermittency/report?format=PDF|XLSX` a `{IT}/api/controller/IntermittencyController.java`.
- [X] T101 [US5] Regenerar OpenAPI y ejecutar `npm run api:types`.
- [X] T102 [P] [US5] Agregar `'INTERMITTENCY'` a `ReportKind` y la etiqueta `INTERMITTENCY: 'Intermitencias'` en `{FE}/features/reports/domain/issued-report.ts`, y actualizar `{FE}/features/reports/application/issued-reports-facade.spec.ts`.
- [X] T103 [US5] Agregar las acciones PDF y Excel al encabezado de `{FI}/presentation/dashboard-page/dashboard-page.html`, con el mismo componente y estilo de encabezado que el dashboard de Consumo y `shared/reports/report-format.ts`. Cada acción descarga con los filtros vigentes, muestra su estado de carga y su error, y no envía correo (FR-041). Agregar `downloadReport` a `{FI}/data-access/intermittency-api.ts` y a `dashboard-facade.ts`, con su spec.

**Checkpoint**: US4 y US5 coinciden en las cifras.

---

## Phase 8: User Story 6 - Explorar la disponibilidad estimada de prueba (Priority: P3)

**Goal**: marcar días revisados para la red KIA y ver, solo con el permiso D, la disponibilidad estimada por punto y agregada, con su cobertura, sus exclusiones, los días bloqueados y sus limitaciones, también en el PDF y el Excel.

**Independent Test**: días marcados con ventanas y sin ellas, días sin marca, un evento abierto y una fila pendiente de vincular. Verificar el 95,00 % y el 99,50 %, que «Sin datos» nunca aparezca como 100 % y que ningún rol sin Analista ni Administrador reciba el indicador. Escenarios 17 y 18 de quickstart.md y la matriz de permisos.

### Tests for User Story 6 ⚠️

- [X] T104 [P] [US6] Crear `{ITT}/domain/policy/AvailabilityEstimatorTest.java`. Debe probar:
  - 10 puntos, 1 día marcado y 72 minutos: 95,00 %, 100,00 % para los otros nueve y 99,50 % agregado (14.328/14.400), con cobertura de 1/1;
  - un día sin marca no suma tiempo evaluado;
  - un punto sin días cubiertos queda en «Sin datos» (`percent = null`), al final de la lista;
  - hoy y los días futuros no se cubren ni cuentan en `coverage.daysInScope`: a mitad de quincena, con todos los días terminados marcados, la cobertura es del 100 %;
  - una fila PENDING_LINK de un lote confirmado bloquea el día para todos los puntos;
  - una fila CONFLICT de un lote confirmado bloquea solo su punto-día;
  - REVIEW excluye el punto-día; una OPEN iniciada hace 3 días excluye su punto en cada día desde su inicio hasta el corte, y al cerrarla esos días se evalúan con la duración definitiva (FR-045);
  - TEST no descuenta;
  - DESCONECTADO queda excluido;
  - un INACTIVO con ventanas queda incluido y uno sin ventanas, excluido;
  - el orden de menor a mayor, y a igualdad, más minutos fuera de línea y luego el nombre;
  - HALF_UP a 2 decimales;
  - el agregado se pondera por minutos y no promedia porcentajes.
- [X] T105 [P] [US6] Crear `{ITT}/api/ReviewedDayApiTest.java`. Debe probar:
  - marcar ayer;
  - hoy devuelve 422 `DAY_NOT_FINISHED`;
  - `warnings` cuando hay lotes READY que tocan el día;
  - el retiro con justificación obligatoria y su historial `itm_reviewed_day_event`;
  - marcar de nuevo después de retirar;
  - una ventana registrada después en un día revisado reduce la disponibilidad sin quitar la marca;
  - `CONCURRENT_MODIFICATION`;
  - 403 para los roles de consulta.
- [X] T106 [P] [US6] Crear `{ITT}/api/AvailabilityAccessTest.java`. Debe probar:
  - `GET /availability` devuelve 200 para Administrador, Analista, Analista+Gerente y Administrador+Asistente;
  - devuelve 403 `AVAILABILITY_RESTRICTED` para Gerente, Supervisor, Asistente y Gerente+Supervisor+Asistente;
  - quitar el rol Analista hace que la siguiente petición reciba 403 (FR-007);
  - ningún parámetro de `/summary` expone la disponibilidad.
- [X] T107 [P] [US6] Ampliar `{ITT}/infrastructure/report/IntermittencyPdfRendererTest.java`, `IntermittencyExcelRendererTest.java` e `IntermittencyReportPrivacyTest.java`:
  - el apartado 9 del PDF y la hoja «Disponibilidad» solo aparecen si quien emite tiene el permiso D;
  - el informe se archiva `WITH_OPERATIONS`;
  - la descarga del archivado por un rol de consulta devuelve 403;
  - el apartado no se mezcla con el Top 10 ni con el detalle por cargador.

### Implementation for User Story 6

- [X] T108 [P] [US6] Crear `{IT}/domain/model/coverage/ReviewedDay.java` (`day DATE PK`, `active BOOLEAN NOT NULL`, `marked_by`, `marked_at` y `version`; solo se marca si `day < hoy` local) y `ReviewedDayEvent.java` (MARKED o WITHDRAWN, con `justification` obligatoria en WITHDRAWN y `pending_batches_warned`).
- [X] T109 [P] [US6] Crear el puerto `{IT}/domain/repository/ReviewedDayRepository.java` y su implementación `{IT}/infrastructure/persistence/ReviewedDayJpaRepository.java`, `ReviewedDayEventJpaRepository.java` y `ReviewedDayRepositoryAdapter.java`, con bloqueo consultivo por día.
- [X] T110 [US6] Crear `{IT}/application/service/ReviewedDayService.java` con `@PreAuthorize(EDITORS)`:
  - `list(from, to)`, con `pendingLinkRows` y `unconfirmedBatches` por día;
  - `mark(day)`, con advertencias de lotes READY cuyo rango `earliest_day`–`latest_day` o `touched_days` toca el día;
  - `withdraw(day, version, justification)`.

  Registra la auditoría de cada acción.
- [X] T111 [US6] Crear `{IT}/domain/policy/AvailabilityEstimator.java`, puro, según research R9 y FR-042 a FR-046a: alcance y exclusiones, el primer día completo en que existía cada punto (`existsFrom`, aclaración 20), días candidatos, días bloqueados para la red y por punto, tiempo evaluado `1440 × díasCubiertos`, fuera de línea igual a la porción CONFIRMED en esos días, porcentaje con `BigDecimal` y HALF_UP, agregado ponderado, cobertura de la red y por punto (días en alcance = días del rango ya terminados hasta el corte, sin hoy ni futuros), y orden de la tabla. Crear también `{IT}/application/result/AvailabilityEstimate.java`.
- [X] T112 [US6] Crear `{IT}/application/service/AvailabilityService.java` con `@PreAuthorize(IntermittencyPermissions.AVAILABILITY)`. Usa `KiaPointDirectory.allKiaPoints`, `WindowRepository`, `ReviewedDayRepository` y las filas bloqueantes de `IntermittencyImportRepository` (`findBlockingRows`). `GET /availability` convierte la denegación de `@PreAuthorize(AVAILABILITY)` en 403 `AVAILABILITY_RESTRICTED`. Agrega los rótulos (`estimated`, `test` y el origen «Registro manual de intermitencias (Kibana)») y las limitaciones de FR-046. Nunca registra valores en los logs.
- [X] T113 [P] [US6] Crear los DTO (más `ReviewedDayMarkResponse`, que lleva el día y los lotes sin confirmar de los que avisar, y `ReviewedDayView` en `result`) `{IT}/api/request/WithdrawReviewedDayRequest.java`, `{IT}/api/response/ReviewedDayResponse.java` y `AvailabilityResponse.java` (con `aggregate`, `coverage`, `blockedDays`, `points` con `percent` nulo para «Sin datos», `exclusions`, `labels` y `limitations`).
- [X] T114 [US6] Crear `{IT}/api/controller/ReviewedDayController.java` (`GET /reviewed-days`, `PUT /reviewed-days/{day}` y `POST /reviewed-days/{day}/withdraw`) y agregar `GET /availability` a `IntermittencyController.java`.
- [X] T115 [US6] Ampliar `{IT}/application/service/IntermittencyReportService.java`, `IntermittencyPdfRenderer.java` (apartado 9) e `IntermittencyExcelRenderer.java` (hoja «Disponibilidad»): la disponibilidad solo se calcula e incluye si `IntermittencyReadAccess.canReadAvailability()` en el momento de emitir, y en ese caso `profile = WITH_OPERATIONS`.
- [X] T116 [US6] Regenerar OpenAPI y ejecutar `npm run api:types`.
- [X] T117 [P] [US6] Agregar a `{FI}/data-access/intermittency-api.ts` los métodos `getReviewedDays`, `markDay`, `withdrawDay` y `getAvailability`.
- [X] T118 [US6] Crear `{FI}/application/reviewed-days-facade.ts` y `{FI}/application/availability-facade.ts`, con sus specs. La petición de disponibilidad solo se crea si el usuario tiene `INTERMITTENCY_AVAILABILITY`.
- [X] T119 [US6] Crear `{FI}/presentation/reviewed-days-panel/reviewed-days-panel.ts` (con `.html`, `.scss` y spec), en la ruta `dias-revisados`. Lista los días con su estado, autor, filas pendientes y lotes sin confirmar; muestra por defecto los últimos 31 días y un selector de mes para marcar el histórico (aclaración 21), ofrece la acción de marcar (deshabilitada para hoy y los días futuros), un diálogo de aviso de lotes sin confirmar y el retiro con `justification-dialog`.
- [X] T120 [US6] Crear `{FI}/presentation/availability-section/availability-section.ts` (con `.html`, `.scss` y spec), montado en `dashboard-page` solo con el permiso, en una sección propia rotulada «Disponibilidad estimada (prueba)». Muestra el KPI agregado con su cobertura, la tabla de menor a mayor con «Sin datos» al final, las exclusiones con su motivo, los días bloqueados con su contador de pendientes y la nota de origen y limitaciones. Usa las tarjetas y tablas existentes y no se presenta como uptime. La spec comprueba que el componente no se monta ni hace la petición sin el permiso.

**Checkpoint**: las seis historias funcionan cada una por su lado.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: matriz de seguridad completa, regresiones, rendimiento, revisión visual, ensayo de restauración, documentación y publicación. El ensayo de la migración se hizo antes, en T006 (cerrada el 2026-11-13).

- [X] T121 [P] Crear `{ITT}/api/IntermittencySecurityTest.java`, con la matriz de los 5 roles y las 3 combinaciones de T002 contra **cada** endpoint de contracts/intermittency-api.md y la descarga de informes archivados (SC-007). Incluye el acceso directo sin interfaz.
- [X] T122 [P] Crear `{BT}/Feature008RegressionBaseline.java`, siguiendo `Feature005ConsumptionBaseline`. Con la base de Consumo y Uptime sembrada, sus cifras de dashboard, rankings, coberturas e informes deben ser idénticas antes y después de crear ventanas, lotes y días revisados (FR-047, FR-050, SC-008, SC-012).
- [X] T123 [P] Crear `{ITT}/api/Feature008PerformanceBench.java`, siguiendo `Feature006PerformanceBench`: vista previa de 248 filas sintéticas en menos de 30 s (SC-002), `/summary` quincenal en menos de 2 s con 6.000 ventanas, y captura en menos de 1 s.
- [X] T124 Crear `{BT}/Feature008BrowserSandbox.java`, siguiendo `Feature006BrowserSandbox`: Testcontainers, `RANDOM_PORT`, `FileStorage` simulado, generación del proxy de Angular y de los archivos sintéticos, y espera hasta un archivo `.done`.
- [X] T125 Ejecutar la revisión en el navegador según quickstart.md, «Comprobación en el navegador»:
  - las 7 rutas a 360, 390, 768, 1024 y 1440 píxeles, en tema claro y oscuro;
  - el teclado, el foco visible, los nombres accesibles, el contraste (4,5:1 en texto y 3:1 en componentes y gráficos) y la alternativa textual de los gráficos (WCAG 2.2 AA, FR-049), con una pasada de AXE por ruta y tema sin violaciones. Parte ya está automatizada: `npm run lint` aplica `angular.configs.templateAccessibility` a todas las plantillas, y algunos specs comprueban nombres accesibles y leyendas textuales, sin cubrir todas las pantallas. Lo que solo se ve en el navegador (contraste real, foco, AXE) queda en esta tarea y no se da por cubierto antes;
  - la comparación visual con Consumo, sin colores, componentes ni estilos ajenos (SC-011);
  - la visibilidad de la disponibilidad por rol;
  - un recorrido cronometrado: registrar un punto con 5 ventanas en menos de 2 minutos y con 10 en menos de 3 (SC-001), marcar un día como revisado en menos de 30 segundos (SC-008) y distinguir en menos de 10 segundos las cifras oficiales, las provisionales y el rango (SC-010).

  Guardar la evidencia en `specs/008-intermitencias-kia/quickstart.md` y cerrar los puertos al terminar. **Hecho salvo el recorrido cronometrado con una persona** (SC-001, SC-008 y SC-010), que queda como verificación humana pendiente en el quickstart.
- [X] T126 Ejecutar las puertas completas y registrar los resultados en quickstart.md: `./mvnw.cmd test` (con `OpenApiContractTest` sin actualizar), y en el frontend `npm run api:types`, `npm test -- --watch=false`, `npm run lint`, `npm run format:check` y `npm run build` (*bundle* inicial por debajo de 500 kB).
- [X] T127 Ensayar la restauración completa con datos sintéticos de intermitencias (ventanas, revisiones, lotes, filas y días revisados) y los originales importados (FR-051). **Depende** de la exportación de Cloudinary pendiente de Feature 007 (T083 de esa feature). Registrar el resultado en `docs/operacion/pendientes-recursos-reales.md`. **Avance**: la parte de base de datos se ensayó con datos sintéticos en `IntermittencyMigrationRehearsalTest` (mismos conteos por tabla tras `pg_dump` y `pg_restore`); **Cerrada el 2026-11-13**: un original sintético con el prefijo `imports/intermittency/` se subió a una carpeta propia de Cloudinary (`sarc-qa008-t127`, vacía antes y después), `Exportar-Cloudinary.ps1` lo incluyó en el índice con el mismo SHA-256 y la comprobación de referencias del ensayo de restauración lo encontró. Además `Ensayar-Restauracion.ps1` ahora cuenta `itm_import_batch` entre los historiales de importación y contrasta sus `original_ref` con la exportación (antes no los miraba). Sigue abierta en la Feature 007 la clave de API dedicada a la exportación (T083), que hay que crear en la consola de Cloudinary antes de la primera exportación real de `sarc-prod`.
- [X] T128 [P] Actualizar la documentación del proyecto, si la tienen: la sección de módulos o arquitectura de `sarc-bakend` (README o CLAUDE.md) con el módulo `intermittency` y el puerto `KiaPointDirectory`, y `docs/operacion/` con la carpeta `imports/intermittency` en el respaldo.
- [X] T129 **Decisión del responsable, no automatizable**: decidir si se agregan a `.gitignore` `documents/plantillas/Intermitencia_copiamos_nube.xlsx` e `Informe_Intermitencias_*.xlsx`, que contienen Códigos asignados reales (research R15). Sin esa decisión, no se versionan.
- [X] T130 Al cerrar, actualizar el estado de CC-PRD-008-20261110 en `documents/control-cambios-prd.md` y `specs/008-intermitencias-kia/checklists/requirements.md`.
- [ ] T131 Publicar con el flujo de Feature 007 **solo cuando el responsable lo autorice y T006 esté cerrada** (V018 ensayada sobre una copia restaurada): respaldo previo con recibo, `ci.yml`, `version-candidata.yml` y `publicar.yml` con la aprobación de `production`. Después, verificar con la cuenta de verificación (no Administrador) que `/intermitencias` abre vacío sin errores. No se hacen push ni commits sin una solicitud explícita.

---

## Cobertura de requisitos

| Requisito | Tareas |
|-----------|--------|
| FR-001 a FR-003 (alcance, identidad, vinculación) | T008, T010, T015, T024, T044, T055, T056 |
| FR-004, FR-005 (edición y detalle operativo) | T014, T021, T024, T038, T056, T058, T062, T074, T078, T121 |
| FR-006, FR-007 (disponibilidad restringida, roles vigentes) | T014, T106, T107, T112, T115, T120, T121 |
| FR-008 a FR-011 (ventana, duración, sin límite, estados) | T004, T009, T010, T013, T023, T024, T027, T033, T034, T036 |
| FR-012, FR-013 (abiertas, porciones) | T012, T023, T027, T035, T077 |
| FR-014, FR-015 (solapamiento, duplicado) | T004, T005, T015, T024, T025, T044 |
| FR-016 a FR-018 (prueba, historial, concurrencia) | T010, T038, T039, T046, T055, T056, T060, T064, T070 |
| FR-019 a FR-021b (captura, ventana larga, instante futuro) | T013, T015, T023, T024, T027, T028, T033, T034, T038, T046, T070 |
| FR-022 a FR-027 (formatos, columnas, interpretación, vista previa, totales) | T036, T037, T042, T043, T044, T046, T053, T054, T055 |
| FR-028 a FR-030 (confirmación, reimportación, lote) | T038, T046, T057, T061 |
| FR-031 a FR-037 (dashboard) | T072, T073, T074, T075, T076, T077, T078, T083, T086, T087 |
| FR-038 a FR-041a (informes y su tope) | T088, T089, T090, T091, T094, T097, T098, T099, T103 |
| FR-042 a FR-046a (disponibilidad estimada) | T104, T105, T107, T110, T111, T112, T115, T119, T120 |
| FR-047 (aislamiento de Uptime) | T016, T122 |
| FR-048, FR-049 (armonía visual, accesibilidad: lint de plantillas y specs automatizados; AXE, contraste y foco en el navegador) | T021, T034, T053, T069, T087, T120, T125 |
| FR-050 (aditiva, migración, publicación) | T004, T005, T006, T122, T131 |
| FR-051 (respaldo y restauración; la clave de exportación real sigue en T083 de la Feature 007, registrado en Assumptions de la spec) | T127 |
| FR-052 (auditoría) | T027, T046, T060, T099, T110 |
| SC-001 | T125 (recorrido cronometrado) y T123 (latencia de captura) |
| SC-002 | T123 |
| SC-003, SC-004 | T036, T038 |
| SC-005 | T012, T072 |
| SC-006 | T090 |
| SC-007 | T106, T121 |
| SC-008 | T104, T122, T125 |
| SC-009 | T056 |
| SC-010, SC-011 | T087, T125 |
| SC-012 | T122, T126, T131 |

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no depende de nada.
- **Foundational (Phase 2)**: depende de Setup (T002 y T003 para las pruebas). **Bloquea todas las historias**, salvo T006. T006, el ensayo de V018 y de `btree_gist` sobre una copia restaurada, se cerró el 2026-11-13 sin necesidad de la alternativa de research R2; ya no bloquea la primera versión candidata.
- **US1, US2 y US3 (P1)**: dependen solo de Foundational. US3 usa las ventanas que crean US1 o US2 en sus pruebas, pero las crea con `IntermittencyApiSupport` (T017), así que no las necesita terminadas. La parte de resolución pendiente de US3 (T061) amplía `IntermittencyImportService` de US2, así que T061 depende de T046.
- **US4 (P2)**: depende de Foundational. T078 lee `IntermittencyImportRepository` (T041) para los conteos pendientes.
- **US5 (P2)**: depende de US4 (T077 y T078), porque reutiliza el mismo cálculo.
- **US6 (P3)**: depende de Foundational y de T041 (filas bloqueantes). T115 depende de US5 (T097 a T099). T120 se monta en `dashboard-page` (T087).
- **Polish (Phase 9)**: depende de las historias que se quieran entregar. T127 se ensayó con un original sintético; la exportación real de `sarc-prod` sigue en T083 de la Feature 007.

### Archivos compartidos (no paralelizar entre tareas que los tocan)

- `{IT}/application/service/WindowService.java`: T027 y T060.
- `{IT}/application/service/IntermittencyImportService.java`: T046 y T061.
- `{IT}/api/controller/IntermittencyController.java`: T080, T100 y T114.
- `{IT}/api/controller/IntermittencyWindowController.java`: T030 y T064.
- `{IT}/api/controller/IntermittencyImportController.java`: T048 y T064.
- `{IT}/application/service/IntermittencyReportService.java`, `IntermittencyPdfRenderer.java` e `IntermittencyExcelRenderer.java`: T097 a T099 y T115.
- `{FI}/presentation/dashboard-page/`: T087, T103 y T120.
- `{FI}/data-access/intermittency-api.ts`: T032, T066, T084, T103 y T117.
- `sarc-bakend/docs/api/openapi.json`: T031, T049, T065, T081, T101 y T116, cada una después de los controladores de su historia.

### Within Each User Story

- Las pruebas se escriben primero y fallan.
- Modelo, luego servicio, luego endpoint, luego OpenAPI, y por último el frontend: data-access, facade y presentación.
- Cada historia se cierra en su checkpoint antes de pasar a la siguiente prioridad.

### Parallel Opportunities

- Setup: T001, T002 y T003.
- Foundational: T007, T009, T012, T013, T014, T016 y T017 en paralelo; luego T008, T010 y T011; luego T015. El frontend (T018, T019 y T020) puede ir en paralelo con todo el backend.
- Después de Foundational, US1, US2 y la parte de backend de US4 pueden avanzar en paralelo si hay capacidad.

---

## Parallel Example: User Story 2

```bash
# Pruebas de US2 a la vez:
Task: "ImportRowInterpreterTest en {ITT}/domain/policy/ImportRowInterpreterTest.java"
Task: "IntermittencyRowReaderTest en {ITT}/infrastructure/parser/IntermittencyRowReaderTest.java"
Task: "IntermittencyImportApiTest en {ITT}/api/IntermittencyImportApiTest.java"
Task: "IntermittencyImportConcurrencyTest en {ITT}/api/IntermittencyImportConcurrencyTest.java"

# Modelo y persistencia a la vez:
Task: "Modelo de importación en {IT}/domain/model/importing/"
Task: "Repositorio de importación en {IT}/infrastructure/persistence/"
```

## Parallel Example: User Story 6

```bash
Task: "AvailabilityEstimatorTest en {ITT}/domain/policy/AvailabilityEstimatorTest.java"
Task: "ReviewedDayApiTest en {ITT}/api/ReviewedDayApiTest.java"
Task: "AvailabilityAccessTest en {ITT}/api/AvailabilityAccessTest.java"
Task: "ReviewedDay y ReviewedDayEvent en {IT}/domain/model/coverage/"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (Setup) y Phase 2 (Foundational).
2. Phase 3 (US1): captura manual con validaciones, cierre de abiertas y protección contra solapamientos.
3. **Detenerse y validar**: escenarios 7, 12, 13 y 14 de quickstart.md, y la concurrencia de solapamientos de T025. El escenario 15 (edición concurrente) se valida con US3.
4. Se puede publicar oculto o completo según decida el responsable, siempre con el flujo de Feature 007.

### Incremental Delivery

1. Setup y Foundational: la base está lista. T006 se cerró el 2026-11-13: V018 se ensayó sobre la última copia restaurada antes de la primera versión candidata.
2. US1: captura (MVP).
3. US2: importación del Excel operativo, que reemplaza el flujo actual.
4. US3: revisión, correcciones y trazabilidad.
5. US4 y US5: dashboard e informes gerenciales.
6. US6: disponibilidad estimada de prueba.
7. Polish: matriz de seguridad, regresiones, rendimiento, navegador y restauración.

Cada incremento conserva intactos Consumo, Inventario, Uptime, autenticación e informes (`Feature008RegressionBaseline`).

---

## Notes

- `[P]` significa archivos distintos y sin dependencias pendientes. `[USn]` traza la tarea hasta la historia.
- Las justificaciones son obligatorias y de hasta 500 caracteres en todos los cambios, salvo la creación.
- Las tareas no autorizan commits, push, despliegues ni cambios en datos de producción. Esas acciones requieren una solicitud explícita del responsable.
