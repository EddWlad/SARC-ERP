# Data Model: Feature-005 entregada

Modelo existente V013–V015; sin migración adicional en el cierre. Convenciones UUID, timestamps UTC/TIMESTAMPTZ, NUMERIC para porcentajes, JSONB para observaciones/crudos y @Version para cambios. No se implementa el modelo temporal nuevo de 006.

## Relaciones

asset_equipment → asset_connector → upt_measurement → upt_measurement_revision / upt_measurement_exclusion. upt_import_batch → upt_import_row; revisión mantiene lote. upt_connector_alias vincula etiqueta de fuente a UUID de conector sin crear otra identidad.

## Cabecera upt_measurement

Conector/equipo/fuente inmutables, period_start/end no nulos con fin posterior, zona, latest_revision, effective_revision nullable, excluded, auditoría/version. UNIQUE conector+fuente+inicio+fin. Índices por conector/fin y fin. No se usan referencia semanal ni fechas nulas en 005.

## Revisiones

Valores de cuatro estados nullable, porcentaje informado NUMERIC(12,4), reading_class, period_type, review_state, observaciones JSONB, justificación, origen/lote/crudos, etiquetas del hecho, confirmación exacta y autor/momentos de ingreso/validación/supersesión. UNIQUE measurement/revision.

V014 añade uptime_basis STATES/REPORTED_PERCENT: esta última requiere porcentaje y Ocupado vacío. Valores numéricos que no caben como minutos enteros se conservan en source_values y observan. Corrección exige motivo; una validada anterior sigue vigente hasta validar la nueva. Exclusión/restauración agrega eventos con justificación y versión, no elimina.

## Lotes, filas y alias

BaseImportBatch, checksum/original privado, estado, tipo móvil declarado, contadores, autor/confirmación exacta. V014 añade período declarado al lote. Filas conservan clasificación/outcome, identidades fuente/resueltas, valores, observaciones/issues, aprobada/motivo y referencia a medición/revisión existente; hoja/fila y uptime_basis desde V014. V015 añade propuesta de conector nullable.

Alias UNIQUE fuente/equipo/label_key, active/version; cambios auditados sin tocar mediciones existentes. Reimportación y vinculación usan la misma comparación de originales/valores.

## Proyecciones

MeasurementFact usa revisión vigente, o última si nunca se validó; included exige validada, no excluida y medible. Tiempo evaluado de cuatro estados; REPORTED_PERCENT usa duración del período declarado para ponderar según 005. UptimeCalculator construye KPIs, cobertura, ranking y equipos sin mezclar tipos.

UptimeReport incorpora corte, período, bloques oficial/provisional, semanas/tendencia, cobertura, exclusiones y pendingLink. Es un modelo de lectura, no nueva tabla. Los renderizadores reciben el mismo snapshot; ReportArchive conserva bytes y metadata. ReportPublication admite criterios de uptime de forma aditiva.

## Compatibilidad

V001–V012 intactas; V013 crea módulo, V014 informe semanal, V015 propuesta. No alterar fechas/aprobaciones/revisiones históricas por el cambio de alcance futuro. Preservar snapshots/UUID/bytes. Pruebas de migración y concurrencia solo en Testcontainers. La limitación de intervalo declarado del Excel queda explícita y será tratada en 006.
