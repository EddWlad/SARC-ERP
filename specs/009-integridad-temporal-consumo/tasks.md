---
description: "Tareas de implementación de la Feature 009"
---

# Tasks: Integridad temporal, gestión de sesiones y estabilidad visual

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contratos](contracts/consumo.md) y [quickstart.md](quickstart.md).

**Pruebas**: Los escenarios de aceptación de la spec y la guía de validación justifican las pruebas de cada historia. Añadir los casos antes de implementar el cambio y comprobar que detectan la diferencia esperada. Ejecutarlos de nuevo al cerrar la historia.

**Formato**: `[ID] [P?] [US?] Descripción y rutas`. `[P]` identifica tareas independientes dentro de su grupo, con los prerrequisitos de fase ya completados. Las rutas son relativas a la raíz y los archivos nuevos se indican como tales.

## Phase 1: Setup (Shared Infrastructure)

**Propósito**: Confirmar una base aislada y coordinar la integración de la Feature 009.

- [ ] T001 Confirmar un checkout aislado para la implementación, registrar el SHA de referencia, la versión de Feature 008 que se usará para regresión, el estado de la migración V018 y los archivos compartidos que requieren coordinación. Documentar en `specs/009-integridad-temporal-consumo/evidence/integration-baseline.md` (nuevo) y verificar la selección temporal mediante `.specify/scripts/powershell/check-prerequisites.ps1` con `SPECIFY_FEATURE_NO_PERSIST=1`.
- [ ] T002 Ejecutar la referencia de Consumo con la guía de `specs/009-integridad-temporal-consumo/quickstart.md` y los comandos vigentes de `.github/workflows/ci.yml`. Guardar los resultados base y las limitaciones de entorno en `specs/009-integridad-temporal-consumo/evidence/integration-baseline.md`.

## Phase 2: Foundational (Blocking Prerequisites)

**Propósito**: Cumplir la gobernanza documental y preparar la evolución de datos compartida por las historias.

- [ ] T003 Registrar la decisión A y el alcance de las acciones de sesión en `documents/control-cambios-prd.md`. Conciliar PRD, TRD y Feature 002 con la matriz de `specs/009-integridad-temporal-consumo/plan.md` antes de implementar el cambio afectado. Registrar el impacto y las referencias que requieran actualización. Incluir la excepción histórica de FR-006a y la autorización de FR-008 en el alcance documental.
- [ ] T004 Añadir casos de actualización desde el esquema anterior en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/architecture/MigrationUpgradeTest.java` y `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionSchemaTest.java`. Verificar que la migración no reclasifica filas antiguas, admite campos opcionales para registros anteriores y conserva identidades, relaciones de revisión e índices.
- [ ] T005 Crear la migración aditiva en `sarc-bakend/src/main/resources/db/migration/` con el primer número disponible tras comprobar V018 y el historial integrado. Usar `V019__consumo_integridad_sesiones.sql` si sigue libre. Ampliar procedencia de sesiones y filas, baja, revisiones y versión esperada de importación según `specs/009-integridad-temporal-consumo/data-model.md`. Mantener «UUID interno y clave natural única `(source, source_scope, external_session_id)`», «`started_at` obligatorio y `ended_at` opcional, en UTC como instantes» y «`start_date` sigue siendo `DATE` e indexada». Los registros de la versión anterior siguen legibles con valores anteriores y campos nuevos opcionales. Validar con T004. Incorporar la persistencia de constancias de aprobación inmutables por pareja propuesta y huella, con identidad y momento registrados por el servidor.
- [ ] T006 Extender `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/session/SessionRevision.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/SessionRevisionJpaRepository.java` para los cuatro tipos de revisión, con datos anteriores y posteriores, actor, instante, motivo y referencia real. Mantener la procedencia de lote de las revisiones importadas y permitir revisiones sin lote sucesor según el modelo. Preparar persistencia reutilizable por US2 y US3.

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
- [ ] H007 Publicar con el flujo de Feature 007 (respaldo previo, versión candidata) y verificar en producción el reparto diario de un rango con sesiones afectadas.

**Checkpoint**: Con la fixture 1212/Wallbox el 10/11 suma 5 kWh y el 11/11 8 kWh en histórico, tablero, PDF y Excel; V019 ensayada sobre copia de producción.

## Phase 3: User Story 1 — Consultar Consumo con la fecha de la fuente (P1, MVP)

**Objetivo**: Atribuir y mostrar sesiones con la política de cada fuente en el histórico, los filtros y las exportaciones.

**Prueba independiente**: Con Enerlink 1212 y Wallbox, obtener 5 kWh el 10/11, 8 kWh el 11/11 y 13 kWh para ambos días en todas las superficies. Filtrar Enerlink conserva su fecha del 11/11. En el cambio de mes Ecuador/UTC se conserva el rango inicial de D13.

### Tests for User Story 1

- [ ] T007 [P] [US1] Extender `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/parser/SessionParsersTest.java` con UTC+00:00, UTC-05:00, otro desplazamiento admitido, zonas ausentes o mal declaradas, fin al día siguiente y regresión Wallbox. Comprobar los instantes y datos originales de Enerlink 1212 y 1211. Afirmar Inicio ausente → Ecuador aplicado, Fin ausente → zona resuelta de Inicio, dos desplazamientos distintos válidos → resolver cada extremo y comprobar orden por instante, y declaración inválida → fila inválida sin incorporación, según los ejemplos de `specs/009-integridad-temporal-consumo/quickstart.md`.
- [ ] T008 [P] [US1] Añadir casos de histórico, límites de día/mes, consolidado y filtro de fuente en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionApiTest.java`, y de tendencia, conteo y ranking en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionCalculatorTest.java`. Usar el conjunto 5/8/13 kWh y verificar cero frente a energía desconocida. Probar FR-006a con 15 kWh y 3 sesiones, incluidos 2 kWh y 1 sesión heredados. Cubrir el caso heredado de energía desconocida. Sembrar dos estados de la misma sesión sintética con igual identidad, uno con atribución heredada y otro con fecha de origen verificada. Comprobar que el segundo conserva el conteo y deja el desglose heredado. La transición por el flujo de aprobación y aplicación se verifica en T022.
- [ ] T009 [P] [US1] Agregar cobertura de etiquetas, corte, formato de Enerlink y concordancia PDF/Excel en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionPdfRendererTest.java` y `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionExcelRendererTest.java`. Comprobar que la presentación vigente de Wallbox se conserva. Afirmar el desglose de sesiones y kWh heredados dentro del total y la fecha de corte propia de PDF y Excel.
- [ ] T010 [P] [US1] Probar D13 en `sarc-frontend/src/app/features/consumption/application/period-selection.spec.ts` y la presentación de la fecha de origen, segundos y caso indeterminado en `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.spec.ts`, independientemente de la zona del navegador.

### Implementation for User Story 1

- [ ] T011 [US1] Incorporar evidencia temporal original y efectiva en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/port/ParsedSession.java`, `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/batch/ImportRow.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/session/EnergySession.java`. Mantener «Valores de inicio y fin en hora local de origen, desplazamiento o zona de cada extremo, origen declarado o aplicado y estado de verificación». Aplicar «Enerlink usa la fecha local de inicio vigente en la base temporal de origen. Wallbox conserva el cálculo vigente en la zona operativa». Conservar el instante, la fila y la referencia a evidencia verificable.
- [ ] T012 [US1] Transportar la procedencia desde `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/parser/EnerlinkSessionParser.java`, `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/parser/TabularSessionParser.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/parser/WallboxSessionParser.java` a `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ImportProcessor.java`. Conservar formatos admitidos y primera hoja, distinguir zona declarada y aplicada, señalar encabezados ilegibles o contradictorios y mantener Wallbox. Una discrepancia de fecha de origen con igual instante exige revisión si modifica la atribución. Aplicar explícitamente los resultados de T007, incluidos los desplazamientos resueltos por extremo y la conservación del fin ausente.
- [ ] T013 [US1] Usar fecha efectiva y límites de calendario `[from, to)` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/SessionPredicates.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java`. Extender `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/response/SessionResponse.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/response/ConsumptionSummaryResponse.java` de forma aditiva para presentar fecha de origen y distinguir atribución operativa heredada. Mantener orden por instante donde se usa y reutilizar filtros, agregados y reglas oficiales. Conservar elegibilidad y fecha operativa anterior para los indeterminados. Agregar las sesiones y kWh conocidos heredados dentro del total usando el predicado oficial y los mismos filtros, sin convertir energía desconocida a cero.
- [ ] T014 [US1] Aplicar el criterio de fuente al corte y la presentación en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionReportService.java`, `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionPdfRenderer.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/report/ConsumptionExcelRenderer.java`. Mantener el snapshot de emisión, zona operativa del corte, nombres actuales, privacidad y artefactos archivados. Rotular «Fecha de inicio según la fuente» en el consolidado y sus exportaciones y distinguir la cobertura heredada cuando aplique. Presentar el desglose heredado de FR-006a dentro del total y el corte propio de cada emisión. Mantener `X-Report-Cut-Off` concordante con el documento.
- [ ] T015 [US1] Regenerar `sarc-bakend/docs/api/openapi.json` y `sarc-frontend/src/app/core/api/generated/types.gen.ts` desde el checkout integrado conforme a `.github/workflows/ci.yml`. Consumir fechas locales explícitas y su zona en `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.html`, `sarc-frontend/src/app/features/consumption/presentation/energy-review-dialog/energy-review-dialog.html` y `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.html`. Mantener el reloj operativo y la presentación Wallbox sin cambiar el DatePipe global. Mostrar «Fecha operativa heredada» en las filas correspondientes y el desglose oficial y `generatedAt` en el dashboard.
- [ ] T016 [US1] Ejecutar T007–T010 y el escenario temporal de `specs/009-integridad-temporal-consumo/quickstart.md`. Registrar comparación de históricos, dashboard, tendencia, PDF y Excel y conservación de D13 en `specs/009-integridad-temporal-consumo/evidence/us1-validation.md` (nuevo). Comparar cifras con un conjunto de revisiones, filtros y permisos sin cambios. En un ensayo separado, usar una fixture en la base aislada: consultar el dashboard con 13 kWh, preparar y confirmar la siguiente revisión de ensayo de la misma sesión (de 8 a 9 kWh) y avanzar el reloj antes de solicitar la exportación. Verificar 14 kWh en el informe posterior y en el dashboard actualizado, con la hora de cálculo y el corte de emisión identificables. Este ensayo no requiere las operaciones nuevas de US2 o US3.

**Checkpoint**: US1 se demuestra en datos sintéticos con ambas plataformas y un corte común. La publicación del histórico requiere el control de cobertura de US2.

## Phase 4: User Story 2 — Auditar y corregir el histórico (P1)

**Objetivo**: Identificar el impacto de la nueva atribución y aplicar únicamente correcciones con evidencia y aprobación.

**Prueba independiente**: Clasificar cuatro sesiones de ensayo, ensayar aprobación de la propuesta y reintentar un conjunto parcialmente aplicado. Verificar identidades, kWh totales y huella del informe archivado.

### Tests for User Story 2

- [ ] T017 [P] [US2] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionTemporalReconciliationTest.java` para los cuatro resultados de diagnóstico, huella discordante, revisión efectiva tras otro lote, lectura sin escrituras, propuesta modificada, conflicto de versión, conservación de kWh y reintento parcial. Probar el contrato de aprobación con Administrador autenticado, `403` para Analista, constancia ausente o de otra propuesta, identidad aportada solo en el archivo y huella alterada. En los rechazos comprobar cero escrituras de sesiones o revisiones.
- [ ] T018 [P] [US2] Añadir la comprobación de que un informe anterior conserva bytes, permisos, identificación y corte tras una corrección, y que el nuevo se distingue por su corte, en `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/reporting/api/IssuedReportAccessApiTest.java`.

### Implementation for User Story 2

- [ ] T019 [US2] Implementar el diagnóstico de solo lectura en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionTemporalAuditService.java` (nuevo). Verificar huella de archivo o pegado, seleccionar fila y revisión efectiva por clave completa y clasificar «**coincide**, **cambia solo atribución**, **error de instante comprobado** e **indeterminado**». Conservar los casos sin evidencia y cuantificar la cobertura.
- [ ] T020 [US2] Construir propuesta y cálculo de impacto en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionTemporalReconciliationService.java` (nuevo). Incluir identidad, evidencia, versión, valores antes/después, motivo, filtros, corte, sesiones y kWh por día, mes y fuente. Ligar la aprobación explícita del responsable a la huella exacta y mostrar diferencias de cobertura. Exponer la operación de aprobación de `specs/009-integridad-temporal-consumo/contracts/consumo.md` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/controller/ConsumptionTemporalReconciliationController.java` (nuevo). Exigir rol Administrador, tomar la identidad de la sesión, contrastar la huella con el manifiesto controlado y persistir una constancia inmutable.
- [ ] T021 [US2] Aplicar únicamente candidatos aprobados con evidencia y versión vigentes en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ConsumptionTemporalReconciliationService.java`. Registrar revisión y auditoría atómicas, conservar los valores previos y usar propuesta y candidato como clave de idempotencia. Registrar aplicados, ya aplicados, conflictos e indeterminados. Dar al procedimiento una entrada operativa de invocación explícita en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/bootstrap/ConsumptionTemporalReconciliationCommand.java` (nuevo), separada del arranque habitual y sin endpoint público. Antes de cualquier escritura, comprobar la constancia persistida controlada por el sistema, la propuesta y la huella recalculada. Rechazar una constancia ausente, inválida o ajena al conjunto, incluso si el manifiesto incluye una supuesta identidad aprobadora.
- [ ] T022 [US2] Ensayar T017–T018 y el procedimiento de `specs/009-integridad-temporal-consumo/quickstart.md` en una copia aislada. Documentar manifestación de alcance, revisión de cobertura, aprobación, reintento y recuperación en `specs/009-integridad-temporal-consumo/evidence/us2-reconciliation.md` (nuevo). La aplicación sobre datos reales es una operación posterior separada. El ensayo valida el mecanismo y no sustituye el diagnóstico del histórico pertinente exigido en T042. Aprobar y aplicar una propuesta con evidencia que verifique la fecha de una sesión heredada. Comprobar que sale del desglose al quedar verificada, mantiene su identidad, se cuenta una sola vez y conserva la emisión archivada.

**Checkpoint**: El ensayo demuestra conservación del informe anterior y del total de kWh en correcciones exclusivamente temporales. Quedan trazados los registros indeterminados.

## Phase 5: User Story 3 — Ver, editar y eliminar lógicamente una sesión (P1)

**Objetivo**: Ofrecer acciones por sesión con permisos, historial y protección ante reimportaciones.

**Prueba independiente**: Como editor, ver, editar y dar de baja una sesión. Como usuario de consulta, verificar ausencia de privilegios de escritura. Reimportar lecturas iguales y distintas y confirmar que se preserva la decisión anterior.

### Tests for User Story 3

- [ ] T023 [P] [US3] Crear `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionSessionActionsApiTest.java` para detalle por rol, acceso directo, redacción de evidencia, edición, baja y concurrencia. Incluir motivo obligatorio, estados D10, nueva vinculación validada, contraste entre energía desconocida y cero, y respuestas 403, 404 y 409.
- [ ] T024 [P] [US3] Ampliar `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/consumption/api/ConsumptionApiTest.java` para reimportación idéntica y discrepante, lotes preparados antes de una edición o baja, lotes antiguos sin versión, y protección de comandos de prueba, aprobación y enlace. Verificar exclusión de bajas en las consultas oficiales y preservación de identidad y auditoría.
- [ ] T025 [P] [US3] Ampliar `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.spec.ts` con acciones según rol, confirmación, cancelación, conservación de contexto y conflicto al guardar. Cubrir la tabla y la tarjeta móvil.

### Implementation for User Story 3

- [ ] T026 [US3] Implementar transiciones de edición y baja en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/session/EnergySession.java` y reutilizar `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/policy/SessionRules.java`. Mantener «kWh normalizado `HALF_UP` a tres decimales, con `null` distinto de cero», «Marca con instante, actor y motivo en su revisión» y «Se conserva el estado de calidad previo». Revalidar `fin >= inicio`, vínculo y fuente preferente, aplicar D10 y mantener `version` optimista y `revision` histórica.
- [ ] T027 [US3] Extender `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/SessionService.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/SessionQueryService.java` con detalle autorizado, edición y baja con versión y motivo. Conservar identidad, evidencia y revisión en la misma transacción. Proteger de nuevas mutaciones una baja desde las operaciones vigentes y resolver equipo mediante el puerto de Inventario.
- [ ] T028 [US3] Excluir bajas en el predicado común de `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/SessionPredicates.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java`. Mantener «El lookup por identidad incluye las bajas» y resguardar conciliaciones operativas. Comprobar el mismo criterio para histórico, agregados y exportaciones.
- [ ] T029 [US3] Proteger `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/application/service/ImportProcessor.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/domain/model/batch/ImportRow.java` en previsualización, confirmación y reintento. Comparar estado, versión y procedencia, reclasificar filas obsoletas, excluir bajas de la aplicación y exigir decisión explícita para discrepancias. Conservar procesamiento de filas nuevas no conflictivas.
- [ ] T030 [US3] Exponer detalle, `PATCH` y acción de baja según `specs/009-integridad-temporal-consumo/contracts/consumo.md` en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/controller/EnergySessionController.java`. Definir entradas en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/request/EditEnergySessionRequest.java` y `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/request/DeleteEnergySessionRequest.java` y el detalle autorizado en `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/consumption/api/response/SessionDetailResponse.java` (nuevos). Validar los campos y la semántica ausente/nulo, usar el manejo de errores vigente y sincronizar `sarc-bakend/docs/api/openapi.json` y `sarc-frontend/src/app/core/api/generated/types.gen.ts` tras verificar el estado integrado.
- [ ] T031 [US3] Añadir métodos y estado de las acciones en `sarc-frontend/src/app/features/consumption/data-access/consumption-api.ts` y `sarc-frontend/src/app/features/consumption/application/sessions-facade.ts`. Actualizar la consulta vigente tras una mutación y mantener edición, versión y filtro al tratar errores. Exponer datos de auditoría solo cuando los entrega el servidor.
- [ ] T032 [US3] Integrar «Ver», «Editar» y «Eliminar» en `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.ts`, `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.html` y `sarc-frontend/src/app/features/consumption/presentation/session-action-dialog/session-action-dialog.ts` (nuevo). Reutilizar patrones de diálogo, motivos y accesibilidad de Consumo e Inventario. Validar teclado, confirmación y temas.
- [ ] T033 [US3] Ejecutar T023–T025 y los recorridos de `specs/009-integridad-temporal-consumo/quickstart.md`. Guardar la matriz de permisos, evidencia de baja y reimportación y validación de tabla y tarjeta móvil en `specs/009-integridad-temporal-consumo/evidence/us3-session-actions.md` (nuevo). Repetir el escenario de cortes de T016 guardando el cambio de 8 a 9 kWh mediante la acción Editar de US3. Verificar el corte posterior, los 14 kWh y la revisión registrada.

**Checkpoint**: Una baja no reaparece al reimportar y las solicitudes de edición sobre datos obsoletos no pisan revisiones nuevas.

## Phase 6: User Story 4 — Trabajar con vistas estables (P2)

**Objetivo**: Diagnosticar y corregir el parpadeo verificable manteniendo los refrescos legítimos y el contexto del usuario.

**Prueba independiente**: Repetir el recorrido antes y después con respuestas iguales, nuevas, tardías y fallidas, y con regreso a la pestaña. Confirmar estabilidad de gráficos y filas y conservación de foco, scroll y edición.

### Tests and diagnosis for User Story 4

- [ ] T034 [US4] Reproducir el síntoma en un sandbox aislado según `specs/009-integridad-temporal-consumo/quickstart.md`. Registrar en `specs/009-integridad-temporal-consumo/evidence/us4-baseline.md` (nuevo) condiciones, red, navegación, identidad de componentes y gráficos, animación y vista afectada. Distinguir hallazgos confirmados de casos sin reproducción.
- [ ] T035 [P] [US4] Agregar casos de recarga con igual clave, datos nuevos, error, cambio de filtro y respuesta tardía en `sarc-frontend/src/app/features/consumption/application/consumption-dashboard-facade.spec.ts` (nuevo) y `sarc-frontend/src/app/features/consumption/application/sessions-facade.spec.ts`. Verificar que el refresco conserva el corte por inactividad.
- [ ] T036 [P] [US4] Agregar regresiones para la causa observada en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.spec.ts` y `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.spec.ts`. Verificar estabilidad de identidad, foco y contexto, y cubrir el ciclo de Chart.js en `sarc-frontend/src/app/shared/ui/chart/chart-view.spec.ts` si el diagnóstico exige cambiarlo y se coordinó la integración.

### Implementation for User Story 4

- [ ] T037 [US4] Ajustar el manejo de refresco para la causa verificada en `sarc-frontend/src/app/features/consumption/application/consumption-dashboard-facade.ts`, `sarc-frontend/src/app/features/consumption/application/sessions-facade.ts` y sus plantillas en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.html` y `sarc-frontend/src/app/features/consumption/presentation/sessions-page/sessions-page.html`. Conservar resultados compatibles con la misma consulta, descartar respuestas tardías y mostrar estados veraces para una clave nueva. Mantener navegación, filtros, edición e inactividad.
- [ ] T038 [US4] Estabilizar las entradas de gráfico y las actualizaciones de Chart.js cuando la evidencia lo justifique en `sarc-frontend/src/app/features/consumption/presentation/dashboard-page/dashboard-page.ts`. Mantener un cambio local a Consumo cuando cubra el caso. Si la causa exige tocar `sarc-frontend/src/app/shared/ui/chart/chart-view.ts` u otras vistas, coordinar primero con Feature 008 y verificar a ambos consumidores.
- [ ] T039 [US4] Repetir la reproducción de T034 y ejecutar T035–T036. Documentar en `specs/009-integridad-temporal-consumo/evidence/us4-results.md` (nuevo) el antes/después, recargas con datos nuevos y equivalentes y revisión a 360, 768 y 1440 px en ambos temas. Dejar pendiente SC-006 si el síntoma original no pudo reproducirse.

**Checkpoint**: SC-006 se acredita con evidencia del mismo recorrido antes y después. La UI se valida por teclado, móvil, tablet y escritorio.

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T040 Ejecutar las verificaciones integradas de `.github/workflows/ci.yml` y `specs/009-integridad-temporal-consumo/quickstart.md`, incluyendo RF-001, RF-002, RF-003 y Feature 008, validación de OpenAPI y tipos generados, arquitectura, privacidad, actualización de esquema e índices con volumen representativo. Registrar los resultados en `specs/009-integridad-temporal-consumo/evidence/integration-validation.md` (nuevo).
- [ ] T041 Ensayar la preparación de versión candidata y recuperación según `docs/operacion/version-candidata.md`, `docs/operacion/restaurar.md` y `docs/operacion/recuperar.md`. Comprobar respaldo previo a publicar y previo a aplicar el histórico, restauración aislada y conciliación de escrituras posteriores. Registrar en `specs/009-integridad-temporal-consumo/evidence/integration-validation.md`.
- [ ] T042 Completar el diagnóstico de solo lectura sobre una copia autorizada y aislada del conjunto histórico pertinente antes de revisar su publicación. Registrar identificación y corte de la copia, universo, cobertura, sesiones y kWh indeterminados, impacto por fuente y período y ausencia de escrituras en `specs/009-integridad-temporal-consumo/evidence/us2-historical-coverage.md` (nuevo), conforme a `specs/009-integridad-temporal-consumo/quickstart.md`. Si falta la copia o el diagnóstico, dejar pendiente la revisión del histórico. Una aplicación de correcciones se autoriza por separado.
- [ ] T043 Revisar criterios SC-001–008 y la trazabilidad de FR-001–022 (incluido FR-006a) frente a `specs/009-integridad-temporal-consumo/spec.md` y `specs/009-integridad-temporal-consumo/quickstart.md`. Incluir el diagnóstico de T042, consolidar resultados y pendientes reales en `specs/009-integridad-temporal-consumo/evidence/integration-validation.md` y dejar el resultado listo para revisión del flujo de publicación vigente de Feature 007.

## Dependencies & Execution Order

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
- FR-001–006, FR-006a y FR-010 se verifican en US1. FR-007–010 se verifican en US2 y la cobertura histórica en T042. FR-011–016 se verifican en US3. FR-017–020 se verifican en US4. FR-021 se atiende en T003 y FR-022 en T040–T043.

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

