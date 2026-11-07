# Research: Ajuste semanal de uptime — 2026-11-04

Estado: decisiones resueltas para el diseño incremental. Investigación de código local, PRD §8.1/8.2, TRD como referencia y Constitución 1.2.0. Sin instalar dependencias, migrar datos ni ejecutar aplicación.

## R-21 — Mantener arquitectura y diseño

**Decisión:** ampliar el módulo existente y sus puertos/records, no crear arquitectura paralela. Frontend por feature, mapeos existentes y ChartView compartido.
**Motivo:** `ArchitectureTest` prohíbe dependencias de application a API/adaptadores y entre módulos privados; ESLint mantiene HTTP en data-access e impide importar presentación privada ajena. DESIGN exige tokens/componentes existentes.
**Alternativas descartadas:** nuevo microservicio, motor de importación, MapStruct/Spring Batch sin uso actual, nueva biblioteca de charts, o ShareChart privado de Consumo acoplado a kWh.

## R-22 — Referencia temporal distinta de intervalo real

**Decisión:** `KNOWN_RANGE` y `REFERENCE_WEEK`, semana de referencia separada. Captura conserva segundos por conector; Excel actual conserva límites desconocidos.
**Motivo/evidencia:** `CaptureFacade` envía un CapturePeriod común; `capture-page` añade :00 y abre 24 h. `UptimeImportService.upload` crea lunes a lunes; `EnerlinkUptimeParser` reemplaza fechas por DeclaredPeriod. `Measurement` y V013 exigen timestamps no nulos. Son las causas concretas de la discrepancia.
**Alternativas descartadas:** fin de carga como hora observada, semana elegida como intervalo, deducir fecha por nombre/cabecera o restar 7 días sin hora verificada. Las seis respuestas del usuario autorizan no reconstruir esos datos.

## R-23 — Identidad sin fechas y reimportaciones

**Decisión aprobada:** conector + fuente + ventana 7 d + semana de referencia; misma lectura se omite, distinta requiere revisión justificada. Captura real tiene otra identidad y nunca se fusiona automáticamente.
**Diseño:** ramas explícitas del mismo repositorio y restricciones únicas parciales. Usar la misma comparación de originales, base de uptime, minutos y porcentaje en preview, confirmación y vinculación. Revalidar identidad al confirmar; captura concurrente no duplica.
**Alternativas descartadas:** checksum/nombre del archivo como clave, fila/hoja como identidad de negocio, o fusionar por conector y proximidad. Cambiar orden/archivo no crea un período nuevo; hoja/fila sigue como procedencia.

## R-24 — Promedio informado separado

**Decisión aprobada:** media aritmética de porcentajes validados, rotulada Promedio del informe. No se pondera por duración supuesta ni se mezcla con capturas reales.
**Evidencia:** `MeasurementRepositoryAdapter.FACTS` deriva peso de timestamps para REPORTED_PERCENT; `UptimeCalculator` usa ese peso. El DTO actual obliga `evaluatedMinutes` como long. Con horario desconocido ambos supuestos deben romperse de forma explícita, no rellenarse con cero.
**Diseño:** sección de informe con método REPORT_MEAN y evaluado nulo; BigDecimal a precisión de fuente, redondeo final. Valor 0 incluido; ausencia/pendiente/exclusión fuera del promedio, dentro de cobertura según reglas existentes. Capturas reales conservan fórmula y ponderación.
**Alternativas descartadas:** 10080 minutos artificiales, promedio general de todos los datos, rechazo completo del Excel o descarte silencioso del resumen.

## R-25 — Apertura semanal por referencia

**Decisión aprobada:** semana de referencia más reciente con datos incluidos, apartados conocidos y Excel separados. Oficial exacto conserva regla y prioridad existentes; 24 h/30 d siguen separadas.
**Diseño:** selección resuelta por backend antes de filtrar los resultados por conector; el total sin atribuir depende del período/reference solicitado y no del rango de los conectores visibles. Sin referencia, permanece consulta anterior por límites reales. No autoasignar referencia a manuales previas.
**Alternativas descartadas:** comparar un fin real con una fecha de referencia como si fueran el mismo dato, elegir semana desde filename, o duplicar selección en Angular.

## R-26 — Composición y cobertura

**Decisión aprobada:** dona/pastel en detalle y dashboard, estados ausentes explícitos. Backend suma duraciones efectivamente conocidas de la misma selección y entrega cobertura. No obtiene Ocupado por diferencia ni uptime × duración.
**Diseño visual:** componente local uptime sobre ChartView, 4 estados con orden fijo, leyenda HTML y campo de completitud. Estado parcial se rotula Distribución de tiempos informados; las porciones representan fracciones de la suma conocida, no del período. Sin suma positiva no se dibuja círculo. Cada apartado tiene su propio gráfico, sin mezclar poblaciones. Mantener campos originales nulos frente a cero.
**Alternativas:** ShareChart privado no corresponde al dominio; nueva librería innecesaria. Una serie4 en el sistema actual permite distinguir cuatro categorías sin colores sueltos; requiere pruebas y documentar DESIGN. No se ejecuta una auditoría estética general en esta fase.

## R-27 — Datos heredados y migración

**Decisión técnica:** nueva migración posterior a V015, nulos condicionales e índices por identidad. Preservar datos y snapshots. Identificar procedencia demostrable de fechas asignadas por lote, marcar LEGACY_DECLARED sin sustituir valores ni revisar decisiones humanas automáticamente.
**Preflight:** inventario de candidatos manuales, Excel declarados, confirmados exactos y colisiones por referencia. Los candidatos legacy bloquean una nueva alta conflictiva hasta conciliación explícita; no duplicar ni escoger un ganador. No modificar copias archivadas ni depender de datos productivos para pruebas.
**Alternativas descartadas:** editar V013/V014, borrar fechas, reasignar semanas por apariencia o backfill que fusione registros. La planificación no autoriza una reparación automática de aprobaciones existentes.

## R-28 — Hallazgos QA en el recorrido afectado

1. `UptimeQueryService.pendingLink` usa el período resuelto de datos visibles; sin datos usa null/null y puede contar toda la historia. Resolver el alcance temporal/referencia una vez, estable ante filtros de atribución, incluidos rangos vacíos.
2. `UptimeReportService.read` no transfiere `view.pendingLink()`; añadirlo al modelo común y a ambos renderizadores.
3. `ConnectorResolver` usa Integer.parseInt sobre número libre de etiqueta; capturar el desbordamiento o comprobar rango y devolver pendiente con motivo, sin fallar el lote.

## Fuentes y alcance de verificación

Código y migraciones ya citados; `MeasurementGridService`, `ImportedMeasurements`, `PendingLinkService`, API responses y reportes; `shared/ui/chart/chart-view.ts`, `chart-theme.ts`, `DESIGN.md`, facades/páginas de uptime, `ArchitectureTest` y `eslint.config.js`. Revisión previa: Java 513 pruebas aprobadas, Angular 203/205 con dos timeouts que pasaron aislados, lint/build correctos, 341,77 kB inicial. Es baseline, no validación del diseño aún no implementado.

No quedan NEEDS CLARIFICATION de producto o tecnología para este plan. El estado real de datos legacy/colisiones se verifica antes de desplegar; su conciliación no es automática ni forma parte de una migración destructiva. Control de cambio PRD: CC-PRD-005-20261104.
