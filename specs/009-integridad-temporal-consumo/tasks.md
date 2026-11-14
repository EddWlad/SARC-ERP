---
description: "Tareas de implementación de la Feature 009"
---

# Tasks: Integridad temporal, gestión de sesiones y estabilidad visual

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contratos](contracts/consumo.md) y [quickstart.md](quickstart.md).

**Pruebas**: Los escenarios de aceptación de la spec y la guía de validación justifican las pruebas de cada historia. Añadir los casos antes de implementar el cambio y comprobar que detectan la diferencia esperada. Ejecutarlos de nuevo al cerrar la historia.

**Formato**: `[ID] [P?] [US?] Descripción y rutas`. `[P]` identifica tareas independientes dentro de su grupo, con los prerrequisitos de fase ya completados. Las rutas son relativas a la raíz y los archivos nuevos se indican como tales.

## Phase 1: Setup (Shared Infrastructure)

**Propósito**: Confirmar una base aislada y coordinar la integración de la Feature 009.

- [X] T001 Confirmar un checkout aislado para la implementación, registrar el SHA de referencia, la versión de Feature 008 que se usará para regresión, el estado de la migración V018 y los archivos compartidos que requieren coordinación. Documentar en `specs/009-integridad-temporal-consumo/evidence/integration-baseline.md` (nuevo) y verificar la selección temporal mediante `.specify/scripts/powershell/check-prerequisites.ps1` con `SPECIFY_FEATURE_NO_PERSIST=1`.
- [X] T002 Ejecutar la referencia de Consumo con la guía de `specs/009-integridad-temporal-consumo/quickstart.md` y los comandos vigentes de `.github/workflows/ci.yml`. Guardar los resultados base y las limitaciones de entorno en `specs/009-integridad-temporal-consumo/evidence/integration-baseline.md`.

## Phase 2: Foundational (Blocking Prerequisites)

**Propósito**: Cumplir la gobernanza documental y preparar la evolución de datos compartida por las historias.

- [X] T003 Registrar la decisión A y el alcance de las acciones de sesión en `documents/control-cambios-prd.md`. Conciliar PRD, TRD y Feature 002 con la matriz de `specs/009-integridad-temporal-consumo/plan.md` antes de implementar el cambio afectado. Registrar el impacto y las referencias que requieran actualización. Incluir la excepción histórica de FR-006a y la autorización de FR-008 en el alcance documental.
- [X] T004 Añadir casos de actualización desde el esquema anterior en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/architecture/MigrationUpgradeTest.java` y `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionSchemaTest.java`. Verificar que la migración no reclasifica filas antiguas, admite campos opcionales para registros anteriores y conserva identidades, relaciones de revisión e índices.
- [X] T005 Crear `V020__consumo_acciones_y_fin_de_origen.sql` (V019 la usó el hotfix): desplazamiento del fin (`source_end_utc_offset_minutes`, nulo = mismo que el inicio) en `energy_session` y `energy_import_row`; baja lógica (`deleted_at`, `deleted_by`, `deletion_reason`) en `energy_session`; versión esperada de la sesión en `energy_import_row`; tipo, actor y motivo en `energy_session_revision` con lote opcional. Registros anteriores legibles con campos nuevos nulos o por defecto. Validar con T004. Sin constancias de aprobación (US2 cerrada).
- [X] T006 Extender `SessionRevision` y su repositorio para tres tipos de revisión (importación, edición, baja) con datos anteriores, actor, instante y motivo. Las de importación conservan el lote; las de edición y baja no tienen lote.

**Checkpoint**: La evolución de esquema pasa la prueba de actualización y la evidencia original permanece disponible. Las fases de historia usan esta base.

## Phase H: Hotfix de producción — fecha de origen Enerlink (subconjunto de US1)

**Propósito**: Corregir en producción el desplazamiento al día anterior de las sesiones Enerlink que inician entre 00:00 y 05:00 UTC, sin esperar a US2–US4. Decisión del responsable (2026-11-13): una migración recalcula el histórico, con diagnóstico previo sobre una copia restaurada de producción, y la lista y el Excel muestran la hora de la fuente.

**Causa confirmada**: `EnergySession.apply` calculaba `start_date` con `America/Guayaquil` para toda fuente. El parser Enerlink interpretaba bien `UTC+00:00`, pero la atribución convertía a Ecuador (FR-002, FR-006).

**Alcance**: FR-002 (fecha y hora declaradas), FR-005/FR-006 (atribución por la fuente en histórico, filtros, tablero, PDF y Excel) y FR-022 (regresiones). Fuera de alcance: zona distinta por extremo, desglose «Fecha operativa heredada» (FR-006a), US2–US4. Se retoman en sus fases.

- [x] H001 [US1] Pruebas que fallan con el código vigente: parser Enerlink con `UTC+00:00` y `UTC-05:00` conserva el desplazamiento; `EnergySession` atribuye la sesión 1212 al 11/11 y Wallbox `2026-11-10 23:59:11` al 10/11; consulta del 11/11 incluye 1211 y 1212 y el 10/11 no las incluye.
- [x] H002 [US1] Migración `V019__consumo_desplazamiento_origen.sql` (prueba `SourceOffsetMigrationTest`): columna `source_utc_offset_minutes` (INTEGER) en `energy_session` y `energy_import_row`; rellenar Wallbox con −300 y Enerlink según el encabezado de su lote (H006: 0 solo para los seis lotes cuyo archivo declara `UTC+00:00`, −300 para el resto) y recalcular `start_date` desde `started_at` con ese desplazamiento.
- [x] H003 [US1] `SessionReading`, `TabularSessionParser`, `ImportRow` y `EnergySession` transportan el desplazamiento de origen y calculan `start_date` con él. `sameAs` lo compara para que una reimportación con otro desplazamiento requiera aprobación.
- [x] H004 [US1] API (`SessionResponse.sourceUtcOffsetMinutes`), Excel y lista Angular muestran inicio y fin con el desplazamiento de origen e incluyen segundos donde ya se muestran detalles. Regenerar OpenAPI y tipos.
- [x] H005 Ejecutar `./mvnw test`, `npm test` y `npm run build`; registrar resultados en `specs/009-integridad-temporal-consumo/evidence/hotfix.md` (nuevo).
- [x] H006 Diagnóstico de solo lectura sobre una copia restaurada de producción y sus archivos originales: confirmar que cada lote Enerlink declara `UTC+00:00`, contar sesiones y kWh que cambian de día por mes, y ensayar V019 sobre la copia. Si algún lote declara otro desplazamiento, ajustar H002 antes de publicar.
- [x] H007 **[Publicada el 2026-11-14 00:57 UTC: versión `2026.11.14-41-793e92f`, ejecución 4 del flujo `publicar`, resultado `COMPLETADA`; ver «Publicación» en evidence/hotfix.md]** Publicar con el flujo de Feature 007 (respaldo previo, versión candidata) y verificar en producción el reparto diario de un rango con sesiones afectadas.

**Checkpoint**: Con la fixture 1212/Wallbox el 10/11 suma 5 kWh y el 11/11 8 kWh en histórico, tablero, PDF y Excel; V019 ensayada sobre copia de producción.

## Phase 3: User Story 1 — Consultar Consumo con la fecha de la fuente (P1, MVP)

**Objetivo**: Atribuir y mostrar sesiones con la política de cada fuente en el histórico, los filtros y las exportaciones.

**Prueba independiente**: Con Enerlink 1212 y Wallbox, obtener 5 kWh el 10/11, 8 kWh el 11/11 y 13 kWh para ambos días en todas las superficies. Filtrar Enerlink conserva su fecha del 11/11. En el cambio de mes Ecuador/UTC se conserva el rango inicial de D13.

### Tests for User Story 1

- [X] T007 [P] [US1] Extender `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/parser/SessionParsersTest.java` con UTC+00:00, UTC-05:00, otro desplazamiento admitido, zonas ausentes o mal declaradas, fin al día siguiente y regresión Wallbox. Comprobar los instantes y datos originales de Enerlink 1212 y 1211. Afirmar Inicio ausente → Ecuador aplicado, Fin ausente → zona resuelta de Inicio, dos desplazamientos distintos válidos → resolver cada extremo y comprobar orden por instante, y declaración inválida → fila inválida sin incorporación, según los ejemplos de `specs/009-integridad-temporal-consumo/quickstart.md`.
- [X] T008 [P] [US1] Añadir en `ConsumptionApiTest` límites de día y de mes `[from, to)` con sesiones Enerlink UTC y Wallbox, y el filtro de fuente que no mueve fechas. En `ConsumptionCalculatorTest`, tendencia, conteo y ranking con el conjunto 5/8/13 kWh, distinguiendo cero de energía desconocida.
- [X] T009 [P] [US1] Probar en `ConsumptionPdfRendererTest` y `ConsumptionExcelRendererTest` el rótulo «Fecha de inicio según la fuente», el corte propio de cada emisión, la hora de la fuente con su desplazamiento en Excel y la presentación vigente de Wallbox.
- [X] T010 [P] [US1] Probar D13 en `period-selection.spec.ts` y, en `sessions-page.spec.ts`, la hora de la fuente con segundos y etiqueta UTC, y el fin con su propio desplazamiento, sin depender de la zona del navegador.

### Implementation for User Story 1

- [X] T011 [US1] `SessionReading`, `ImportRow` y `EnergySession` guardan el desplazamiento del fin cuando difiere del inicio. El instante, la fila y la referencia de lote se conservan. (El desplazamiento del inicio y la fecha de atribución ya los hizo H003.)
- [X] T012 [US1] `EnerlinkSessionParser` y `TabularSessionParser` resuelven la zona de cada extremo (TERMINO con su encabezado; si no lo declara, la del inicio) e invalidan las filas cuando un encabezado declara un desplazamiento ilegible o fuera de rango, en vez de caer en Ecuador. Wallbox sin cambios. `sameAs` compara también el desplazamiento del fin.
- [X] T013 [US1] Hecha en H003–H004: las consultas filtran por `start_date` (fecha de la fuente) con `[from, to)`, y `SessionResponse` expone el desplazamiento. Se agrega el del fin en T011/T015.
- [X] T014 [US1] PDF: rotular «Fecha de inicio según la fuente» en el consolidado diario, igual que el Excel (hecho en H004). Mantener corte, snapshot y `X-Report-Cut-Off`.
- [X] T015 [US1] API y Angular: `sourceEndUtcOffsetMinutes` en `SessionResponse` e `ImportRowResponse`; la lista, la tarjeta, la vista previa y el diálogo muestran el fin con su desplazamiento. Regenerar OpenAPI y tipos. El dashboard muestra su hora de cálculo si aún no lo hace.
- [X] T016 [US1] Ejecutar T007–T010 y registrar en `evidence/us1-validation.md` (nuevo) la concordancia de histórico, dashboard, PDF y Excel con el conjunto 5/8/13 kWh y la conservación de D13.

**Checkpoint**: US1 se demuestra en datos sintéticos con ambas plataformas y un corte común. La publicación del histórico requiere el control de cobertura de US2.

## Phase 4: User Story 2 — Auditar y corregir el histórico (P1) — CERRADA POR H006–H007

**Aclaración 2026-11-13**: el responsable decidió no construir el subsistema genérico de diagnóstico, propuestas y constancias. H006 auditó los 24 lotes Enerlink de producción contra sus archivos originales, H007 aplicó la corrección con copia previa y aprobación explícita, y producción quedó sin sesiones sin evidencia. Ver `evidence/hotfix.md`.

- [X] T017 [US2] Superada por `SourceOffsetMigrationTest` y el ensayo H006 (clasificación por lote, conservación de sesiones y kWh, cero escrituras fuera de los lotes UTC).
- [X] T018 [US2] Superada: V019 no toca `report_issued` ni los artefactos archivados; las emisiones posteriores usan su propio corte (cubierto por las pruebas vigentes de Reporting).
- [X] T019 [US2] Superada por H006 (lectura del encabezado de cada original y clasificación por lote).
- [X] T020 [US2] Superada por H006 (impacto por día, mes y fuente presentado al responsable antes de publicar).
- [X] T021 [US2] Superada por V019 y H007 (aplicación idempotente por Flyway con copia previa `sarc-20261114T0056Z-previa`).
- [X] T022 [US2] Superada por H006–H007 (ensayo sobre copia aislada y verificación en producción).

## Phase 5: User Story 3 — Ver, editar y eliminar lógicamente una sesión (P1)

**Objetivo**: Ofrecer acciones por sesión con permisos, historial y protección ante reimportaciones.

**Prueba independiente**: Como editor, ver, editar y dar de baja una sesión. Como usuario de consulta, verificar ausencia de privilegios de escritura. Reimportar lecturas iguales y distintas y confirmar que se preserva la decisión anterior.

### Tests for User Story 3

- [X] T023 [P] [US3] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionSessionActionsApiTest.java` para detalle por rol, acceso directo, redacción de evidencia, edición, baja y concurrencia. Incluir motivo obligatorio, estados D10, nueva vinculación validada, contraste entre energía desconocida y cero, y respuestas 403, 404 y 409.
- [X] T024 [P] [US3] Ampliar `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionApiTest.java` para reimportación idéntica y discrepante, lotes preparados antes de una edición o baja, lotes antiguos sin versión, y protección de comandos de prueba, aprobación y enlace. Verificar exclusión de bajas en las consultas oficiales y preservación de identidad y auditoría.
- [X] T025 [P] [US3] Ampliar `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.spec.ts` con acciones según rol, confirmación, cancelación, conservación de contexto y conflicto al guardar. Cubrir la tabla y la tarjeta móvil.

### Implementation for User Story 3

- [X] T026 [US3] Implementar transiciones de edición y baja en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/session/EnergySession.java` y reutilizar `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/policy/SessionRules.java`. Mantener «kWh normalizado `HALF_UP` a tres decimales, con `null` distinto de cero», «Marca con instante, actor y motivo en su revisión» y «Se conserva el estado de calidad previo». Revalidar `fin >= inicio`, vínculo y fuente preferente, aplicar D10 y mantener `version` optimista y `revision` histórica.
- [X] T027 [US3] Extender `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/SessionService.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/SessionQueryService.java` con detalle autorizado, edición y baja con versión y motivo. Conservar identidad, evidencia y revisión en la misma transacción. Proteger de nuevas mutaciones una baja desde las operaciones vigentes y resolver equipo mediante el puerto de Inventario.
- [X] T028 [US3] Excluir bajas en el predicado común de `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/SessionPredicates.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java`. Mantener «El lookup por identidad incluye las bajas» y resguardar conciliaciones operativas. Comprobar el mismo criterio para histórico, agregados y exportaciones.
- [X] T029 [US3] Proteger `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ImportProcessor.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/batch/ImportRow.java` en previsualización, confirmación y reintento. Comparar estado, versión y procedencia, reclasificar filas obsoletas, excluir bajas de la aplicación y exigir decisión explícita para discrepancias. Conservar procesamiento de filas nuevas no conflictivas.
- [X] T030 [US3] Exponer detalle, `PATCH` y acción de baja según `specs/009-integridad-temporal-consumo/contracts/consumo.md` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/controller/EnergySessionController.java`. Definir entradas en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/request/EditEnergySessionRequest.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/request/DeleteEnergySessionRequest.java` y el detalle autorizado en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/response/SessionDetailResponse.java` (nuevos). Validar los campos y la semántica ausente/nulo, usar el manejo de errores vigente y sincronizar `sarc-bakend/docs/api/openapi.json` y `sarc-frontend/src/app/core/api/generated/types.gen.ts` tras verificar el estado integrado.
- [X] T031 [US3] Añadir métodos y estado de las acciones en `sarc-frontend/src/app/features/consumption/data-access/consumption-api.ts` y `sarc-frontend/src/app/features/consumption/application/sessions-facade.ts`. Actualizar la consulta vigente tras una mutación y mantener edición, versión y filtro al tratar errores. Exponer datos de auditoría solo cuando los entrega el servidor.
- [X] T032 [US3] Integrar «Ver», «Editar» y «Eliminar» en `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.ts`, `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.html` y `sarc-frontend/src/app/features/consumption/presentation/session-action-dialog/session-action-dialog.ts` (nuevo). Reutilizar patrones de diálogo, motivos y accesibilidad de Consumo e Inventario. Validar teclado, confirmación y temas.
- [X] T033 [US3] Ejecutar T023–T025 y los recorridos de `specs/009-integridad-temporal-consumo/quickstart.md`. Guardar la matriz de permisos, evidencia de baja y reimportación y validación de tabla y tarjeta móvil en `specs/009-integridad-temporal-consumo/evidence/us3-session-actions.md` (nuevo). Repetir el escenario de cortes de T016 guardando el cambio de 8 a 9 kWh mediante la acción Editar de US3. Verificar el corte posterior, los 14 kWh y la revisión registrada.

**Checkpoint**: Una baja no reaparece al reimportar y las solicitudes de edición sobre datos obsoletos no pisan revisiones nuevas.

## Phase 6: User Story 4 — Trabajar con vistas estables (P2)

**Objetivo**: Diagnosticar y corregir el parpadeo verificable manteniendo los refrescos legítimos y el contexto del usuario.

**Prueba independiente**: Repetir el recorrido antes y después con respuestas iguales, nuevas, tardías y fallidas, y con regreso a la pestaña. Confirmar estabilidad de gráficos y filas y conservación de foco, scroll y edición.

### Tests and diagnosis for User Story 4

- [X] T034 [US4] Reproducir el síntoma en un sandbox aislado según `specs/009-integridad-temporal-consumo/quickstart.md`. Registrar en `specs/009-integridad-temporal-consumo/evidence/us4-baseline.md` (nuevo) condiciones, red, navegación, identidad de componentes y gráficos, animación y vista afectada. Distinguir hallazgos confirmados de casos sin reproducción.
- [X] T035 [P] [US4] Agregar casos de recarga con igual clave, datos nuevos, error, cambio de filtro y respuesta tardía en `sarc-frontend/src/app/features/consumption/application/consumption-dashboard-facade.spec.ts` (nuevo) y `sarc-frontend/src/app/features/consumption/application/sessions-facade.spec.ts`. Verificar que el refresco conserva el corte por inactividad.
- [X] T036 [P] [US4] Agregar regresiones para la causa observada en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.spec.ts` y `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.spec.ts`. Verificar estabilidad de identidad, foco y contexto, y cubrir el ciclo de Chart.js en `sarc-frontend/src/app/shared/ui/chart/chart-view.spec.ts` si el diagnóstico exige cambiarlo y se coordinó la integración.

### Implementation for User Story 4

- [X] T037 [US4] Ajustar el manejo de refresco para la causa verificada en `sarc-frontend/src/app/features/consumption/application/consumption-dashboard-facade.ts`, `sarc-frontend/src/app/features/consumption/application/sessions-facade.ts` y sus plantillas en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.html` y `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.html`. Conservar resultados compatibles con la misma consulta, descartar respuestas tardías y mostrar estados veraces para una clave nueva. Mantener navegación, filtros, edición e inactividad.
- [X] T038 [US4] **[Sin cambio: la evidencia de T034 no muestra animación visible con datos equivalentes; ver evidence/us4-results.md]** Estabilizar las entradas de gráfico y las actualizaciones de Chart.js cuando la evidencia lo justifique en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.ts`. Mantener un cambio local a Consumo cuando cubra el caso. Si la causa exige tocar `sarc-frontend/src/app/shared/ui/chart/chart-view.ts` u otras vistas, coordinar primero con Feature 008 y verificar a ambos consumidores.
- [X] T039 [US4] Repetir la reproducción de T034 y ejecutar T035–T036. Documentar en `specs/009-integridad-temporal-consumo/evidence/us4-results.md` (nuevo) el antes/después, recargas con datos nuevos y equivalentes y revisión a 360, 768 y 1440 px en ambos temas. Dejar pendiente SC-006 si el síntoma original no pudo reproducirse.

**Checkpoint**: SC-006 se acredita con evidencia del mismo recorrido antes y después. La UI se valida por teclado, móvil, tablet y escritorio.

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T040 Ejecutar las verificaciones integradas de `.github/workflows/ci.yml` y `specs/009-integridad-temporal-consumo/quickstart.md`, incluyendo RF-001, RF-002, RF-003 y Feature 008, validación de OpenAPI y tipos generados, arquitectura, privacidad, actualización de esquema e índices con volumen representativo. Registrar los resultados en `specs/009-integridad-temporal-consumo/evidence/integration-validation.md` (nuevo).
- [X] T041 Ensayar la preparación de versión candidata y recuperación según `docs/operacion/version-candidata.md`, `docs/operacion/restaurar.md` y `docs/operacion/recuperar.md`. Comprobar respaldo previo a publicar y previo a aplicar el histórico, restauración aislada y conciliación de escrituras posteriores. Registrar en `specs/009-integridad-temporal-consumo/evidence/integration-validation.md`.
- [X] T042 **[Superada por H006: diagnóstico sobre la copia `sarc-20261113T2319Z-manual`; ver evidence/hotfix.md]** Completar el diagnóstico de solo lectura sobre una copia autorizada y aislada del conjunto histórico pertinente antes de revisar su publicación. Registrar identificación y corte de la copia, universo, cobertura, sesiones y kWh indeterminados, impacto por fuente y período y ausencia de escrituras en `specs/009-integridad-temporal-consumo/evidence/us2-historical-coverage.md` (nuevo), conforme a `specs/009-integridad-temporal-consumo/quickstart.md`. Si falta la copia o el diagnóstico, dejar pendiente la revisión del histórico. Una aplicación de correcciones se autoriza por separado.
- [X] T043 Revisar SC-001–008 y la trazabilidad de FR-001–022 (FR-006a retirado; FR-007–009 acreditados por H006–H007) y consolidar resultados y pendientes en `evidence/integration-validation.md`, listo para el flujo de publicación de Feature 007.

## Dependencies & Execution Order

## Phase 8: Armonía visual de formularios y cabeceras (pedido del responsable, 2026-11-14)

**Purpose**: corregir campos de distinto alto, etiquetas mezcladas, filas descuadradas y acciones de cabecera mal ubicadas en todos los módulos, sin cambiar comportamiento. Evidencia en `evidence/ui-harmony.md`.

- [X] T044 Medir en el navegador (sandboxes 008 y 009) el alto del contorno y la posición de las acciones de cabecera en todas las rutas y diálogos con formulario, y registrar las causas.
- [X] T045 Fijar el contorno de los campos en 56px (`design-system/_base.scss`) y subir siempre la etiqueta (`floatLabel="always"` en cada `mat-form-field`) sin superar el presupuesto inicial.
- [X] T046 Mantener las acciones de cabecera a la derecha cuando no caben junto al título (`shared/ui/_page-chrome.scss`), unificar la columna mínima de filtros y el alto de «Ingresar», y quitar el espacio reservado vacío de la captura de intermitencias.
- [X] T047 Verificar en claro y oscuro a 1366×768 y 360px, y correr lint, formato, pruebas y build; documentar las reglas en `sarc-frontend/DESIGN.md`.

### Orden y dependencias

```text
T001 → T002 → T003 → T004 → T005 → T006
                                   ├─→ US1 · T007–T016
                                   └─→ US4 · T034 (diagnóstico en sandbox)
US1 → US2 · T017–T022 → US3 · T023–T033
US3 + diagnóstico US4 → T035–T039 → T040 → T041 → T042 → T043
```

- US1 usa el esquema aditivo y se valida con estados de ensayo preparados sin depender de los flujos de corrección de US2 o US3. US2 usa la política temporal de US1 y las revisiones comunes de T006. US3 reutiliza estas revisiones y se integra después de US2 para serializar los cambios en sesión e importación. Cada historia tiene su fixture y resultado independiente.
- El diagnóstico inicial de US4 puede avanzar junto a US1 en el sandbox de referencia. La implementación visual espera los cambios funcionales porque comparte las fachadas y plantillas de Consumo.
- En cada historia, terminar el grupo de pruebas antes de su implementación. Modelo y políticas preceden a servicios, contrato y presentación. Regenerar OpenAPI y tipos en serie y con la base de integración acordada.
- FR-001–006 y FR-010 se verifican en US1 (FR-006a retirado). FR-007–009 y la cobertura histórica quedaron acreditados por H006–H007. FR-011–016 se verifican en US3. FR-017–020 se verifican en US4. FR-021 se atiende en T003 y FR-022 en T040–T043.

### Parallel Opportunities

| Historia | Trabajo paralelo con sus prerrequisitos completos |
| --- | --- |
| US1 | T007 parser, T008 API y agregados, T009 renderizadores y T010 Angular. |
| US2 | T017 diagnóstico y aplicación, y T018 archivo de informes. Coordinar el archivo compartido con Feature 008 al integrar. |
| US3 | T023 contrato de acciones, T024 reimportación y T025 interacción de la lista. |
| US4 | T035 fachadas y T036 componentes una vez registrada la causa o hipótesis reproducible de T034. |

## Implementation Strategy

1. **MVP**: Completar base y US1. Demostrar con la fixture mixta que el filtro de fuente no mueve la fecha, se conservan los instantes y el rango inicial aplica D13.
2. **Entrega incremental**: Validar US2 con histórico de ensayo. Integrar US3 con pruebas de versiones, baja e idempotencia. Ejecutar US4 a partir de evidencia observable y coordinar cambios compartidos.
3. **Cierre**: Ejecutar los ensayos integrados y de recuperación. La revisión de publicación usa el flujo de Feature 007 y considera cobertura del histórico y estado de la evidencia visual.

## Notes

- Los documentos de evidencia y archivos marcados como nuevos son entregables de implementación. El número sugerido de Flyway es provisional hasta confirmar el historial.
- Las marcas de esta lista se completan cuando se verifica el resultado correspondiente. Mantener la selección temporal de Feature 009 en el checkout compartido y respetar el punto de integración acordado para los archivos de Feature 008.

