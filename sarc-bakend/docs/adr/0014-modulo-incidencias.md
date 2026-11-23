# ADR 0014: módulo de incidencias, tickets y tiempos

**Estado**: aceptada, feature 010 (especificación en `specs/010-incidencias-tickets-tiempos/`).

## Contexto

Los analistas llevan los tickets de incidencias en un Excel y publican los avisos a mano. La feature 010 los lleva a SARC: un ticket por elemento afectado, estados Abierto y Cerrado con hitos T0 a T4, episodios por reapertura, mensaje para copiar a WhatsApp, y dos informes (Control de tickets y Gestión de incidencias). Debe convivir con Inventario, Consumo, Uptime e Intermitencias sin alterar sus cifras, y debe poder reconstruir qué mostraba un informe y cuál era el estado de un ticket al final de un período.

## Decisión

- **Módulo propio** `modules/incident`, con las capas de `intermittency` y prefijo de tabla `inc_`. Migración `V021`, solo aditiva. No comparte clases con otros módulos (ArchUnit lo comprueba, y ningún módulo depende de `incident`); lo común está en `shared`.
- **Puerto `IncidentAssetDirectory`** en `shared/catalog`, implementado por Inventario (`IncidentAssetDirectoryService`). No se amplía `EquipmentDirectory`, que resuelve identidades externas para Consumo y Uptime, ni `KiaPointDirectory`, que es propio de los puntos KIA. Incidencias necesita equipos de cualquier red y tipo con su ubicación, enlace de Maps y conectores con su posición, y nunca UID/PUK, URL OCPP ni serie (research R1).
- **Puerto `IncidentStaffDirectory`** en `shared/security`, implementado por Identidad (`IncidentStaffDirectoryService`). Un Analista debe poder elegir responsable y relevo, y `/api/v1/users` es solo del Administrador; abrirlo expondría datos de administración de cuentas. El puerto da lo mínimo: editores activos, validar uno y nombres. No expone correo, contraseña, bloqueo ni roles (research R8).
- **Numeración**: contador de una sola fila (`inc_ticket_numbering`, `CHECK (id = 1)`) que se incrementa con `UPDATE … RETURNING` en la misma transacción que crea el ticket. Sin hueco ni duplicado, aun con creaciones simultáneas. Un número asignado no se reasigna. El contador lleva un sello de ambiente (`PRODUCTION` o `TEST`) que debe coincidir con `sarc.incidents.numbering.environment`; la propiedad es opcional y, si falta, la aplicación arranca pero no se configura la numeración ni se crean tickets. La creación es idempotente por `client_request_id` (research R2).
- **Hitos y revisiones de solo anexión**: `inc_milestone` guarda el valor vigente y `inc_milestone_revision` el historial inmutable, con justificación, autor y momento. Los eventos nunca se borran; se anulan (research R3).
- **Estado al final del período**: se reconstruye con los hitos y revisiones vigentes al corte, no con el estado actual. Cada informe emitido registra sus filas y una huella (`rowsSha256`) en `ReportArchive`, de modo que una corrección posterior cambia los informes nuevos pero no los emitidos (research R5).
- **Referencias de tiempo versionadas**: `inc_reference_version` e `inc_reference_rule` son inmutables, y cada episodio guarda la versión vigente en su T0. Un cambio de referencias crea una versión nueva sin tocar episodios ni informes anteriores. Son referencias operativas, no SLA contractual (research R6).
- **Evidencias**: imágenes y PDF, con la firma binaria como criterio (`ImageFormat` y el nuevo `DocumentFormat`), en `FileStorage`, con URL firmada de vida corta.
- **Informes**: `INCIDENT_CONTROL` e `INCIDENT_MANAGEMENT` se agregan a `IssuedReportKinds` como no restringibles. Las cifras del tablero, del PDF y del Excel salen de una sola consulta (`IncidentReportQuery`).
- **Permisos**: `IncidentPermissions.EDITORS` (Administrador y Analista), `READERS` (los cinco roles) y `NUMBERING` (Administrador). Se evalúan en cada operación, con la unión de permisos de un usuario con varios roles.
- **Sin notificaciones**: la campana es una feature transversal posterior. Los eventos que la alimentarán quedan marcados con `notifiable` (research R17).

## Consecuencias

- Una fuente nueva de tickets debe pasar por `TicketCreationService`, que es quien asigna el número.
- Cambiar una referencia de tiempo es un cambio controlado del sistema: una migración con una versión nueva, nunca una edición.
- Identidad e Inventario solo ganan implementaciones de solo lectura de los puertos nuevos.
- V021 se ensaya sobre una copia restaurada de producción antes de publicar (T009).
- El número inicial de producción (P-01) no se escribe en ningún archivo: lo configura un Administrador después de la publicación.

## Estado de la implementación

Registrado el 2026-11-21, con la fase fundacional del backend (T001 a T019) hecha y las historias sin empezar:

- `V021` pasa `IncidentMigrationTest` en PostgreSQL 18 de Testcontainers: solo agrega tablas `inc_*`, la fila de numeración vacía y la versión `PROTOCOLO-FIRMADO-1`. Falta ensayarla sobre una copia restaurada de producción (T009).
- Los puertos `IncidentAssetDirectory` e `IncidentStaffDirectory` están implementados por Inventario e Identidad, con pruebas.
- El agregado `Ticket` (episodios, hitos, revisiones, eventos, relaciones) y la numeración (`TicketNumberingRepository`, con la prueba de concurrencia y de bloqueo) están hechos.
- `IncidentPermissions` vive en `application/service`, no en `infrastructure/security`: `ArchitectureTest` prohíbe que `application` dependa de `infrastructure`. El ajuste de ambiente pasa a los casos de uso por el puerto `IncidentSettings`.
- Dato que conviene recordar: PostgreSQL ordena los `uuid` por bytes sin signo y `UUID.compareTo` de Java compara con signo. `TicketRelation` ordena el par por el texto del UUID.

Actualizado el 2026-11-23, con las siete historias hechas y sin publicar:

- Hechas US1 a US7: creación, seguimiento, chatbot y elementos del sitio, mensaje para WhatsApp, cierre y reapertura, informe «Control de tickets» y tablero con reporte de gestión. Nada está publicado ni en commit.
- Los informes comparten `IncidentReportQuery` e `IncidentManagementQuery` (un solo corte, lectura `REPEATABLE READ`) y las reglas puras `TicketStateAt`, `MilestonesAsKnownAt` e `IncidentKpiCalculator`. El corte es inclusivo y cada emisión guarda `rowsSha256` en la auditoría.
- Con 5.000 tickets y 50.000 eventos sintéticos (`Feature010PerformanceBench`), el listado con tres filtros, el resumen de un año, los informes y la creación cumplen sus límites. Para lograrlo se cacheó la regla de referencia por versión y tipo, el tablero no lee la bitácora completa y los lotes de carga subieron a 500.
- Pendientes: ensayar V021 sobre una copia de producción (T009), el ensayo de restauración con datos de incidencias (T115) y las decisiones P-01 y P-02 del responsable.
