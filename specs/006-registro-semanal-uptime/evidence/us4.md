# US4 — Transición durable, conciliación y reportes

Fecha: 2026-11-06. PostgreSQL18 Testcontainers; almacenamiento simulado. No se ejecutó transición ni migración contra dev.

## Implementación y prueba

- Solicitud persistente por actor/Idempotency-Key y hash canónico de IDs/versiones; mismo contenido reordenado devuelve el trabajo existente, distinto contenido409. ImportJobs recibe un trabajo ya confirmado.
- Recuperación QUEUED/RUNNING→INTERRUPTED. Retry explícito versionado conserva terminales y versiones de origen. Estado/resultados paginados salen de PostgreSQL, no del Runnable.
- Cada decisión confirma cabecera/evento/auditoría/resultado en una transacción corta. IO/checksum/parser fuera de escritura. Prueba de fallo de auditoría verifica rollback conjunto; caída posterior al último resultado solo completa al reintentar.
- Prueba de cadena completa con originales XLSX/CSV sintéticos, checksum/hoja/fila/crudos/valores. Ausencia, inaccesibilidad, discordancia y cadena mixta conservan REQUIRES_RECONCILIATION. Dos candidatos con destino igual permanecen ambiguos: sin ganador por orden.
- Conciliación manual exige versión/motivo/evidencia; colisión409; no cambia revisión efectiva, valores, validaciones, exclusiones ni históricos. Un evento sin cambio de metadata también incrementa versión de cabecera.
- API crea202/repite terminal200/colisión409/invariante422. Roles/CSRF y proyección de lectores cubiertos con accesos directos. Historial temporal visible solo a operadores.
- UI usa Revisión/detalle/diálogo existentes. Clave estable, workId recuperable en sesión, progreso backend, paginado, retry y polling que termina al salir o finalizar. Borrador de conciliación permanece ante rechazo del backend y pide confirmación al descartarlo.
- Reportes separan REPORT_MEAN y ponderación real; sin límites ni minutos evaluados ficticios. ReportPublication mantiene contratos de otros módulos. PDF conserva branding/gráficos oficiales; Excel conserva siete hojas y valores numéricos.

## Evidencia ejecutada

Logs en sarc-bakend/target: feature006-us4-base.log (13), us4-recovery.log (15), us4-evidence.log (41), us4-expanded.log (37), us4-terminal.log (12), reports.log (21), report-reference.log (4). Son ejecuciones sucesivas con solapamiento, no sumar como pruebas distintas.

UptimeTemporalEvidenceTest, UptimeTemporalTransitionTest, UptimeTemporalTransitionApiTest, InterruptedUptimeImportRecoveryTest, UptimeMigrationTest, UptimeSecurityTest, UptimeReadProjectionTest, UptimeReportConsistencyTest/PrivacyTest/ApiTest y ReportPublicationTest contienen los escenarios. Los nombres exactos y conteo global se verifican en surefire-reports y automated-checks.md.

Angular: feature006-temporal-ui.log, dos componentes/dos pruebas verdes; suite posterior53 archivos/218 pruebas verdes. Pruebas de facade incluyen rechazo, clave estable, estado persistente y detención del polling.

El proveedor privado real no se certifica con estas pruebas; sigue pendiente por decisión expresa del usuario. Archivos emitidos permanecen inmutables: la migración/regresión compara huellas, UUID y metadata; los renderizadores solo producen documentos nuevos.
