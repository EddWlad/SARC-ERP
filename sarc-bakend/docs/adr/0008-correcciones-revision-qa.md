# ADR 0008 - Correcciones de la revision QA de RF-001 a RF-003

Estado: aceptada (27-oct-2026). Responde a `documents/REVISION_QA_RF001_RF003_2026-10-26.md`. Modifica las ADR 0005 (consumo e importacion) y 0007 (motor de importacion).

## Contexto

La revision QA reprodujo hallazgos en la importacion de consumos, los reportes y el inventario. Tres de ellos exigian decidir una regla de negocio; las decisiones las tomo el usuario.

## Decision

1. **Cambio de cargador: se bloquea y se reasigna aparte (QA-01).** Una sesion que vuelve a llegar con otro cargador (otra serie en Wallbox, otro sitio o cargador en Enerlink) se clasifica `EQUIPMENT_CHANGED`. Aprobar una correccion no la mueve: el analista debe marcar "Reasignar". Al aplicar, la version anterior conserva el equipo previo (`energy_session_revision.equipment_id`) y la fila queda `REASSIGNED`. `EnergySession.correct` rechaza un cambio de cargador.

2. **La aprobacion vale para la revision revisada (QA-02).** La fila guarda la revision de la sesion que vio el analista. Si al confirmar la sesion ya cambio (otro lote se aplico antes), la fila queda `SKIPPED` con el motivo en `outcome_detail`; nunca pisa una correccion posterior.

3. **Fuente no preferente vinculada despues: queda por aprobar (QA-03).** Nuevo estado `AWAITING_APPROVAL`. "Vincular pendientes" valida solo las sesiones de la fuente preferente del equipo; las demas pasan a `AWAITING_APPROVAL` y no suman hasta que un analista las aprueba (`POST /api/v1/energy/sessions/{id}/source-approval`). El tablero y los reportes las informan aparte. Las sesiones ya vinculadas antes de esta regla se marcan aprobadas en la migracion para no alterar historicos.

4. **Reportes emitidos con copia archivada (Lectura-01, RF-RPT-08).** Nuevo modulo `reporting` con la tabla `report_issued`: tipo, formato, alcance, SHA-256, tamano y referencia a una copia privada del archivo. Consumo e inventario lo usan a traves del puerto `shared/catalog/ReportArchive`. La copia se descarga con una URL firmada de 5 minutos (`GET /api/v1/reports/issued/{id}/download`). Solo sin almacenamiento configurado (desarrollo) se emite con la huella y sin copia; si el almacenamiento existe pero falla, el reporte no se entrega (`REPORT_ARCHIVE_UNAVAILABLE`, 503) para no emitir un documento sin respaldo.

5. **Un reporte, una instantanea (QA-05).** Los datos de un reporte se leen en una transaccion `REPEATABLE READ` de solo lectura; el renderizado y el archivo ocurren fuera de ella.

6. **Fallos de cola en transaccion propia (QA-06).** Si la cola de importaciones rechaza el trabajo, el lote se marca `FAILED` en una transaccion `REQUIRES_NEW` (el rechazo ocurre tras confirmar, sin transaccion activa).

7. **Auditoria con valores anteriores y nuevos (QA-09).** Editar un equipo o su horario, a mano o por carga masiva, audita `campoFrom`/`campoTo` de lo que cambio. El horario incluye intervalos y excepciones con su nota. De UID/PUK y URL OCPP solo se registra que cambiaron.

8. **Errores de integridad sin datos en los logs (QA-10).** El manejador registra la restriccion, el SQLSTATE y el traceId; el driver no incluye el detalle con valores (`logServerErrorDetail=false`).

## Consecuencias

- Migracion `V007`: columnas nuevas en sesiones, revisiones, filas y lotes; estados y clasificaciones ampliados; tabla `report_issued`.
- La auditoria de reportes cambia de entidad: `CONSUMPTION_REPORT_GENERATED` e `INVENTORY_REPORT_GENERATED` apuntan al `ISSUED_REPORT` creado.
- Las consultas automaticas del frontend solo corren si la persona interactuo en los ultimos 5 minutos: no mantienen viva una sesion sin uso (RF-001.10).
- El frontend muestra "Por aprobar", "Reasignar", la pagina "Reportes emitidos", la hora en America/Guayaquil y se actualiza solo mientras esta a la vista.
