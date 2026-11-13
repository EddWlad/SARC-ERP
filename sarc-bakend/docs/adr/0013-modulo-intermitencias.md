# ADR 0013: módulo de intermitencias de puntos de carga KIA

**Estado**: aceptada, feature 008 (especificación en `specs/008-intermitencias-kia/`).

## Contexto

Los analistas registran en un Excel las ventanas en que un punto de carga KIA estuvo fuera de línea. La feature 008 las lleva a SARC: captura manual, importación con vista previa editable, tablero, informes y una disponibilidad estimada restringida a Analistas durante la prueba. Debe convivir con Consumo, Inventario y Uptime sin alterar sus cifras.

## Decisión

- **Módulo propio** `modules/intermittency`, con las capas de `uptime` y prefijo de tabla `itm_`. Migración `V018`. No comparte clases con `uptime` ni con `consumption` (ArchUnit lo comprueba); lo común está en `shared`.
- **Puerto `KiaPointDirectory`** en `shared/catalog`, implementado por Inventario (`KiaPointDirectoryService`). No se amplía `EquipmentDirectory`: ese puerto resuelve identidades externas para Consumo y Uptime, y un punto KIA se resuelve por Código asignado, con alcance (red KIA, tipo punto de carga) y estado propios. Ampliarlo mezclaría dos contratos con ciclos de cambio distintos.
- **Cabecera y revisiones**: `itm_window` guarda la ventana vigente y `itm_window_revision` el historial inmutable. Las ventanas nunca se borran; se anulan.
- **No solapamiento en la base**: restricción de exclusión `btree_gist` sobre `(equipment_id, tstzrange(start_at, coalesce(end_at, 'infinity')))` para todo estado no anulado, más un índice único parcial contra duplicados. El principio II de la constitución se cumple porque la regla vive también en el dominio y en `WindowWriteGuard`; la base es la última barrera, no la única.
- **Alternativa si `btree_gist` no se puede crear**: sustituir la restricción por la comprobación en `WindowWriteGuard` bajo el bloqueo por equipo y por un disparador de respaldo. Si V018 todavía no se aplicó en una base persistente se edita; si ya se aplicó, el cambio va en `V019` (principio III: las migraciones no se reescriben).
- **Concurrencia**: `pg_advisory_xact_lock(hashtext('itm:' || equipment_id))` por equipo, tomado en orden ascendente de `equipment_id` cuando una operación toca varios, y bloqueo optimista `@Version` en cabeceras y filas de importación. Todas las escrituras de ventanas pasan por `WindowWriteGuard`.
- **Permisos**: `IntermittencyPermissions.EDITORS` (Administrador y Analista) para escribir, y `IntermittencyPermissions.AVAILABILITY` como constante aparte, aunque hoy tenga la misma expresión, para poder cambiar la visibilidad de la disponibilidad sin tocar la edición. Los permisos se evalúan con los roles vigentes de cada petición, de modo que un usuario con varios roles recibe la unión de sus permisos.
- **Tiempo**: las ventanas son `TIMESTAMPTZ` en minuto exacto. Los días y las porciones por período se calculan en `America/Guayaquil` con `PeriodSlicer`, puro y sin Spring; el fin del período es exclusivo.
- **Nada derivado se persiste**: indicadores, Top 10 y disponibilidad se calculan en `IntermittencyCalculator`, fuente única del tablero y de los informes.

## Consecuencias

- Una fuente nueva de ventanas exige pasar por `WindowWriteGuard`.
- Cambiar quién ve la disponibilidad es cambiar una constante.
- La restricción de exclusión exige la extensión `btree_gist` y un rol de aplicación que pueda crearla; se ensayó con la copia de producción (tarea T006, 2026-11-13) antes de la primera versión candidata.

## Estado de la implementación

Registrado el 2026-11-13, con las historias US1 a US6 implementadas:

- El módulo `intermittency` y el puerto `KiaPointDirectory` (con `allKiaPoints()` y `createdAt` desde US6) están en producción de código, no publicados.
- La restricción de exclusión con `btree_gist` pasa en PostgreSQL 18 de Testcontainers (`IntermittencySchemaTest`). T006 (2026-11-13) la ensayó sobre la última copia restaurada de producción, con un rol de aplicación no superusuario propietario de su base: V018 tardó menos de 1 s, creó `btree_gist` y no cambió las tablas existentes.
- El bloqueo consultivo por equipo y por día vive en `shared/persistence/AdvisoryLocks`.
- La disponibilidad estimada usa `IntermittencyPermissions.AVAILABILITY`, constante aparte de `EDITORS`, y se evalúa con el estado actual de cada punto desde su fecha de alta (aclaración 20 de CC-PRD-008).
