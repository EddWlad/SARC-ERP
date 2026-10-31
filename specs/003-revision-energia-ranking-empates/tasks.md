# Tasks: Revisión de energía alta y empates del ranking

**Input**: `specs/003-revision-energia-ranking-empates/`: spec.md, plan.md, research.md, data-model.md, contracts/api-changes.md y quickstart.md.
**Branch**: `codex/003-revision-energia-ranking-empates`, consistente con plan.md y el selector de Spec Kit.
**Prerequisites**: Feature 002 integrada en `cb9a763`, V009 existente y recorrido confirmado por el usuario. Verificar la línea base técnica al implementar, sin exigir repetir el recorrido ni modificar T075 de la 002. V010 reservada para esta feature. No ejecutar tareas pendientes de la 002 ni sobrescribir cambios de otra sesión.
**Revision**: lista actualizada al plan y sus anexos con las seis aclaraciones aprobadas del 2026-10-30. Sustituye las descripciones anteriores de estos mismos IDs; no sumar otra lista de tareas ni aplicar la regla obsoleta de conservar únicamente aprobaciones. La advertencia histórica de plan.md sobre actualizar tareas queda atendida por esta revisión.
**Tests**: Incluidos para verificar los escenarios y criterios de prueba explícitos de spec.md y las obligaciones de `sarc-bakend/CLAUDE.md`; escribirlos y comprobar el fallo pertinente antes de implementar cada historia.
**Organization**: Tres historias P1. US1 entrega revisión trazable; US2 completa las combinaciones e informes; US3 corrige el ranking. Generar esta lista no autoriza implementar ni ejecutar cambios sobre datos locales.

## Format: `[ID] [P?] [Story] Description`

- `[P]` permite trabajar en archivos distintos una vez satisfechas las dependencias indicadas; no autoriza agentes ni cambios concurrentes al mismo archivo.
- Todas las tareas de historia llevan `[US1]`, `[US2]` o `[US3]` y referencia funcional. Las de preparación y cierre no llevan historia.
- Cada casilla representa trabajo pendiente, no una comprobación ya realizada.

## Path Conventions

- `BE/` = `sarc-bakend/src/main/java/com/tidsec/sarc_bakend/`.
- `BT/` = `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/`.
- `MIG/` = `sarc-bakend/src/main/resources/db/migration/`.
- `FE/` = `sarc-frontend/src/app/`.
- `DOC/` = `specs/003-revision-energia-ranking-empates/`.

Se conserva el monolito modular y los records `from(...)`, sin introducir MapStruct ni Spring Batch. Java con tabulaciones; textos en español. Contrato generado, nunca editar tipos generados a mano. Ninguna tarea modifica migraciones aplicadas ni reportes archivados.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: comprobar dependencias y preparar validación reproducible sin alterar el entorno del usuario.

- [X] T001 Verificar `MIG/V009__vinculacion_enerlink_sesiones.sql`, commits de la 002 y `DOC/plan.md`; registrar en `DOC/quickstart.md` que el usuario confirmó el recorrido real y comprobar V010 libre, rama 003 y regresiones. T075 sin marcar en `specs/002-vinculacion-enerlink-sesiones/tasks.md` no invalida por sí sola esa confirmación ni autoriza modificarla. Conservar nombres reales `LINKABLE`, `linkableRows`, `LINKED`; no implementar otra vez 002.
- [X] T002 Definir en `DOC/quickstart.md` la ejecución aislada: PostgreSQL 18 desechable, almacenamiento de prueba sin acceso al real, datos sintéticos, puertos independientes y limpieza de recursos propios; reutilizar `sarc-bakend/src/test/java/com/tidsec/sarc_bakend/Feature001BrowserSandbox.java` como referencia si se necesita recorrido de navegador. No descartar lotes reales ni detener servicios por compartir 8080, 4200 o 5432.
- [X] T003 Ejecutar la línea base de `sarc-bakend/pom.xml` con `./mvnw test` y de `sarc-frontend/package.json` con `npm test -- --watch=false`, `npm run lint` y `npm run build`; registrar resultados previos y comandos efectivos en `DOC/quickstart.md`, usando únicamente los recursos aislados de T002.

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: fijar las reglas comunes que comparten estados, revisiones e indicadores. No aplicar todavía una migración con entidades desalineadas.

- [X] T004 Contrastar las seis aclaraciones de `DOC/spec.md`, `DOC/data-model.md` y `DOC/contracts/api-changes.md` con `BE/modules/consumption/domain/model/session/EnergySession.java` y `BE/modules/consumption/domain/policy/SessionRules.java`; registrar en `DOC/quickstart.md` matriz de correcciones por kWh, estados excluyentes, filtro independiente y versiones. No reabrir decisiones aprobadas ni recuperar la regla antigua de última lectura aprobada.
- [X] T005 Preparar matriz FR-001–017/SC-001–007 en `DOC/quickstart.md` con correcciones/versiones y seis aclaraciones; incluir explícitamente I1 (aviso vs error en ParsedSession y clasificación), A1 (contador de lecturas altas y decisión conservada) y lote mixto G1 con sus nueve filas y oráculo 7 sesiones/5 oficiales/34 kWh conocidos/1 sin energía, sin sustituir regresiones 002.

**Checkpoint**: dependencia 002 satisfecha, línea base conocida y matrices preparadas. El arranque con `ddl-auto=validate` se comprueba después de T009–T012, no antes de mapear las nuevas columnas.

## Phase 3: User Story 1 - Revisar lecturas superiores al umbral (Priority: P1)

**Goal**: guardar observaciones, aprobar o rechazar con justificación, consultar decisiones y proteger correcciones y concurrencia (FR-001–005, FR-007–008, FR-010–011, FR-017).
**Independent Test**: importar 1.500 kWh por cada fuente con equipo/fuente válidos; confirmar sin decisión energética en preview, aprobar/rechazar y sustituir decisiones. Corregir solo término conserva aprobación y rechazo; cambiar energía alta reinicia revisión. Dos decisiones iguales con misma versión solo permiten una aplicación; reimportación idéntica dos veces no añade sesiones ni decisiones.

### Tests for User Story 1

- [X] T006 [P] [US1] Ampliar `BT/modules/consumption/infrastructure/parser/SessionParsersTest.java` y crear `BT/modules/consumption/application/port/ParsedSessionTest.java`: ambas fuentes conservan lectura alta y aviso, `isValid()` acepta solo aviso energético con lectura y rechaza lectura nula u otro código bloqueante incluso junto al aviso; límites 1.000,000/1.000,0004 frente a 1.000,001/1.000,0005 tras HALF_UP, negativos/formato/fechas/no representable inválidos; energía ausente admisible de 002 no se observa. Comprobar consumo clasifica/guarda la alta y mantiene errores reales (I1; FR-001, FR-011).
- [X] T007 [P] [US1] Ampliar `BT/modules/consumption/domain/model/EnergySessionTest.java`: alta observada, decisiones sucesivas, corrección de término con mismos kWh normalizados conserva PENDING/APPROVED/REJECTED sin otra decisión; cambio 1.500→1.800→1.500 reinicia revisión cada vez sin rescatar aprobación histórica; energía no superior/ausente limpia condición; revalidar controles y preservar TEST. Decidir no incrementa revisión de lectura (FR-002–008; US1.9).
- [X] T008 [US1] Ampliar `BT/modules/consumption/api/ConsumptionApiTest.java`: POST/GET, roles/CSRF, justificación blancos/501 rechazada y 1/500 tras recorte aceptada, inexistente/sin observación; versión vieja y dos decisiones iguales simultáneas solo dejan una y rollback integral. Con versión nueva se permite decidir de nuevo y avanza version, no revisión; historial con timestamps iguales ordenado por sessionVersion. Reimportar altas idénticas aprobada/rechazada: decisión e indicadores intactos, mensaje no anuncia reinicio y energyReviewRows cuenta lecturas altas, no pendientes; filtro coincide incluso con duplicada interpretable, inválida sin lectura excluida (A1; FR-003–005/007–010/017; SC-001/003/004).

### Implementation for User Story 1

- [X] T009 [US1] Crear `MIG/V010__revision_energia.sql` según `DOC/data-model.md`: `energy_session.energy_review VARCHAR(20)` con «PENDING, APPROVED, REJECTED» y nulo sin observación; ampliar estados con `ENERGY_REVIEW` y `ENERGY_REJECTED`, CHECK «VALIDATED exige energy_review nulo o APPROVED» sin admitir REJECTED/PENDING por lógica de NULL; índice parcial por `energy_review` no nulo; `energy_session_revision.energy_review VARCHAR(20)` con la misma condición de versión; `energy_import_batch.energy_review_rows INTEGER NOT NULL DEFAULT 0`. No cambiar `ck_energy_session_energy` ni hacer UPDATE de históricos; no editar V001–V009.
- [X] T010 [US1] Completar `MIG/V010__revision_energia.sql` con tabla de solo inserción `energy_session_review`: «id UUID PK», «session_id UUID FK a energy_session», «session_revision INTEGER: Revisión evaluada», «session_version BIGINT: Versión de concurrencia evaluada, obligatoria; única por sesión», «decision VARCHAR(20): APPROVED o REJECTED», «justification VARCHAR(500): Obligatoria, no vacía», «energy_kwh NUMERIC(12,3): Lectura evaluada, obligatoria», «decided_by UUID: Puede ser nulo como otros campos de actor», «decided_at TIMESTAMPTZ: Obligatoria». Índice `(session_id, session_version DESC)` y UNIQUE `(session_id, session_version)`; no nuevas cascadas de borrado.
- [X] T011 [US1] Añadir `BE/modules/consumption/domain/model/session/EnergyReviewStatus.java` con «PENDING, APPROVED, REJECTED. Nulo = sin observación» y crear `BE/modules/consumption/domain/model/session/EnergyReview.java` con las restricciones literales de T010; añadir estados a `BE/modules/consumption/domain/model/session/SessionState.java` y columnas a `EnergySession.java` y `SessionRevision.java` del mismo directorio, conservando identificadores y lecturas previas.
- [X] T012 [US1] Crear `BE/modules/consumption/domain/repository/EnergyReviewRepository.java`, `BE/modules/consumption/infrastructure/persistence/EnergyReviewJpaRepository.java` y `EnergyReviewRepositoryAdapter.java` del mismo directorio: inserción sin actualización/borrado, historial por sessionVersion evaluada descendente (también con timestamps iguales), consulta de decisión vigente para la lectura actual y recuperación por conjuntos de justificaciones para informes sin consulta por sesión. La condición energética actual gobierna elegibilidad; no restaurar una decisión antigua por coincidencia de kWh.
- [X] T013 [US1] Ampliar `BT/modules/consumption/infrastructure/persistence/EnergySessionSchemaTest.java`: migrar V009 con fixtures a V010, comprobar catálogo/versiones Flyway/PostgreSQL 18, estados, PK/FK/UNIQUE sesión+versión, justificación y lectura obligatorias, nulabilidad conservada de 002, CHECK VALIDATED y `ddl-auto=validate` tras T011–T012; verificar datos previos intactos. Guardar pruebas negativas de restricciones, sin tocar base dev (FR-004, FR-017).
- [X] T014 [US1] Modificar `BE/modules/consumption/domain/policy/SessionRules.java`, `BE/modules/consumption/infrastructure/parser/TabularSessionParser.java` y `BE/modules/consumption/application/port/ParsedSession.java`: >1.000 tras HALF_UP genera solo ENERGY_ABOVE_THRESHOLD; >=1e9 invalida ENERGY_NOT_STORABLE; requireValid no lanza por aviso energético. ParsedSession.isValid exige lectura no nula y ningún código bloqueante: únicamente ENERGY_ABOVE_THRESHOLD puede ser aviso no invalidante. Parser conserva lectura solo sin errores reales; negativos, formato, fechas y vacíos por fuente siguen iguales. Actualizar Javadoc de ParsedSession; no modificar RowIssue global ni admitir cualquier observación desconocida (I1).
- [X] T015 [US1] Implementar en `BE/modules/consumption/domain/model/session/EnergySession.java` evaluación al registrar/corregir y decisión con `TEST > PENDING_LINK > ENERGY_REJECTED > ENERGY_REVIEW > AWAITING_APPROVAL > VALIDATED`. Comparar kWh normalizados previos/nuevos antes de mutar: alta sin cambios conserva PENDING/APPROVED/REJECTED; alta distinta queda PENDING sin restaurar decisiones históricas; no superior o ausente deja nulo. Recalcular tras correct/reassign preservando TEST y demás controles. `decideEnergy` exige observación no nula; ninguna corrección ajena a energía genera otra decisión (FR-006–008).
- [X] T016 [US1] Actualizar `BE/modules/consumption/domain/model/batch/ImportRow.java`, `ImportBatch.java` y `BE/modules/consumption/application/service/ImportProcessor.java`: classifyRow conserva parsed.issues de las válidas antes de añadir motivos propios; no pierde aviso ni reclasifica errores bloqueantes como válidos. energyReviewRows cuenta todas las filas interpretables >1.000 incluso IDENTICAL/DUPLICATE con lectura, excluye inválidas sin lectura; es transversal y se fija al preparar preview. Aviso neutral no cambia decisión vigente ni requiresApproval. Preservar LINKABLE, duplicados, correcciones y ENERGY_OUT_OF_RANGE histórico sin reescribir lotes (I1, A1).
- [X] T017 [US1] Añadir filtro opcional aboveThreshold al listado mediante `BE/modules/consumption/api/controller/EnergyImportController.java`, `BE/modules/consumption/application/service/SessionImportService.java`, `BE/modules/consumption/domain/repository/ImportRowRepository.java`, `BE/modules/consumption/infrastructure/persistence/ImportRowRepositoryAdapter.java` e `ImportRowJpaRepository.java`: true selecciona filas interpretables con energía normalizada >1.000 (mismo universo de energyReviewRows), false restantes y omitido no restringe; combinable con clasificación/paginación. Conservar clasificación e idempotencia (A1; FR-010).
- [X] T018 [US1] Crear `BE/modules/consumption/application/command/DecideEnergyReviewCommand.java` y `BE/modules/consumption/api/request/EnergyReviewRequest.java`: «decision: APPROVED o REJECTED», «justification: Obligatoria, 1 a 500 caracteres sin contar espacios exteriores», «version: Versión revisada» obligatoria. Validar también en el caso de uso para no depender solo del formulario o de Jakarta Validation.
- [X] T019 [US1] Implementar `SessionService.decideEnergy` en `BE/modules/consumption/application/service/SessionService.java` y el puerto/adaptador en `BE/modules/consumption/domain/repository/EnergySessionRepository.java` y `BE/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java`: permisos EDITORS, requireVersion y actualización/incremento optimista forzado por cada decisión, aun mismo estado; comprobar efectividad de flush/versionado antes de construir respuesta. Persistir sesión, decisión y ENERGY_SESSION_REVIEWED en una transacción con rollback integral; actor/reloj del servidor, sessionVersion evaluada y sessionRevision de lectura separadas. Devolver versión actualizada; conservar 404, 422, 403 y 409 CONCURRENT_MODIFICATION de `BE/shared/error/GlobalExceptionHandler.java` sin otro manejador global (FR-003–005, FR-007, FR-017).
- [X] T020 [US1] Implementar historial en `BE/modules/consumption/application/service/SessionQueryService.java`, crear `BE/modules/consumption/api/response/EnergyReviewResponse.java` con decisión, justificación, energyKwh, sessionRevision, sessionVersion, decidedBy nullable y decidedAt; añadir GET `/{id}/energy-reviews` y POST `/{id}/energy-review` en `BE/modules/consumption/api/controller/EnergySessionController.java`. Historial ordenado por versión evaluada descendente para lectores de consumo; escritura ADMINISTRADOR/ANALISTA y actor suministrado solo por servidor.
- [X] T021 [US1] Añadir filtro opcional `energyReview` en `BE/modules/consumption/domain/repository/SessionCriteria.java`, `BE/modules/consumption/infrastructure/persistence/SessionPredicates.java`, `BE/modules/consumption/application/service/SessionQueryService.java` y `BE/modules/consumption/api/controller/EnergySessionController.java`; exponer `SessionResponse.energyReview` nullable y `ImportBatchResponse.energyReviewRows` en `BE/modules/consumption/api/response/`, manteniendo filtros anteriores y visibilidad de revisión aun si la sesión está pendiente de equipo o en TEST.
- [X] T022 [US1] Regenerar `sarc-bakend/docs/api/openapi.json` con `./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true` y `FE/core/api/generated/types.gen.ts` con `npm run api:types` tras T017–T021; verificar nuevos endpoints, permisos, campos nullable y restricciones contra `DOC/contracts/api-changes.md`.
- [X] T023 [US1] Ampliar `FE/features/consumption/data-access/consumption-api.ts` y `energy-imports-api.ts` del mismo directorio con decidir/consultar revisiones y filtros `energyReview`/`aboveThreshold`; actualizar `FE/features/consumption/domain/consumption.ts` y `energy-import.ts` con estados, motivos, contador y etiquetas, manteniendo lectura de observaciones históricas.
- [X] T024 [US1] Crear `FE/features/consumption/presentation/energy-review-dialog/energy-review-dialog.ts` y `energy-review-dialog.html` del mismo directorio: aprobar/rechazar, justificación obligatoria recortada de 1 a 500 caracteres, historial, lectura y versión revisadas; gestionar carga, conflicto obsoleto y errores en español, foco y navegación por teclado con Signal Forms/Material existentes, sin inventar otra política.
- [X] T025 [US1] Integrar diálogo y actualización en `FE/features/consumption/application/sessions-facade.ts`, `FE/features/consumption/presentation/sessions-page/sessions-page.ts` y `sessions-page.html` del mismo directorio; filtrar `energyReview`, mostrar motivo y decisiones, habilitar acciones solo a editores y para observación no nula, incluidas decisiones sustituibles; mantener acceso de lectura al historial.
- [X] T026 [US1] Actualizar `FE/features/consumption/presentation/session-state-badge/session-state-badge.ts`, `FE/features/consumption/presentation/import-batch-page/import-batch-page.ts`, `import-batch-page.html` del mismo directorio y `FE/features/consumption/application/import-batch-facade.ts`: contador etiquetado «Lecturas superiores a 1.000 kWh», no pendientes ni sumando adicional de filas; filtro aboveThreshold y aviso neutral del contrato. Una idéntica aprobada/rechazada no anuncia reinicio ni exclusión nueva; no deducir decisión solo de kWh. Comunicar texto, no solo color; preview no registra decisión energética (A1).
- [X] T027 [P] [US1] Crear `FE/features/consumption/presentation/energy-review-dialog/energy-review-dialog.spec.ts` tras T024: blancos, extremos 1/500/501, decisión y versión enviadas, historial conservado y conflicto sin éxito aparente; comprobar etiquetas/foco accesibles (FR-003–005, FR-007).
- [X] T028 [P] [US1] Ampliar `FE/features/consumption/data-access/consumption-api.spec.ts` y `FE/features/consumption/application/import-batch-facade.spec.ts` tras T023–T026: etiquetas/contador transversal y filtro coincidentes, avisos neutrales con idénticas aprobadas/rechazadas sin anuncio de nueva revisión, no sumar contador a clasificaciones, datos históricos legibles. Mantener clasificación/linkableRows y aprobación ordinaria cuando corresponda (A1; FR-008/010–011).

**Checkpoint**: SC-001, SC-003 y SC-004 pasan. Una lectura alta queda guardada y revisable; solo VALIDATED es oficial. La demostración mínima usa equipo y fuente válidos; el cierre de D10 requiere además US2.

## Phase 4: User Story 2 - Respetar las demás reglas al revisar (Priority: P1)

**Goal**: aprobación energética independiente de otros controles y resultados coherentes en tablero e informes (FR-006, FR-009, FR-011, FR-017).
**Independent Test**: combinar revisión con identidad pendiente, fuente no preferente en ambos órdenes desde ambos menús, prueba y reincorporación; cada sesión cuenta en un único estado principal pero sigue localizable por filtro energético. PDF/Excel y tablero coinciden; aprobadas sobre umbral son subconjunto oficial, no sumando adicional.

### Tests for User Story 2

- [X] T029 [P] [US2] Ampliar `BT/modules/consumption/domain/model/EnergySessionTest.java`: tabla completa `TEST > PENDING_LINK > ENERGY_REJECTED > ENERGY_REVIEW > AWAITING_APPROVAL > VALIDATED`, aprobación de fuente y energía en ambos órdenes, vinculación posterior, reasignación y marca/reincorporación de prueba sin perder decisiones; ninguna aprobación evita coherencia (FR-006; SC-002).
- [X] T030 [US2] Ampliar `BT/modules/consumption/api/ConsumptionApiTest.java` con US2.1–8: TEST+pendiente+revisión solo cuenta en TEST; pendiente+revisión solo en PENDING_LINK, aunque filtro energyReview encuentre ambas; después de vínculo cambia categoría sin duplicar. Aprobar fuente y energía en ambos órdenes, nada no oficial suma y approvedAboveThreshold es subconjunto oficial. Comprobar filtros compartidos, PDF/Excel/justificación vigente, tamaño de informe con nuevos excluidos y copias archivadas intactas (FR-006, FR-009–010, FR-017; SC-002–003).

### Implementation for User Story 2

- [X] T031 [US2] Ajustar `approveSource`, `link`, `reassign` y `markTest` en `BE/modules/consumption/domain/model/session/EnergySession.java` para recalcular por la única regla de estado: fuente exige equipo vinculado, fuente no preferente y aprobación ausente, no AWAITING_APPROVAL. Exponer la capacidad del registro y añadir `SessionResponse.canApproveSource` en `BE/modules/consumption/api/response/SessionResponse.java`, independiente del estado principal; no concede permisos. Mantener decisión energética al marcar/reincorporar prueba y aprobar en ambos órdenes.
- [X] T032 [US2] Añadir `awaitingEnergyReview` a `BE/modules/consumption/application/result/PendingLinkResult.java` y `BE/modules/consumption/api/response/PendingLinkResponse.java`, y ajustar `BE/modules/consumption/application/service/SessionService.java` para contar vinculadas que terminan ENERGY_REVIEW o ENERGY_REJECTED aparte de oficiales y de fuente por aprobar, conforme a R4; preservar auditoría por sesión de 002 y agregado sin contar dos veces.
- [X] T033 [US2] Ampliar `BE/modules/consumption/application/service/ConsumptionCalculator.java`, `BE/modules/consumption/application/result/ConsumptionSummary.java`, `BE/modules/consumption/domain/repository/EnergySessionRepository.java` y `BE/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java` con energyReview, energyRejected y approvedAboveThreshold, «todos EnergyTotals». Las dos primeras consultan estados principales exclusivos; la tercera VALIDATED+APPROVED como subconjunto, nunca sumando adicional. Mismo período/filtros compartidos y tratamiento conocido/ausente de 002; filtro energético de histórico no cambia universo del resumen.
- [X] T034 [US2] Ampliar `BE/modules/consumption/application/result/ConsumptionReport.java` y `BE/modules/consumption/application/service/ConsumptionReportService.java`: justificación vigente por sesión oficial aprobada, recuperada por conjuntos de T012 en la misma instantánea REPEATABLE_READ; incluir ENERGY_REVIEW/ENERGY_REJECTED en EXCLUDED y en cómputo del límite de filas, no solo en renderers. Renderizar/archivar fuera de esa transacción; no reescribir reportes emitidos.
- [X] T035 [US2] Actualizar `BE/modules/consumption/infrastructure/report/ConsumptionLabels.java`, `ConsumptionPdfRenderer.java` y `ConsumptionExcelRenderer.java` del mismo directorio: cantidades/energía por revisar y rechazadas fuera de indicadores, cantidad/energía de aprobadas sobre umbral en PDF y columnas Excel «Excepción de energía» y «Justificación» con decisión vigente; conservar ausencia frente a cero y metodología de 002.
- [X] T036 [US2] Exponer tres totales en `BE/modules/consumption/api/response/ConsumptionSummaryResponse.java`, regenerar `sarc-bakend/docs/api/openapi.json` y `FE/core/api/generated/types.gen.ts` incluyendo canApproveSource de T031; ajustar `FE/features/consumption/domain/consumption.ts` para campos ampliados y awaitingEnergyReview. Conservar contrato LINKABLE/linkableRows y no editar tipos generados manualmente (FR-006, FR-009).
- [X] T037 [US2] Actualizar `FE/features/consumption/presentation/dashboard-page/dashboard-page.ts`, `dashboard-page.html` del mismo directorio, `FE/features/consumption/application/sessions-facade.ts`, `FE/features/consumption/presentation/sessions-page/sessions-page.ts` y `sessions-page.html` del mismo directorio: tarjetas exclusivas desde resumen backend, sin derivar totales de página filtrada; ambos menús de fuente usan canApproveSource&&canEdit, no solo AWAITING_APPROVAL. Mensajes respetan estado devuelto y no anuncian «ya suma» si sigue no oficial; recargar lista/resumen sin perder filtros; informar awaitingEnergyReview aparte sin doble conteo.
- [X] T038 [US2] Ampliar `FE/features/consumption/data-access/consumption-api.spec.ts` y `FE/features/consumption/application/sessions-facade.spec.ts`; crear `FE/features/consumption/presentation/dashboard-page/dashboard-page.spec.ts` y `FE/features/consumption/presentation/sessions-page/sessions-page.spec.ts` tras T036–T037. Probar categorías exclusivas, aprobado como subconjunto, filtro energético separado del resumen, ambos menús permiten fuente con ENERGY_REVIEW/TEST si canApproveSource y canEdit, lectores no escriben y no hay éxito oficial falso. Mantener cero/ausencia (FR-006, FR-009–010).

**Checkpoint**: SC-002 pasa y el flujo US1 conserva SC-001/003/004. D10 solo se considera terminado con las combinaciones y evidencias de US2, no únicamente con el diálogo.

## Phase 5: User Story 3 - Ver todos los empatados en el Top 5 (Priority: P1)

**Goal**: ranking por energía oficial con empates completos, posiciones compartidas y salto, igual en tablero e informes (FR-012–016).
**Independent Test**: preparar equipos con cuarto, quinto y sexto empatados y séptimo menor; mostrar seis con posiciones 1,2,3,4,4,4 y nota. Probar también tres empatados desde el quinto (siete filas), sin empate, menos de cinco y todos empatados.

### Tests for User Story 3

- [X] T039 [P] [US3] Crear `BT/modules/consumption/application/service/ConsumptionCalculatorTest.java` con ranking completo, empate 4–6 y empate 5–7, sin empate de corte, empate 2–3, menos de cinco, todos iguales incluso 0, diferencia en tercer decimal y escalas BigDecimal distintas iguales por `compareTo`; orden por nombre, nombre repetido orden estable sin excluir UUID y exclusión previa de equipos sin energía de 002 (FR-012–016; SC-005–007).
- [X] T040 [US3] Ampliar `BT/modules/consumption/api/ConsumptionApiTest.java`: cambiar UUID de los empatados no cambia miembros del Top, `position` compartida y salto en ranking completo, mismos equipos/cifras/nota en tablero y PDF y ranking completo Excel; consultas con mismos filtros y menos de cinco sin nota (FR-012–016; SC-005–007).

### Implementation for User Story 3

- [X] T041 [US3] Quitar el parámetro de límite y `setMaxResults` de `byEquipment` en `BE/modules/consumption/domain/repository/EnergySessionRepository.java` y `BE/modules/consumption/infrastructure/persistence/EnergySessionRepositoryAdapter.java`, adaptando llamadas en `BE/modules/consumption/application/service/ConsumptionCalculator.java` y `ConsumptionReportService.java` del mismo directorio; conservar predicado de oficiales y `having` de energía conocida de 002.
- [X] T042 [US3] Añadir `position` a `BE/modules/consumption/application/result/RankedEquipment.java` e implementar en `BE/modules/consumption/application/service/ConsumptionCalculator.java` el orden «energía descendente; nombre del equipo; identificador solo para nombres iguales», igualdad energética por `compareTo`, posición «1, 2, 3, 4, 4, 4, 7» y Top «position <= 5»; reutilizar ranking completo y los nombres actuales del directorio sin acoplar módulos ni consultas por equipo.
- [X] T043 [US3] Añadir `Ranked.position` en `BE/modules/consumption/api/response/ConsumptionSummaryResponse.java`, regenerar `sarc-bakend/docs/api/openapi.json` y `FE/core/api/generated/types.gen.ts`; comprobar que `top` permite más de cinco sin truncarlo en contrato ni consumidores.
- [X] T044 [US3] Actualizar `BE/modules/consumption/infrastructure/report/ConsumptionPdfRenderer.java` y `ConsumptionExcelRenderer.java` del mismo directorio: numerar por `position`, preservar ranking completo con saltos, Top ampliado y nota de empate solo si `top.size() > 5`; adaptar paginación para siete o todos los equipos empatados y conservar notas de exclusión por ausencia de 002.
- [X] T045 [US3] Actualizar `FE/features/consumption/presentation/top-equipment/top-equipment.ts` y consumidores de `FE/features/consumption/domain/consumption.ts`: posiciones recibidas sin renumerar por índice, sin truncar lista; adaptar altura fija del gráfico para seis/siete/todos empatados, orden recibido y nota solo con más de cinco; conservar nota independiente de equipos sin energía.
- [X] T046 [US3] Crear `FE/features/consumption/presentation/top-equipment/top-equipment.spec.ts` tras T045 para casos de seis/siete/todos empatados, cinco sin nota, menos de cinco y posiciones con salto; ampliar `FE/features/consumption/data-access/consumption-api.spec.ts` para conservar `position` y todos los elementos del contrato (FR-012–016; SC-005–007).

**Checkpoint**: SC-005–007 pasan. D14 no admite exclusión de empatados por UUID; este solo estabiliza nombres idénticos después de determinar pertenencia por energía.

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: cerrar regresiones, accesibilidad y evidencia sin modificar datos de desarrollo.

- [X] T047 [P] Crear `sarc-bakend/docs/adr/0010-revision-de-energia-y-empates.md` con referencia a FR/SC, precedencia de estados, decisiones de solo inserción, concurrencia, límites de almacenamiento, orden de correcciones y posiciones del ranking; documentar dependencia y despliegue coordinado backend/frontend.
- [X] T048 [P] Actualizar Javadoc en `BE/modules/consumption/domain/policy/SessionRules.java`, `BE/modules/consumption/domain/model/session/EnergySession.java` y `BE/modules/consumption/application/service/ConsumptionCalculator.java` para describir observación revisable, controles independientes y ranking ampliable, sin modificar reglas adicionales.
- [X] T049 Añadir y ejecutar en `BT/modules/consumption/api/ConsumptionApiTest.java` el lote mixto G1 exacto de `DOC/quickstart.md`: normal, cero real, ausencia energía/término, alta, ambigua, LINKABLE, duplicada, corrección aprobada y error bloqueante. Verificar nueve clasificaciones, siete sesiones, cinco oficiales/34 kWh conocidos/una ausente, una ENERGY_REVIEW y una pendiente; tablero/PDF/Excel iguales. Dos reimportaciones no añaden sesiones/revisiones/decisiones; H aprobada o rechazada conserva decisión y mensajes neutros. Junto a `BT/modules/consumption/infrastructure/persistence/EnergySessionSchemaTest.java`, preservar datos V009/archivos/errores históricos. Ejecutar además suite 002 sin debilitarla (G1, I1, A1; FR-011/017; SC-001–004/007).
- [X] T050 Regenerar y verificar el contrato definitivo `sarc-bakend/docs/api/openapi.json` y `FE/core/api/generated/types.gen.ts` tras las tres historias; ejecutar `BT/architecture/OpenApiContractTest.java` sin modo update y `BT/architecture/ArchitectureTest.java` para comprobar sincronización y fronteras modulares.
- [X] T051 Ejecutar `./mvnw test` de `sarc-bakend/pom.xml` y `npm test -- --watch=false`, `npm run lint`, `npm run build` de `sarc-frontend/package.json`; comprobar presupuesto de 500 kB si sigue vigente y registrar resultados en `DOC/quickstart.md`, incluidos regresiones 001/002, fuentes, prueba, vinculación, redondeo y ranking sin empate (SC-007).
- [X] T052 Ejecutar `DOC/quickstart.md` en sandbox de T002, creando si es necesario `BT/Feature003BrowserSandbox.java` opt-in, fuera de pruebas ordinarias: ambas fuentes, decisiones sucesivas/obsoletas/iguales concurrentes, corregir solo término conserva aprobación y rechazo, cambio alto reinicia, categorías y filtro independiente, ambos menús y órdenes de aprobación, ranking. Revisar teclado/foco, etiquetas, WCAG AA y AXE sin dependencia productiva; guardar evidencia sintética en `DOC/evidence/` y documentar limitaciones reales.
- [X] T053 Registrar en `DOC/quickstart.md` la matriz final FR-001–017/SC-001–007 con pruebas y resultados, recursos de sandbox utilizados y detenidos; retirar únicamente procesos/contenedores/almacenamiento creados para QA y comprobar que la base local y servicios preexistentes siguen intactos. No hacer commit, push ni desplegar sin solicitud del usuario.

## Dependencies & Execution Order

### Phase Dependencies

```text
Feature 002 implementada y verificada
  -> Setup T001–T003
  -> Foundational T004–T005
  -> US1 T006–T028
  -> US2 T029–T038
  -> US3 T039–T046
  -> Polish T047–T053
```

- No aplicar V010 sobre la base local para preparar esta documentación.
- T009–T012 preceden a la prueba de arranque T013. Las pruebas iniciales pueden fallar antes de existir los nuevos tipos; confirmar el fallo funcional pertinente cuando ya compilen.
- T014–T021 completan el backend de US1 antes de regenerar contrato T022; frontend T023–T026 consume ese contrato.
- T010–T013 establecen sessionVersion de concurrencia e historial separado de sessionRevision; T019 prueba incremento aun en decisiones iguales antes de usar la versión devuelta en T020–T025.
- T027 y T028 pueden realizarse en paralelo tras T026 porque modifican archivos distintos.
- US2 necesita decisiones de US1. T031 no es una segunda regla de estado: integra la única regla creada en T015 con operaciones existentes.
- T031 precede T036 (contrato canApproveSource); T037 consume ese contrato y T038 prueba ambos menús/filtros/resúmenes. T034 incluye el límite del informe antes de comprobar igualdad de reportes en T030 y el cierre global.
- US3 es funcionalmente independiente y puede probarse con sesiones ordinarias de energía conocida. Se agenda tras US2 porque comparte calculadora, repositorio, DTO, contrato, informes y pruebas de integración; no editar esos archivos simultáneamente.
- Después de cada historia, ejecutar sus pruebas dirigidas. Antes de entregar, ejecutar T049–T053 para la feature completa.

### Parallel Opportunities and Examples per Story

- **US1**: T006 (parser) y T007 (dominio) son independientes. Tras T026, T027 (diálogo) y T028 (API/facade) no comparten archivos. T008 se ejecuta aparte porque las fases siguientes también editan `ConsumptionApiTest.java`.
- **US2**: T029 (dominio) puede escribirse junto a T030 (integración), sin ejecutar modificaciones de producción sobre sus archivos al mismo tiempo. T035 (renderers) y T037 (dashboard) solo pueden avanzar juntos después de sus contratos respectivos; ambos se dejan secuenciales en la lista por claridad.
- **US3**: T039 (calculadora) y T040 (integración) tienen archivos distintos. Tras T043, T044 (informes) y T045 (componente) pueden avanzar de forma independiente; T046 espera T045.
- **Cierre**: T047 (ADR) y T048 (Javadoc) pueden realizarse en paralelo, sin cambios simultáneos de producción sobre esos archivos.

Estas oportunidades describen dependencias; no requieren delegación ni agentes para ejecutar el plan.

## Implementation Strategy

### MVP First

1. Verificar 002 y completar preparación.
2. Entregar US1 como demostración de revisión trazable con equipo y fuente válidos.
3. Completar US2 antes de declarar D10 satisfecho: aprobación de energía no sustituye otros controles.
4. Completar US3 y verificaciones finales antes de considerar terminada Feature 003.

### Incremental Delivery

- **US1**: lectura alta conservada, decisión justificada e historial, sin duplicados.
- **US2**: controles independientes, categorías e informes coherentes.
- **US3**: todos los empatados del corte y posiciones compartidas.
- **Cierre**: prueba conjunta, accesibilidad, regresión, archivo de evidencias y limpieza del sandbox.

### Traceability

| Requisito | Tareas principales |
| --- | --- |
| FR-001–002 | T006, T009–T016 |
| FR-003–005 | T007–T013, T018–T025, T027 |
| FR-006 | T015, T029–T032, T038 |
| FR-007–008 | T007–T008, T012, T015–T020 |
| FR-009 | T030, T033–T038 |
| FR-010 | T008, T016–T017, T021–T028 |
| FR-011 | T006, T014, T029–T030, T051 |
| FR-012–016 | T039–T046 |
| FR-017 | T002, T008–T013, T030, T034, T049–T053 |
| SC-001 | T006, T008, T014–T016 |
| SC-002 | T029–T038 |
| SC-003 | T007–T008, T012, T019–T020, T027, T030 |
| SC-004 | T008, T016, T052 |
| SC-005–006 | T039–T046 |
| SC-007 | T039–T040, T049–T052 |

## Notes

- La spec y el plan están redactados antes de la implementación; ningún PASS de diseño equivale a una prueba ejecutada.
- Solo la decisión vigente puede gobernar la lectura actual; el historial de decisiones y las revisiones previas se preservan.
- Conservar decisión significa misma energía normalizada actual; volver a un valor aprobado en el pasado después de otro cambio no recupera esa aprobación. Ninguna decisión reemplaza vínculo, fuente, prueba o coherencia temporal.
- No considerar como hechas las pruebas aquí descritas: todas las casillas permanecen pendientes. Validación documental no sustituye pruebas de producto ni análisis transversal de consistencia.
- Si la dependencia 002 o una contradicción de los artefactos impide ejecutar una tarea con precisión, registrar el punto y resolverlo antes de modificar comportamiento; no elegir una regla nueva por conveniencia técnica.
- No realizar cambios funcionales en inventario, usuarios ni otros módulos para completar esta feature.
