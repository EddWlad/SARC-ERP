# US4 — avance, no aceptación final

2026-11-06. No marca tareas completas de US4.

Corregida compilación del fixture de recuperación. Añadidas pruebas de rechazo durable de cola, retry con versión origen, resultado STALE_VERSION, decisión REQUIRES sin cambios en revisiones y avance de versión de cabecera, repetición sin evento duplicado. PostgreSQL18 desechable, ImportJobs controlado; no BD local.

Comando: mvnw.cmd -o -Dtest=UptimeTemporalTransitionTest,UptimeTemporalTransitionApiTest,InterruptedUptimeImportRecoveryTest,ArchitectureTest test
Resultado: target/feature006-us4-recovery.log, exit0,15pruebas (API4/modelo3/recuperación1/arquitectura7). No certifica todavía originales XLSX, colisiones concurrentes ni todo FR020.

Añadida detección previa de otros candidatos temporales del mismo conector/fuente, inspeccionados fuera de transacción. Evita asignar automáticamente una identidad compartida al primer candidato. Falta prueba específica con originales completos y concurrencia.

Pendientes T054–T081: ampliar evidencia/transición real con originales sintéticos/cadena completa, colisiones/atomicidad/concurrencia, historial operativo, reportes, UI de revisión, tipos OpenAPI, gates completos, QA visual/rendimiento/Excel y rollout. No publicar. Sin commits/merge/push. Hooks inexistentes.
