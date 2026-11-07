# Base temporal006 — 2026-11-05

T004–T014 implementadas. MeasurementPeriod/TemporalPrecision, evaluacion numerica de referencia sin duracion, entidad/campos de importacion, evento temporal append-only y repositorio, identidades parciales y filtro de candidatos no verificados. Nueva V016; hashes V001–V015 iguales a baseline.

Fase roja dominio: MeasurementPeriodTest no compilaba por modelos nuevos ausentes; despues41pruebas dominio verdes. Fase roja esquema: UptimeMigrationTest 11pruebas/1fallo por tablas/columnas ausentes. Tras V016:43pruebas verdes.

Checkpoint final: mvnw.cmd -o -Dtest=MeasurementPeriodTest,MeasurementRulesTest,UptimeMigrationTest,ArchitectureTest test; exit0,52pruebas sin fallos (log target/feature006-foundation-final.log). Upgrade real en segundo PostgreSQL18 desechable: migrar hastaV015, sembrar revision validada y pendiente/exclusion/reporte, migrarV016, comparar filas historicas/reportes. Se preservan latest/effective/version y candidato queda REQUIRES_RECONCILIATION; restricciones de referencia/nulos/duplicado comprobadas. Transicion definitiva de evidencia sigue pendienteUS4.

Las consultas reales existentes filtran KNOWN_RANGE; lectores no acceden a candidatos sin conciliar. Captura/importacion/UI/reportes006 y proveedor real aun no aceptados. No datos reales ni BD local.
