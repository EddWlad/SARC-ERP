# Implementation Plan: Registro semanal de uptime Enerlink

**Branch:** `codex/006-registro-semanal-uptime` | **Date:** 2026-11-04 | **Spec:** [spec.md](spec.md)
**Base entregada:** `9739aca` (Feature-005). **Estado:** diseño técnico, sin implementación. Plan activo de006, distinto de los antecedentes de drafts/. Siguientes pasos: `$speckit-tasks` y `$speckit-analyze` antes de ejecutar cambios.

## Summary

Adaptar uptime ya existente para capturar límites reales por conector, importar el Excel semanal sin horarios ficticios, separar referencia/intervalo y promedios, y añadir composición de estados en el diseño actual. La transición de Excel005 conserva porcentajes/validaciones/historial y cambia su tratamiento temporal en consultas nuevas; no toca documentos emitidos.

Se mantienen módulos, capas, tecnologías, componentes y estilo corporativo. Un valor temporal de dominio, una extensión acotada de persistencia y del calculador, y un componente local de composición resuelven el alcance; no nueva arquitectura, motor de reportes, librería o servicio externo.

## Technical Context

| Aspecto | Decisión basada en código existente |
|---|---|
| Lenguaje/backend | Java25, SpringBoot4.1.1, MVC/Security, JPA/Hibernate, Jakarta Validation; records y mapeos from/toCommand |
| Persistencia | PostgreSQL18/Flyway; migración nueva posterior a V015; UUID, TIMESTAMPTZ, BigDecimal/NUMERIC y @Version |
| Frontend | Angular22, TypeScript6, Material/CDK22, Signals/Signal Forms, Sass; Chart.js existente |
| Dependencias | Sin nuevas; ImportJobs/ImportFiles/BaseImportBatch/TabularReader, FileStorage, ReportArchive, CorporatePdf/Workbook existentes |
| Pruebas | JUnit/MockMvc/Testcontainers18, ArchUnit/OpenAPI; Vitest/ESLint/Prettier/build y QA visual en sandbox |
| Plataforma | Web responsive cinco roles, claro/oscuro, zona America/Guayaquil |
| Rendimiento | Inicial ≤500kB; p95 usual2s/dashboard5s con30usuarios es objetivo TRD por demostrar, no capacidad afirmada |
| Escala de verificación | Mantener escenarios005 de300/1000conectores e historial sintético y cargas2000filas; medir diferencias en recorridos tocados |
| Restricciones | Sin cambio de stack, identidad de inventario ni módulos ajenos; archivos existentes/modelo actual; siete decisiones aprobadas y 16/16 calidad |

MapStruct y Spring Batch mencionados en referencias generales no están implementados en el recorrido actual; incorporarlos aquí sería una desviación innecesaria. La arquitectura y reglas verificables del código prevalecen para el diseño técnico compatible, sin sustituir decisiones funcionales del PRD.

## Constitution Check

Leídas Constitución1.2.0 completa y PRD v1.1 pp.12–13 (§8.1/8.2/8.3, RF004.1–16 y CA004.1–6); TRD arquitectura/tiempos como referencia. Control de cambios aprobado: `documents/control-cambios-prd.md`, CC-PRD-006-20261104. La ausencia de límites reales y la media del informe son excepciones explícitas del responsable del producto, no inferencias técnicas.

| Puerta | Preinvestigación | Postdiseño | Control |
|---|---|---|---|
| I: especificar primero | PASS | PASS | Spec006 aclarada, sin implementar en esta fase |
| II: reglas explícitas | PASS | PASS | Seis respuestas previas + A/FR020; ninguna regla de agregación se introduce silenciosamente |
| III: migraciones inmutables | PASS | PASS | V016 propuesta, V001–V015 intactas, upgrade desdeV015 |
| D05 | PASS | PASS | Identidades verificadas/alias; ambiguas a conciliación, no aproximación nueva |
| D06/D07 | PASS | PASS | UUID y continuidad del inventario; sin crear identidad física paralela |
| D09 | PASS | PASS | Dos hojas solo en informe semanal autorizado; otras importaciones intactas |
| D12 | PASS | PASS | Etiqueta del hecho, nombres actuales y copias emitidas inmutables |
| D10/D11/D13/D14 | N/A | N/A | No cambiar ni extender reglas de energía a uptime |
| Capas y módulos | PASS | PASS | Puertos públicos, dominio Java puro, ArchUnit/ESLint como puertas |
| Conservación visual | PASS | PASS | Componentes/tokens/Material actuales; extensión mínima de una serie, no rediseño |
| Transición aprobada | PASS | PASS | Evidencia original + cadena, validaciones preservadas, conflicto sin ganador automático |

No hace falta enmienda constitucional ni cambio del PDF maestro. PASS certifica coherencia de diseño, no que el código ya cumpla.

## Fase 0: investigación y comportamiento real

Decisiones, motivos, alternativas y referencias en [research.md](research.md), R01–R10. Hallazgos principales:

| Código actual | Diferencia con006 | Cambio mínimo |
|---|---|---|
| CaptureFacade/CapturePage | Período común,24h inicial, conversión añade :00 | Default7d, referencia de contexto y borrador de límites por conector con segundos |
| Measurement/V013 | Identidad real/NOT NULL para todos | Precisión y referencia independientes, claves parciales |
| UptimeImportService/parser | La semana impone límites y oculta si original los tenía | No forzar horarios; conservar prueba temporal de fuente |
| RevisionData/MeasurementRevision | Nueva corrección siempre PENDING | Decisión temporal separada, preservando validaciones/valores |
| SQL MeasurementRepositoryAdapter | Peso desde timestamps y COALESCE de estados | Hechos conocidos y referencia separados; composición desde valores nullable |
| UptimeQueryService/calculador | Selección por fin real, Kpis.evaluated primitivo | Resolver referencia y dos métodos con resultados propios |
| countPendingLinks | OR period_end IS NULL atravesaría todas las semanas | Conteo por referencia/tipo/apartado antes de filtros de atribución |
| Summary/Detail y UptimeReport | Suponen límites presentes, no composición de4estados | Nullable explícito, nuevo bloque referencia, método/cobertura/composición |
| ReportPublication | from/to obligatorios, fallback de publicación hoy/mañana | Variante por referencia validada, sin fechas ficticias |
| ChartView/ChartTheme | Doughnut ya existe, solo3series | Reuso local y cuarta serie dentro de tokens existentes |

Los borradores anteriores no contemplaban completamente FR020 aprobado ni originales/cadenas mixtas; este diseño los sustituye. No quedan NEEDS CLARIFICATION de producto para planificar; disponibilidad de archivos/datos reales es una condición verificable del despliegue con resultado ambiguo ya definido.

## Clasificación de aceptación y trazabilidad

“Cumplido” abajo significa base existente con regresión obligatoria, no aceptación anticipada de006.

| Requisitos / criterios | Base actual | Trabajo006 |
|---|---|---|
| FR001/011/019; SC008/009 | Alcance, inventario, alias, permisos, auditoría y archivo implementados | Mantener y ampliar pruebas de proyección/composición/conciliación |
| FR002–005; US1/SC001 | Pegado/fórmula existen; flujo temporal no cumple | Límites por conector y segundos; default7d/contexto independiente |
| FR006–008; US2/SC002 | Ambas hojas/crudos/conversión existen; horarios se asignan | Representación sin horario y procedencia, sin Ocupado ni peso inventado |
| FR009/010; SC003 | Revisiones/idempotencia por rango existen | Nueva identidad por referencia, dos claves/no fusión, concurrencia |
| FR012–014; US3/SC004/005 | Ponderación conocida/cobertura existen | Referencia más reciente y media del informe separada, conteos coherentes |
| FR015–017; SC006/007 | Wrapper/chart/DS existen | Componente local, leyenda/partial/0/null, cuarta serie y matriz visual |
| FR018; US4/SC005/008 | Snapshot/render/archivo existen | Sección por referencia, criterios fieles y nulabilidad en documentos |
| FR020; US4.4–6/SC010 | No transición canónica implementada | Verificación de fuente, reclasificación trazada sin revalidar y cola de conciliación |

## Fase 1: diseño técnico

### Backend: cambios dentro de las capas actuales

- **Dominio:** `MeasurementPeriod` y precisión; métodos de transición controlada en Measurement sin reescribir revisiones; validación intrínseca/temporal separada; extensión de UptimeCalculator para media de referencia y composición. Mantener UptimeFormula conocida; no duplicarla en otro módulo o Angular.
- **Repositorios/infra:** nuevas consultas tipadas por selección real/referencia y modo; índices parciales; proyecciones nullable, sumas bigint/long y porcentaje original. Pasar los UUID del puerto de conectores al criterio, sin JOIN a entidades privadas de inventario. Reducir alcance en SQL en las nuevas consultas, sin una refactorización general de las de005.
- **Servicios:** MeasurementService/ImportedMeasurements/PendingLinkService usan la misma identidad/validación. UptimeImportService no convierte weekStart a límites reales; parser expone horarios proporcionados y prueba fuente. UptimeQueryService resuelve selección única y deriva ambos apartados, cobertura y operaciones del mismo conjunto.
- **Transición:** `UptimeTemporalTransitionService` orquesta prueba de original y cadena; lectura externa fuera de transacción, comparación/actualización con versión, evento temporal persistente y AuditTrail en transacción corta. Reutilizar ImportJobs para bloques y reintento, sin nuevo motor. `UptimeRowParser`/TabularReader ofrecen inspección de fuente; application no conoce POI/adaptadores. Detalle en modelo/contrato.
- **API:** extender controllers y DTOs actuales, nueva acción de clasificación temporal acotada, cola operativa de Revisión. Mantener autorizaciones servidor y ProblemDetail. OpenAPI/tipos regenerados; no editar TypeScript generado manualmente.
- **Reportes:** UptimeReportService usa el mismo snapshot/proyección y view.cutOff; renderer nunca recibe valores operativos no autorizados. ReportPublication añade variante por referencia y conserva el contrato de criterios anteriores.

**SOLID aplicado:** dominio decide reglas, servicio orquesta transacción/puertos, repositorio persiste/proyecta, DTO mapea y componente presenta. Precisión temporal y base de cálculo son ejes explícitos, no condicionales repetidos por cada controller. Puertos pequeños existentes ampliados donde corresponde; no una clase/servicio por campo ni jerarquía genérica especulativa.

### Ajustes de consistencia U1/U2 — 2026-11-05

- **U1 — evaluación explícita por precisión:** MeasurementRules separa validación numérica y comprobaciones de intervalo; referencia no llama PeriodRules con null ni usa duración0/10080/nominal. Conservar conversiones, porcentaje fuente, límites numéricos, crudos y observaciones aplicables de005. Importar/confirmar/vincular, corregir directamente y validar comparten esta política; MeasurementService.correct/correctionData deja de asumir límites presentes. KNOWN_RANGE conserva sus reglas. Modelo 3.1 y contrato 4.1 fijan los casos y pruebas.
- **U2 — trabajo durable local:** una tabla upt_temporal_transition_work en V016 conserva solicitud inmutable, clave idempotente por solicitante, resultados por item, estado y versión. TemporalTransitionWork y su repositorio/adaptador viven en las capas actuales de uptime. ImportJobs sigue ejecutando después del commit; no se convierte en almacén de trabajos. El cambio de medición/evento/audit y resultado del item se confirman juntos.
- **Recuperación y API:** POST acepta el trabajo y devuelve identificador/estado; GET consulta progreso; retry versionado reencola solo items sin resultado. Arranque marca QUEUED/RUNNING como INTERRUPTED y exige reintento autorizado con contexto actual; no restaura identidad de usuario desde memoria. Cola llena deja FAILED durable y sin cambios en mediciones. Detalles, estados y rutas en modelo 4.1 y contrato 8.1.
- **Pruebas antes de cambios:** ampliar T004/T010/T025–T031 para validación/corrección sin fechas y T005/T054/T055/T058–T063/T069–T073 para trabajo persistente, idempotencia de solicitud, progreso, reinicio antes del primer item y entre items, atomicidad, cola llena y retry concurrente. No se añaden frameworks ni módulos.

### Persistencia y transición de005

[Modelo detallado](data-model.md). V016 propone precision/reference y constraints condicionales en cabecera/filas/lotes, antecedente temporal persistente y trabajo local recuperable de transición. No cambiar energy_*/asset_* ni identidad/reportes emitidos. Transición de los candidatos basada en original verificado, no FILE/REPORTED_PERCENT/fecha lunes por sí solos.

Una cadena exclusivamente demostrable como Excel sin horarios pasa a referencia con fechas canónicas nulas, conservando valores, revisiones, punteros efectivos, revisor/fecha/justificación y exclusión. Las confirmaciones EXACT_WEEK antiguas permanecen como antecedentes; elegibilidad nueva exige KNOWN_RANGE. No se genera una revisión PENDING solo por reclasificar.

Original inexistente/inaccesible, cadena mixta, porcentaje de fuente requerido ausente o colisión: REQUIRES_RECONCILIATION, trazado y visible en la cola operativa, sin atribución/ganador ni aporte a indicadores nuevos. La conciliación usa evidencia/motivo/versión; no fusión automática. Reejecución idempotente y recuperación de interrupciones con resultados persistentes.

**Despliegue:** schema nuevo sin red en Flyway; ventana coordinada con escritor005 detenido; verificar candidatos y terminar trabajo automático antes de publicar nuevos indicadores, mostrando pendientes explícitos. Backend/frontend juntos por nuevos nulos. Pruebas con original simulado y DB desechable. En producción el acceso privado al original es requisito de transición; fallo no autoriza inventarlo. Copias emitidas byte a byte intactas.

### Cálculo, selección y contrato

[API/UI](contracts/api-ui.md) y [reportes](contracts/reporting.md) fijan nombres y ejemplos.

- Última referencia global con incluidos para la vista semanal; fijarla antes de filtros/páginas. Los filtros pueden producir vacío sin cambiar de semana. Referencia explícita vacía no cuenta historia ajena.
- Última conocida incluida por conector dentro de referencia, orden fin/inicio; mismo cálculo005. Referencia única por conector usa media aritmética de porcentajes fuente,0incluido y ausencia fuera; redondeo solo al presentar.
- Dos coberturas y dos composiciones, no KPI/dona conjunta. Casos de conciliación no acreditados no se distribuyen entre referencias supuestas.
- Summary calcula KPIs/dona/cobertura desde un snapshot; tablas fijan la referencia resuelta. Cada documento tiene su propio snapshot y corte visible. No construir almacenamiento histórico de snapshots ni afirmar igualdad de dos peticiones con escrituras intermedias.
- Contexto de semana no es hora observada ni peso nominal. Rango máximo366d para consultas reales se conserva; búsqueda de última referencia usa criterio propio.

### Frontend responsive y armónico

Mantener rutas, shell, chips/filtros, paneles, Material, tablas/cards, diálogos y foco de DESIGN.md. Reutilizar clases compartidas de dashboard, list-page, dialog-form y QuantityFormat. No replicar el aspecto de Enerlink ni rediseñar otras pantallas.

1. CaptureFacade/MeasurementForm: referencia en contexto; fechas reales en borrador por conector, step1, validación y errores conservan datos. Reset limpia límites/valores al avanzar, no contexto.
2. ImportsPage/ImportDialog: mismos controles, rótulo Semana de referencia del informe, aviso Horario real no informado, dos hojas y crudos/preview. Sin columnas nuevas; sin confirmación exacta del informe sin horarios. Otros importadores intactos.
3. DashboardFacade: backend determina referencia/métodos; pintar apartados independientes. No media ni cálculo por página en Angular; respuestas antiguas no pisan filtros nuevos. Mostrar corte/aviso de actualización fallida.
4. StatesChart local uptime sobre shared ChartView doughnut, leyenda fija4estados, nulos0parciales explícitos. Toda suma/coverage llega del backend. Ampliar series4 solo en `_tokens.scss`, ChartTheme y DESIGN con contraste claro/oscuro/daltonismo; conservar las tres series actuales. Sin nueva biblioteca.
5. MeasurementPage: rama explícita para rango o referencia, no DatePipe sobre desconocido; leyenda incluye Ocupado no informado. Datos solo de la revisión ya visible para ese rol. Historial muestra cambios temporales para operativos.
6. ReviewPage/JustificationDialog: reutilizar cola para conciliación y decisiones temporales con evidencia/versión; no nueva pantalla ni catálogo.

Verificación: 360/390/768/1024/1440 × claro/oscuro; formularios con segundos/errores largos, leyendas parciales, nombres largos, foco/teclado/ARIA/contraste/movimiento reducido. Impeccable puede guiar audit/polish focalizado al implementar, sin alterar identidad ni convertir esta fase en rediseño. No se ejecuta una auditoría visual ni se actualiza su metadata ahora.

## Pruebas, compatibilidad y aceptación

[quickstart.md](quickstart.md) define recorridos y comandos; ampliar pruebas existentes antes de cambios que verifican.

- Dominio: identidades, precisión, períodos con segundos, media0/100y precisión original, composición null/0/parcial, estados/ponderación conocidos y elegibilidad oficial.
- Integración/API: dos conectores, ambas hojas, reimportación/concurrencia/corrección, referencia vacía/filtros y pendientes de otras semanas, roles y unión, esquemas/constraints.
- Upgrade V015: exacta falsa validada, vigente+pendiente, excluidas, manual real, cadena mixta, original inaccesible, colisiones, reintentos e inmutabilidad del archivo; ninguna validación se reinicia en reclasificación inequívoca.
- Angular: captura/reset/segundos, referencia común con bloques separados, dona4estados/null0, leyenda/coverage y contrato de errores/roles; regresión de wrapper con cuarta serie.
- Reportes: mismos datos/filtros/corte en fixture; referencia sin horarios, sin atribuir, oficiales después de transición, XLSX numérico y archivo privado inmutable.
- Puertas: Maven completo, ArchUnit/OpenAPI, Angular completo/lint/formato/build; hashesV001–V015; sin dato real o secreto en evidencia. Baseline005517Java/205Angular no certifica006. T124 humana005 y30usuarios siguen pendientes hasta medir.
- Rendimiento: fixtures comparables a005, nuevas consultas de referencia/índices, transición por bloques e IO externo fuera de transacción. Medir p95/volumen/concurrencia real de prueba; registrar límites, no prometer escala no probada.

## Orden propuesto para tasks posterior

1. Fixtures/pruebas de identidad y transición, baseline/hashes e inmutabilidad de archivos.
2. Dominio temporal + V016/constraints/proyecciones y upgrade; transición con prueba de fuente e idempotencia.
3. Servicios/importación/captura/conciliación + contratos OpenAPI/tipos.
4. Consulta/media/composición/selección y reportes/criterios archivados.
5. Ajustes UI existentes/componente local/tokens y pruebas visuales/accesibilidad.
6. Regresión/performance/documentación y revisión de rollout. No commits/merge/push/deploy en esta fase.

## Riesgos y mitigación

| Riesgo | Control concreto |
|---|---|
| FILE parece prueba pero original tenía horarios | Inspección checksum/hoja/fila + cadena completa |
| Exacta falsa sobrevive en otro endpoint | Una precisión/tipo efectivo común antes de todos los predicados |
| Transición pierde validación | Evento temporal separado; latest/effective/validated_* se preservan |
| Colisión/mixed-chain clasificada por último | Requiere conciliación, sin merge ni winner implícito |
| NULL a0/epoch o peso10080 | DTOs nullable, resultado propio y pruebas fuente/renderer/Angular |
| Media/compuesto de poblaciones mezcladas | Dos bloques/coberturas, sin KPI conjunto, backend source común |
| Consultas lentas/uso de memoria | Predicados de referencia en repositorio e índices comprobados, sin aggregates persistidos prematuros |
| Cambio global de chart/criterios shared | Regresión de Consumo/Inventario y de las tres series anteriores |
| Escritor005 durante schema006 | Ventana coordinada, backup y no binario antiguo después de referencias nuevas |

## Project Structure

```text
specs/006-registro-semanal-uptime/
  spec.md / clarification-report.md / plan.md / research.md / data-model.md / quickstart.md
  contracts/api-ui.md / reporting.md
  checklists/requirements.md
  drafts/                         # antecedentes, no diseño activo
sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/
  domain/model/measurement/        # precisión, valor temporal y evento propuestos
  domain/policy/                   # políticas/cálculo existentes extendidos
  domain/repository/               # consultas por selección y transición
  application/{command,result,service,port}/
  api/{controller,request,response}/
  infrastructure/{parser,persistence,report}/
sarc-bakend/src/main/java/com/tidsec/sarc_bakend/shared/report/ReportPublication.java
sarc-bakend/src/main/resources/db/migration/V016__uptime_referencia_temporal.sql  # propuesta
sarc-bakend/src/test/java/com/tidsec/sarc_bakend/modules/uptime/
sarc-frontend/src/app/features/uptime/{domain,data-access,application,presentation}/
  presentation/states-chart/       # nuevo componente local propuesto
sarc-frontend/src/app/shared/ui/chart/{chart-view,chart-theme}.ts
sarc-frontend/src/app/design-system/_tokens.scss
sarc-frontend/DESIGN.md
```

**Structure Decision:** adaptar repositorio actual sarc-bakend/sarc-frontend. No crear carpeta backend/frontend paralela, paquetes raíz nuevos ni imports privados entre features. Los nombres de tablas/clases nuevas son propuestas de diseño para implementar después de tasks/analyze.

## Complexity Tracking

Sin violaciones constitucionales ni capas/dependencias nuevas. El evento temporal es necesario para conservar el antes/después y validación sin mutar revisiones inmutables; no es un sistema de auditoría paralelo. El resultado de referencia y su identidad evitan primitivos falsos y cálculos duplicados. El tercer estado expresa ambigüedad ya aprobada, no fuente inventada. Se rechazaron reconstrucción genérica del módulo, agregados persistidos prematuros y bibliotecas nuevas.
