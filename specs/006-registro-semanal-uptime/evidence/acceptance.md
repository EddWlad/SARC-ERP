# Aceptación006 — trazabilidad de ejecución

Fecha:2026-11-06. Sandbox PostgreSQL18/Testcontainers + FileStorage simulado. No se importó el archivo del usuario ni se modificó dev. Las pruebas de navegador usan cuentas y Excel sintéticos. Ningún resultado simulado certifica proveedor real.

## Recorrido quickstart1–55

A=automatizado (API real contra PostgreSQL, dominio o componente/facade); V=navegador/inspección visual; E=Excel escritorio. La siguiente tabla identifica la prueba; el resultado global definitivo depende de automated-checks.md y visual-audit.md. No se presenta como55 pruebas manuales ni se suman ejecuciones superpuestas.

| Casos | Vía y evidencia específica |
|---|---|
|1–5|A:CaptureFacade/MeasurementForm, UptimeMeasurementApiTest, UptimeValidationApiTest; captura por conector/ref/segundos/reset, errores, provisional≠exacta. V:captura|
|6–8|A:EnerlinkUptimeParserTest, UptimeImportApiTest.weeklyModelIsImportedFromEverySheetWithTheChosenWeekAndItsUptime/referenceCannotBeConfirmedAsExactAndKeepsPreview|
|9–12|A:UptimeImportApiTest reimportingIdentical/conflictNeedsJustification, UptimeConcurrencyTest y MeasurementPeriodTest; dos identidades temporales separadas|
|13–15|A:UptimeCalculatorTest/UptimeRankingTest; media0/100,precisión fuente, ponderación conocida separada|
|16–19|A:UptimeSummaryApiTest referenceSummarySeparatesMethodsAndStaysOnGlobalLatestWhenFiltered/referenceOpeningHasNoArbitraryHistoricalCutoff/pendingLinkUsesOnlyChosenReferenceAndSectionBeforeAttributionFilters; DashboardFacade paginación/selección fija|
|20–23|A:StateComposition y UptimeCalculatorTest/ChartViewTest; fuente completa/parcial,0 y todos nulos; V:dona y leyenda|
|24|A:UptimeReadProjectionTest, detalle y revisiones autorizadas; V:detalle|
|25|A:UptimeClosureRegressionTest,SummaryApiTest,ValidationApiTest; grilla weeklyDoesNotTreatAnotherWindowAsWeeklyCapture|
|26|A:UptimeImportApiTest.oversizedConnectorNumberStaysPendingWithoutFailingOtherRows y resolución ambigua|
|27–30|A:UptimeMigrationTest.upgradeFromV015PreservesRevisionsExclusionAndArchivedPublication; UptimeTemporalEvidenceTest.originalWorkbookProvesReferenceWithoutResettingValuesOrValidation; transición API conserva valores/validación/ID y cadena efectiva|
|31–33|A:UptimeTemporalEvidenceTest realbounds/checksum/inaccesible/ausente/coords/valores/cadenaMANUAL/BLOCK; UptimeTemporalTransitionApiTest.simultaneousDestinationCandidatesNeverSelectFirstAsWinner; SummaryApiTest.reconciliationNeverChoosesAWinnerOrDistributesUnknownSectionTwice|
|34–35|A:UptimeTemporalTransitionApiTest.manualClassificationRequiresEvidenceAndNeverBypassesCanonicalIdentityWhenExcluded; UI no valida ni corrige valores antes de resolver; V:diálogo|
|36–37|A:TransitionApiTest changeDuringOriginalReadIsStaleAndDoesNotApply/unresolvedDecisionPreservesRevisionAndAdvancesHeaderVersion/keyConflictAndTerminalRepeatUseDurableRequest; recuperación persistente|
|38|A:UptimeMigrationTest y UptimeReportApiTest/PrivacyTest, ReportArchive regresión; publicación anterior intacta y nuevas autorizaciones|
|39–41|A:UptimeReportConsistencyTest,ReportPublicationTest,renderersPDF/Excel; instantánea/corte/criterios/métodos/sietehojas; V:PDF renderizado|
|42|E:Excel2608 compilación20326.20232, archivo sintético generado sin reparación; report-compatibility.md|
|43|A:UptimeSecurityTest cinco roles+unión, CSRF/create/get/retry/clasificación/historial y sin secretos; rutas frontend conservan guardas|
|44|A:DashboardFacade ignoresASlowerAnswer/refreshFailure y selección fija; ReviewFacade ticket/cola/polling|
|45–46|V:matriz cinco anchos×dos temas, teclado/foco/ARIA/contraste; visual-audit.md. Revisión focal, no certificación externa WCAG|
|47|A:ChartView.spec y ReportPublicationTest de criterios anteriores, suite frontend/backend completa|
|48|A:regresión001–005, abajo y automated-checks.md|
|49–51|A:MeasurementPeriodTest/MeasurementRules, ImportApiTest/ValidationApiTest/CalculatorTest, corrección de referencia y parser; sin NPE/nominal inventado, números inválidos y política conocida conservada|
|52–55|A:TemporalTransitionTest/ApiTest,InterruptedUptimeImportRecoveryTest: solicitud antes de encolar, atomicidadrollback, caída antes/después último item, clave reordenada/diferente, queuefull, retries concurrentes/versión origen/actor y autorización; ReviewFacade/ReviewPage/JustificationDialog|

## Regresión001–005

-001:EquipmentCodeContractTest/InventoryApiTest y ConsumptionApiTest.freeEquipmentWithoutCodeKeepsConsumptionHistoryAndArchivedReportsAcrossUsageChanges.
-002:ConsumptionApiTest.mixedBatchProtectsFeature002AndKeepsEnergyDecisionsWhenReimported/fiveSessionsWithTheSamePairLinkToOneEquipmentEvenWithDifferentWriting/reimportingTheSameFileAddsNeitherSessionsNorEnergy; sesiones incompletas y0 preservados.
-003:ConsumptionApiTest.highReadingsFromBothSourcesAreStoredAndDecisionsAreIdempotent/tiedRankingIsSharedBySummaryPdfAndCompleteExcelRegardlessOfUuids; revisión no oficial sin destrucción.
-004:ReportPrivacy/Archive y acceso por cinco roles/unión; datos operativos no llegan a lectores ni documentos restringidos.
-005:UptimeMeasurement/Import/Validation/Summary/ReadProjection/Security/Closure/Report/Ranking; rangos conocidos, semana oficial, alias/pendientes/correcciones y reporte anterior.

No marcar T124humana005: estos fixtures no son la medición real ni el proveedor de documentos.

## FR001–020 / SC001–010

La tabla de tasks.md enlaza cada requisito con IDs de tareas. Evidencia de ejecución: FR001–005→us1;FR006–011→us2;FR012–017→us3/visual-audit;FR018–020→us4/report-compatibility/rollout. SC001–006→us1–us4;SC007→visual-audit;SC008→us4/roles;SC009→suite completa;SC010→migración/transición/restore. El gate de rendimiento registra límites reales en performance.md, sin inventar cumplimiento de objetivos.

## Cierre

Cierre 2026-11-06 (sin commit/merge/push/deploy ni transición real; sandbox, servidor frontend 4203 y BD desechable detenidos).

Comprobado:
- Backend completo tras la corrección del pegado: 582 pruebas de la ejecución final, 0 fallos/errores/omitidas (informes Surefire posteriores a las 07:40; un informe antiguo de `QaReviewProbeTest`, clase que ya no existe, se ignora).
- Frontend: 53 archivos/218 pruebas, lint sin errores; build y OpenAPI sin cambios por esta corrección (solo cambió el parser, no el contrato).
- Pegado de Enerlink con duración antes de la etiqueta ("163 hrs 35 mins - Disponible"): parser (13 pruebas), API (`UptimeImportApiTest`) y diálogo en el navegador (9815/31/0/234).
- Casos 45–46: matriz 5 anchos × 2 temas × 5 pantallas sin desbordes, controles pequeños ni contraste bajo, más foco/ARIA; ver `visual-audit.md`.
- Casos 1–5 y 20–23 en navegador (captura y dona parcial); resto del recorrido por las pruebas citadas arriba.

Recorrido adicional en navegador (sandbox desechable, cuenta sintética ANALISTA, localhost:4203):
- Caso 24, detalle de medición `QA005 JAC Punto · Conector 1`: estado "Por conciliar · Horario real no informado · Semana de referencia 2026-10-26", etiqueta "En la fecha del hecho: Nombre anterior · Tipo2", 97,68 % informado, 9.815 min disponible, 0 con falla, 234 fuera de línea, sin tarjeta de Ocupado, dona con "Ocupado: No informado" e historial con la revisión 1 pendiente. "Corregir" aparece deshabilitado mientras la medición requiere conciliación.
- Casos 34–35, diálogo "Conciliar período": justificación, tratamiento (Informe sin horario real / Rango real acreditado), semana de referencia y evidencia obligatorias; al elegir rango real aparecen "Desde real" y "Hasta real" en hora de Ecuador y "Guardar decisión" sigue deshabilitado hasta completar todo. Cancelar con borrador pide confirmar ("Hay cambios sin guardar… ¿Desea descartarlos?"). Tras descartar, valores e historial quedaron intactos (sin revisión nueva): no se validó ni corrigió nada antes de resolver.
- Cola "Conciliación temporal" en Revisión: lista la medición con "Horario real no informado · Referencia 2026-10-26" y la acción "Comprobar evidencia (bloques de 500)".
- No se guardó ninguna decisión en el navegador; el guardado y sus colisiones siguen cubiertos por `UptimeTemporalTransitionApiTest`.

Pendiente, sin ocultar:
- **T079 cerrada (2026-11-06, segunda pasada en sandbox):** se recorrió en el navegador el detalle de medición (caso 24) y la conciliación temporal (casos 34–35); ver "Recorrido adicional" abajo.
- Proveedor real de documentos/Enerlink y archivo real del usuario: sin entorno QA autorizado.
- Sin prueba con lector de pantalla; axe 4.14.0 e Impeccable `detect` se ejecutaron el 2026-11-06 (ver `visual-audit.md`).
- `npm run format:check` quedó en verde con `endOfLine: auto` (ver `automated-checks.md`); objetivos de rendimiento según `performance.md`.
- T124 humana de 005 sigue abierta; estas pruebas no la cierran.
