# Data Model: precisión temporal y referencia semanal de uptime

**Revisión 2026-11-04, propuesta sin ejecutar.** Sustituye el diseño previo que asignaba una semana calendario como intervalo observado del Excel. Base existente: V013, V014 y V015. Nueva migración propuesta V016, verificando numeración al implementar. No se cambian migraciones anteriores ni tablas de Consumo/inventario.

## Entidades y relaciones conservadas

Inventario `asset_connector` → `upt_measurement` → revisiones/exclusiones. Lote `upt_import_batch` → filas `upt_import_row`; revisión conserva lote/procedencia. Alias de conector y UUID del inventario no cambian. `BaseAuditableEntity`, `@Version`, validación pendiente, revisión vigente y auditoría siguen en uso.

## Valor temporal compartido en dominio

`MeasurementPeriod` (nombre propuesto, Java puro) concentra precisión, límites reales cuando existen, zona y semana de referencia. Lo reutilizan comandos, cabecera, datos de importación y políticas; no mezcla HTTP, JPA o Angular con las reglas.

| Campo conceptual | KNOWN_RANGE | REFERENCE_WEEK |
|---|---|---|
| precision | KNOWN_RANGE | REFERENCE_WEEK |
| periodStart / periodEnd | Instantes reales, ambos obligatorios y fin > inicio, no futuro | Ambos nulos |
| periodZone | America/Guayaquil | America/Guayaquil, sin implicar hora conocida |
| referenceWeek | Lunes de referencia, obligatorio para el nuevo flujo semanal; opcional en registros reales anteriores/otras ventanas | Lunes obligatorio, semana de referencia terminada |
| periodType | Tipo existente; EXACT_WEEK solo con límites alineados y confirmación | Solo ROLLING_7D |
| nominalWindow | Tipo de ventana, no se almacena como minutos evaluados | 7 días declarados, no fechas ni tiempo evaluado inventados |

`LEGACY_DECLARED` identifica fechas asignadas por el pipeline anterior, solo con evidencia de lote/procedencia. Sus columnas originales se conservan para reconstrucción; los DTOs no deben rotularlas como límites realmente observados. No se asigna ese modo por coincidencia casual con un lunes. Casos dudosos se reportan para conciliación.

## Cambios propuestos en `upt_measurement`

- Añadir `period_precision VARCHAR` con CHECK de valores definidos y `reference_week DATE` nullable; control de lunes en dominio y restricción consistente en BD.
- Permitir nulos en period_start/end SOLO para REFERENCE_WEEK, ambos a la vez. KNOWN_RANGE mantiene integridad temporal. LEGACY_DECLARED preserva valores originales y procedencia.
- Reemplazar la unicidad total existente por dos índices únicos parciales: KNOWN_RANGE por `(connector_id, source, period_start, period_end)`; REFERENCE_WEEK por `(connector_id, source, reference_week)`, dado que su tipo está restringido a ROLLING_7D. No usar file checksum como identidad.
- Indexar la lectura por referencia/precision y conector según SQL final; aprovechar índices de período real para KNOWN_RANGE. No crear índices redundantes sin verificar plan.
- UUID, equipo, fuente, revisiones, exclusión, autores y versiones se conservan. No generar equipos/conectores nuevos.

No representar ausencia mediante epoch, fecha de carga, cadena vacía o lunes de referencia convertido a timestamp. No cambiar automáticamente de identidad conocida a referencia en una corrección ordinaria. Esa transición exige conciliación explícita, conservando las dos procedencias.

## Revisiones y transiciones

`upt_measurement_revision` mantiene ReadingClass, ReviewState, UptimeBasis, cuatro duraciones nullable, reported_percent, observaciones, crudos, etiquetas D12 y auditoría. No necesita un nuevo enum de validación por el mero desconocimiento de fechas.

- Nueva → PENDING; sin aporte hasta validación expresa.
- Igual identidad/valores/originales/base → IDENTICAL sin escribir.
- Distinta lectura → conflicto; corrección justificada crea nueva revisión pendiente; vigente anterior permanece.
- Validación: exige reglas aplicables al modo; fechas desconocidas no se inventan ni se consideran una autorización de semana exacta.
- Exclusión/restauración conserva el historial y su justificación.
- Para informe sin Ocupado, REPORTED_PERCENT sigue obligatorio. El porcentaje no reconstruye minutos offline ni Ocupado.
- `evaluatedMinutes` es nullable en proyecciones de referencia; no cambia a 0 ni a 10080 para mantener un DTO primitivo.

Separar `MeasurementRules` de comparación contra duración real: reglas intrínsecas de números, porcentaje, coherencia/ausencia aplican siempre; suma/duración real y alineación solo cuando el intervalo existe. Las observaciones que dependen de una duración real no se ejecutan con una duración ficticia. Conservar crudos para valores que no caben y comparación idempotente.

## Lotes y filas

`upt_import_batch`: añadir reference_week y precisión declarada; `weekStart` del contrato actual se mantiene como entrada de referencia, cambia la semántica explicada al usuario. `declared_period_start/end` anteriores se conservan como metadata heredada, no se vuelven a usar para poblar límites reales. El archivo se identifica por checksum para trazabilidad, no por identidad de las mediciones.

`upt_import_row`: añadir precisión y referencia; permitir límites reales ausentes cuando corresponde. Mantener hoja/fila, crudos, clasificaciones NEW/IDENTICAL/CONFLICT/INVALID/PENDING_LINK y outcomes actuales. La comparación `seen` incluye el modo de identidad; conector/semana repetidos no pasan como dos registros nuevos. La confirmación y la vinculación posterior consultan la misma identidad. Número desbordado en etiqueta deja pendiente con motivo sin abortar el lote.

Los originales de las dos hojas se conservan. Cabeceras de período inconsistentes se mantienen como fuente, sin sobrescribir la referencia elegida ni fabricar límites. El modelo actual no requiere añadir Ocupado/fechas al archivo del usuario.

## Proyecciones y cálculo

1. **KNOWN_RANGE:** MeasurementFact conserva tiempo evaluado real/cálculo existente y las cuatro duraciones de fuente necesarias para composición. Última incluida por conector en la selección, sin sumar solapamientos.
2. **REFERENCE_WEEK:** proyección con porcentaje informado, minutos conocidos nullable, referencia, precisión y revisión vigente. No convertir porcentaje a minutos. Media BigDecimal por conectores incluidos; etiqueta REPORT_MEAN.
3. **Coverage:** incluida/evaluable/por estado por conector en cada apartado; sin atribuir separado por referencia/período y estable ante filtros de atribución. No unificar denominadores de dos apartados como si fueran conectores distintos.
4. **StateComposition:** available/occupied/fault/offline nullable, totalKnownMinutes, selectedMeasurements, completeMeasurements y knownMeasurements por estado; isComplete. La suma de datos de distintos niveles de completitud se rotula parcial y publica denominadores. No añadir cuota inventada para el estado ausente.
5. **Report snapshot:** referencia, precisión, método, cifras, composición y sin atribuir; mismo instante y predicados que consulta. El documento archivado conserva sus bytes y soporte.

Usar long/SQL bigint para sumas de minutos; BigDecimal para porcentajes. No modificar UptimeFormula ni las reglas de Consumo para acomodar la media del informe.

## Compatibilidad y datos existentes

La nueva migración añade estructura y relaja NOT NULL de forma condicional. Prueba desde V015, sin datos dev. No eliminar fechas, revisiones o archivos ni revisar automáticamente validaciones anteriores. Inspeccionar todos los Excel con período declarado y revisiones posteriores, incluidos EXACT_WEEK.

Una procedencia heredada demostrable puede marcarse LEGACY_DECLARED conservando originales. Una alta nueva por referencia debe buscar candidatos legacy del mismo conector/fuente/semana: si existen, devolver conflicto de conciliación, sin crear duplicado ni elegir un ganador. No añadir un índice que falle por colisiones legacy ni fusionarlas mediante backfill. El diagnóstico de upgrade debe enumerarlas y impedir reparación silenciosa.

No degradar automáticamente un EXACT_WEEK confirmado previamente. La presentación conserva trazabilidad de lo que fue declarado y no lo convierte en evidencia real nueva. Cualquier corrección de ese histórico requiere decisión justificada posterior, fuera de la migración automática. Los archivos emitidos permanecen accesibles bajo los permisos actuales e inmutables.
