# Research — Feature-006: Registro semanal de uptime Enerlink

**Fecha:** 2026-11-04. **Base:** main `9739aca`; rama `codex/006-registro-semanal-uptime`. Investigación de código, Constitución 1.2.0, PRD v1.1 pp.12–13 y TRD como referencia. Las siete decisiones aceptadas están en spec.md y CC-PRD-006-20261104. Los archivos de drafts/ son antecedentes no normativos y no se copian como diseño activo.

## R01 — Arquitectura y tecnología existentes

**Decisión:** conservar módulos, capas, puertos públicos, records y mapeos from/toCommand actuales; frontend por feature, Signals/Signal Forms, Material, Sass y Chart.js. No añadir librerías ni infraestructura.
**Motivo/evidencia:** `sarc-bakend/pom.xml` declara Java25/SpringBoot4.1.1; `ArchitectureTest.java` impide domain→Spring/capas externas, application→adapters/API y dependencias privadas entre módulos. `sarc-frontend/package.json`/ESLint delimitan stack y HTTP en data-access. No existen MapStruct ni Spring Batch implementados en el recorrido; se usa ImportJobs.
**Alternativas descartadas:** microservicio, motor de importación nuevo, trasladar lógica a Angular o importar ShareChart privado de Consumo. El cambio es incremental en uptime.

## R02 — Tiempo real frente a semana de referencia

**Decisión:** valor de dominio pequeño `MeasurementPeriod`, con precisión `KNOWN_RANGE`, `REFERENCE_WEEK` o `REQUIRES_RECONCILIATION`; referencia semanal separada de los instantes. El tercer caso solo expresa ambigüedad histórica pendiente de decisión, no otro método de cálculo.
**Motivo/evidencia:** `Measurement.java:29–34` y V013 exigen límites; `UptimeImportService.upload:88–103` convierte weekStart a lunes/lunes; `EnerlinkUptimeParser:112–113` sobreescribe los límites con el período declarado. El UI impone CapturePeriod común, default24h y añade :00 a fechas que podrían llevar segundos.
**Alternativas descartadas:** usar fecha de carga, la semana de referencia o 10080 minutos para simular observación; dejar la semántica solo en rótulos; reestructurar inventario.

## R03 — Identidad e idempotencia

**Decisión aprobada:** conocido conserva conector+fuente+inicio+fin; informe sin horario usa conector+fuente+ventana7d+referenceWeek. Dos ramas del mismo repositorio y restricciones únicas parciales, con comparación común en preview/confirmación/vinculación.
**Motivo:** reordenar archivo o cambiar checksum no crea otra medición; captura y Excel de igual conector/referencia no son automáticamente equivalentes. Latest/effective revision y validación actual se conservan.
**Alternativas:** checksum/número de fila como identidad; fusionar con captura por proximidad; permitir NULL sin índice específico, que no evitaría duplicados concurrentes.

## R04 — Dos agregados, sin minutos inventados

**Decisión aprobada:** conocidos conservan ponderación por minutos; referencia usa media aritmética de porcentajes originales incluidos, redondeo final, título Promedio del informe. Añadir resultado propio `ReferenceReportKpis`, no llenar Kpis.evaluatedMinutes con 0.
**Motivo/evidencia:** `MeasurementFact.included` requiere evaluatedMinutes>0; `MeasurementRepositoryAdapter.FACTS:36–51` inventa peso desde límites para REPORTED_PERCENT y usa COALESCE de estados; `downMinutes:129–135` deriva indisponibilidad desde porcentaje. Ese camino permanece solo para datos conocidos donde corresponda a 005; no es fuente de la composición nueva ni de referencia. `MeasurementRevision.uptimePercent` redondea a dos decimales: la media debe leer reported_percent preservado (NUMERIC12,4), no el texto ya presentado.
**Alternativas:** peso nominal de7d, media sobre porcentajes ya redondeados, excluir ceros reales o promediar ambos bloques juntos. Reutilizar precedencia/cobertura del calculador; separar resultado de informe evita una jerarquía nueva de servicios.

## R05 — Selección semanal, filtros y cortes

**Decisión de diseño:** referencia explícita o última referencia global con mediciones incluidas; resolver antes de filtros de atribución y paginado para mantener una misma semana al cambiar red/equipo/conector. Sin datos en ese filtro, vacío, no salto silencioso a otra semana. Esta interpretación sigue FR012/014 y el total sin atribuir estable.
**Motivo:** el selector actual parte de period_end y no encuentra filas sin horario; `UptimeQueryService` debe consultar referencia con criterio propio. El máximo366d sigue para rangos reales; buscar la última referencia no se limita arbitrariamente al lookback. Conservar oficiales y otras ventanas, e históricos reales sin referencia.
**Detalles técnicos:** empate de capturas con mismo fin se ordena por inicio descendente, sin fusionar identidades; rangos equivalentes ya son únicos. Los endpoints de resultados reciben la referencia resuelta, evitando cambiar de semana durante un refresco. KPIs/dona provienen del mismo snapshot de summary. Cada reporte tiene su snapshot/corte propio: no prometer que dos solicitudes separadas son idénticas si hubo escrituras entre ellas, ni inventar un token histórico sin almacenamiento.
**Alternativas:** fecha desde el nombre/carga, selección duplicada en frontend o sumar páginas visibles para formar el KPI.

## R06 — Composición conocida y diseño corporativo

**Decisión:** composición pura del mismo conjunto incluido, cuatro minutos nullable, total conocido, mediciones completas/seleccionadas y número conocido por estado. Nulo si nadie informó; 0 si se informó cero; suma parcial con cobertura explícita. Distribución parcial no afirma representar168h. Sin suma positiva no hay círculo.
**Evidencia:** ChartView ya admite doughnut/null/ARIA/temas/movimiento reducido; ChartTheme solo tres colores. Reutilizarlo desde un componente local uptime `states-chart`; ampliar `series-4` en `_tokens.scss`/ChartTheme/DESIGN sin cambiar tres colores anteriores ni elegir una paleta fuera del sistema. Leyenda HTML fija accesible.
**Alternativas:** deducir Ocupado/estados de porcentaje o sumando valores del ranking, colores por posición variable y un chart diferente al usado por SARC.

## R07 — Transición aprobada, con prueba de procedencia

**Decisión aprobada A:** reclasificar Excel005 con horarios asignados como referencia sin horario, conservar porcentajes, validaciones e historial, trazar el cambio y retirar la oficialidad basada solo en esos horarios. Los conflictos exigen conciliación, no fusión ni elección automática.
**Evidencia crítica:** parser guarda sourceValues.periodStart/end solo si no recibió DeclaredPeriod (`EnerlinkUptimeParser:124–128`). La ausencia de claves no demuestra que el archivo original careciera de horarios: hay que abrir el original retenido, verificar checksum, hoja/fila y cadena de revisiones/lotes. Una misma Measurement puede mezclar FILE y MANUAL/BLOCK; revisar solo la vigente o basis/origin sería inseguro. Original ausente/inaccesible o prueba no concluyente → conciliación.
**Diseño:** V016 crea estructura; servicio de transición usa FileStorage/TabularReader/UptimeRowParser ya existentes fuera de transacción de escritura, prepara diagnóstico y aplica cada cambio con @Version, evidencia y evento temporal persistente. No usar corrección ordinaria que pondría una validada en PENDING (`MeasurementRevision:123–147`). Mantener revisiones originales y punteros, clasificar período efectivo mediante cabecera/decisión para que EXACT_WEEK histórico no vuelva a oficializarse.
**Alternativas:** SQL que clasifica por FILE/REPORTED_PERCENT, mutar revisiones históricas, guardar solo un log efímero, revalidar todo, escoger último entre colisiones o modificar copias emitidas.

## R08 — Conflictos y conciliación con operaciones existentes

**Decisión técnica:** precisión REQUIRES_RECONCILIATION preserva antecedentes y queda fuera de agregados hasta atribución explícita; mostrar en cola operativa de Revisión. No afirma intervalo conocido ni referencia dudosa. Si la referencia está probada, solo su cantidad por conector se refleja como pendiente en esa selección; lo no atribuible no se reparte entre semanas.
**Diseño:** registro temporal conserva valores previos antes de retirar límites canónicos asignados. El operador puede confirmar referencia o un rango realmente respaldado, con versión/evidencia/motivo. Si hay colisión, 409; no merge de cadenas. Puede excluir explícitamente el registro que no deba computar mediante la exclusión vigente, conservándolo; solo entonces clasificar la identidad elegida. Un excluido sin conciliación no reaparece en métricas al restaurar sin resolverla. No crear motor de MDM ni pantalla nueva de historial.
**Motivo:** implementa el bloqueo de atribución ambigua de FR020 y los estados pendientes existentes, sin convertir una excepción de migración en un ganador implícito.

## R09 — Reportes y criterios fieles

**Decisión:** UptimeReport extiende su sección de referencia/método/composición/cobertura y conserva snapshot REPEATABLE READ/render fuera. Reutilizar view.cutOff en lugar de otro Instant.now. Criterios de ReportPublication deben admitir selección por referencia sin from/to ficticios, exclusivamente con discriminante validado; consumo/inventario conservan sus reglas anteriores.
**Evidencia:** `ReportPublication:19–35` exige from/to; `UptimeReportService.publication:138–142` cae a hoy/mañana. El DTO y renderer actuales formatean límites y evaluado no nulos. No basta añadir metadata; deben distinguir modelos de sección.
**Alternativas:** poner lunes/hoy para satisfacer el constructor, renderizar fecha null como epoch o regenerar documentos antiguos. Mantener siete hojas Excel y gráficos de exportación actuales; el pastel nuevo solo es obligatorio en dashboard/detalle.

## R10 — Migración, compatibilidad y pruebas

**Decisión:** nueva V016 después de V015; confirmar numeración antes de implementar. Actualización desde V015, archivos de prueba simulados y PostgreSQL18 desechable. Despliegue coordinado backend/frontend por nulabilidad. No down migration ni rollback a binario005 después de escribir referencias sin horario; restauración integral de backup o avance compatible.
**Motivo:** UNIQUE anterior, NOT NULL, lecturas por fin real, decisiones exactas y DTOs cambian juntos. V001–V015 inmutables. Transición con estado persistente y reintento idempotente, sin red dentro de Flyway ni lecturas externas durante transacción larga.
**Verificación previa:** baseline005: 517 Java,205 Angular, lint/build correctos y341,77kB; evidencia de benchmark secuencial no prueba30usuarios, T124humana pendiente. No ejecutar ni dar por pasadas las pruebas006 en una fase documental.

## R11 — Cierre U1: evaluación intrínseca y temporal separadas (2026-11-05)

**Evidencia:** MeasurementRules.assess/assessReported exige durationMinutes; MeasurementService.correctInTransaction/correctionData comprueba y calcula límites del registro. Relajar columnas/DTOs no elimina estos supuestos. Con fechas nulas, una corrección podría fallar o recibir un peso ficticio para satisfacer la firma.
**Decisión técnica:** entrada explícita por precisión en las políticas actuales. Mantener reglas numéricas/crudos/observaciones, omitir únicamente las comparaciones que necesitan un rango real en REFERENCE_WEEK y no reconstruir duración. Aplicación común en preview/confirm/vínculo/corrección/validación; KNOWN_RANGE intacto. Detalle en modelo3.1/contrato4.1 y tareasT004/T010/T025–T031.
**Alternativas descartadas:** pasar0/10080 como duración, eliminar todas las validaciones, tratar ausencia como0, o validar solo en el parser/Angular. No se añade regla de producto; se concreta FR008/009.

## R12 — Cierre U2: solicitud durable sobre ejecutor existente (2026-11-05)

**Evidencia:** ImportJobs.submitAfterCommit encola Runnable y maneja rechazo; no guarda solicitud/resultados. InterruptedUptimeImportRecovery solo encuentra lotes de importación. Los eventos terminales no prueban qué items aún no se ejecutaron si cae antes del primero.
**Decisión técnica:** tabla local upt_temporal_transition_work con items inmutables, clave idempotente por actor, results por item, estado y @Version; mismo módulo/capas y ejecutor ImportJobs. Snapshot de trabajo/GET/retry en el controller actual, cola llena persistida y resultados atómicos con evento/audit. Tras reinicio se requiere retry autorizado, sin reconstruir identidad de usuario desde memoria. Modelo4.1/contrato8.1 y tareasT005/T008/T054–T063/T069–T073.
**Alternativas descartadas:** guardar progreso solo en memoria/logs, usar el evento de decisión como solicitud mutable, crear lotes de archivo ficticios o añadir un motor genérico. El único registro local adicional permite demostrar recuperación antes del primer item sin cambiar arquitectura ni agregar dependencias.

## Cierre de investigación

Sin NEEDS CLARIFICATION de producto pendientes para este plan. Originales/cadenas disponibles se diagnostican al desplegar; la especificación ya define conciliación de los ambiguos. No se introduce una regla de producto por falta de acceso a la BD. Referencias verificadas en código local; no se instalaron dependencias ni se ejecutó migración. Los detalles siguientes son propuestas, no cambios implementados.
