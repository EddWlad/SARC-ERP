# Implementation Plan: Ajuste del registro semanal de uptime

**Git real**: `main`, HEAD `3752365`, con implementación 005 sin commit. **Selector Spec Kit**: `005-uptime-disponibilidad`. **Fecha**: 2026-11-04. **Spec**: [spec.md](spec.md), incluidas las seis respuestas de esta fecha. No crear/cambiar rama en esta fase.

**Estado**: diseño incremental concluido; pendiente `$speckit-tasks` y `$speckit-analyze` antes de implementar. Este plan sustituye el diseño anterior de asignar lunes a lunes como intervalo real del Excel. No certifica código corregido ni modifica los marcadores de las tareas existentes.

## Summary

Adaptar el módulo ya existente, sin reconstruirlo: captura de 7 días por conector con fechas reales, Excel semanal con horario desconocido y semana de referencia propia, identidad independiente por referencia, promedio del informe explícito y gráficos de estados. Mantener el diseño, arquitectura, seguridad y cálculos no afectados.

La captura con horario y el Excel sin horario se consultan en apartados separados. Una referencia semanal no se convierte en intervalo observado. La media de porcentajes del informe no utiliza minutos ficticios ni se combina con el promedio ponderado de capturas reales. Las semanas oficiales mantienen su tratamiento.

## Technical Context

- **Lenguajes y stack real:** Java 25, Spring Boot 4.1.1, MVC/Security, Data JPA/Hibernate, Jakarta Validation, Flyway, PostgreSQL 18; Angular 22, TypeScript 6, Material, Signals/Signal Forms, Sass y Chart.js. Confirmado en `sarc-bakend/pom.xml`, frontend `package.json` y código.
- **Convenciones reales:** DTOs `record` y mapeos `from/toCommand`; no introducir MapStruct, Spring Batch, otro motor de gráficas ni librerías nuevas. Importación mediante `ImportJobs`, `ImportFiles`, `BaseImportBatch` y `TabularReader` existentes.
- **Arquitectura:** monolito modular `api → application → domain ← infrastructure`; comunicación con inventario mediante `shared.catalog`. Frontend `data-access → application → presentation`, tipos de OpenAPI. El dominio de uptime conserva reglas y cálculo; Angular solo presenta y valida forma.
- **Persistencia:** adaptar tablas `upt_*`; nueva migración propuesta `V016__uptime_referencia_temporal.sql`, confirmando al implementar que sigue siendo el siguiente número libre. No modificar V001–V015 ni tablas de Consumo/inventario.
- **Pruebas:** JUnit/MockMvc, Testcontainers PostgreSQL 18, ArchUnit, OpenAPI; Vitest, ESLint, Prettier y build. Almacenamiento simulado. No usar BD dev ni cuentas reales.
- **Plataforma:** web responsive, cinco roles y unión de roles; America/Guayaquil, intervalos reales `[inicio, fin)` con segundos.
- **Rendimiento:** presupuesto inicial ≤500 kB; consultas p95 2 s/dashboard 5 s con 30 usuarios como objetivos, no garantías. Baseline 005 documentó límites secuenciales: repetir escenarios afectados sin atribuir aprobación de carga que no se mida.
- **Alcance:** mismas pantallas/rutas/componentes corporativos; datos de referencia por semana y datos reales por intervalo, sin mezcla de ventanas 24 h/7 d/30 d.
- **Unknowns:** decisiones de producto resueltas y registradas en `documents/control-cambios-prd.md`, CC-PRD-005-20261104. El inventario de colisiones de datos anteriores es una verificación de despliegue, no autorización para inferir fechas o fusionar registros.

## Constitution Check

| Puerta | Antes de diseño | Después de diseño | Evidencia/control |
|---|---|---|---|
| I, especificar antes de implementar | PASS | PASS | FR-048–FR-052 y criterios; sin código implementado en esta fase |
| II, reglas explícitas | PASS | PASS | Se preguntó identidad, promedio y apertura; seis respuestas registradas |
| III, migraciones inmutables | PASS | PASS | Nueva V016 propuesta; actualización desde V015, sin editar anteriores |
| D05, identidades verificadas | PASS | PASS | Resolver/alias existentes; ambiguas pendientes, sin aproximaciones nuevas |
| D06/D07, identidad de inventario | PASS | PASS | UUID del conector; no exigir exactamente dos conectores ni crear otro catálogo |
| D09 | PASS | PASS | Dos hojas solo en informe semanal; primera hoja en otras importaciones |
| D12 | PASS | PASS | Etiqueta del hecho y nombre actual; archivos emitidos inmutables |
| Jerarquía PRD/Constitución/TRD | PASS | PASS | Excepciones del Excel y su promedio formalizadas en CC-PRD-005-20261104 |
| Arquitectura existente | PASS | PASS | ArchUnit y ESLint como puertas; no módulos, dependencias o layout paralelos |
| Integridad histórica | PASS | PASS | Preservar UUID/revisiones/bytes; preflight y conciliación explícita de fechas heredadas |

No hace falta enmendar la Constitución: no se amplía D09 ni se cambia otra decisión ratificada. D10–D14 mantienen su alcance; no trasladar reglas de Consumo a uptime.

## Comportamiento actual y cambios mínimos

| Área / evidencia de código | Actual | Ajuste |
|---|---|---|
| `CaptureFacade`, `capture-page`, `measurement-form` | Período común y formulario inicial 24 h; conversión añade segundos `:00` | 7 d por defecto, semana de referencia de contexto y límites reales editables por conector, segundos intactos; preservar opciones 24 h/30 d |
| `MeasurementGridService` / `findByPeriod` | Busca por un intervalo idéntico para toda la grilla | Grilla por referencia de informe para captura semanal; identidad real se comprueba al guardar cada borrador |
| `UptimeImportService.upload`, parser `DeclaredPeriod` | Semana elegida reemplaza los límites de cada fila | Semana solo referencia; Excel actual sin límites reales conserva nulos; seguir leyendo ambas hojas y sus filas |
| `Measurement` / V013 | Fechas NOT NULL y única identidad por intervalo | Precisión temporal explícita, semana separada y claves condicionales; mismo agregado de revisiones |
| `MeasurementRules`, `PeriodRules` | Siempre reciben duración real | Separar validación intrínseca de la comparación temporal; esta última solo con límites conocidos; nunca completar Ocupado |
| `MeasurementRepositoryAdapter.FACTS` | `REPORTED_PERCENT` convierte el intervalo asignado en peso | Proyección separada de referencia; minutos evaluados desconocidos nulos, sin derivarlos del porcentaje |
| `UptimeCalculator`, `UptimeQueryService` | Agrega por minutos y selecciona por fin real | Mantener ese camino; añadir resumen del informe por media aritmética de porcentajes no redondeados y selección por semana de referencia |
| `MeasurementDetailResponse`, resumen/resultados | Fechas asumidas presentes y sin Disponible/Ocupado agregados | Metadata temporal y composición nullable explícitas; respuesta por rol en backend |
| `dashboard-page`, `measurement-page` | Sin pastel de estados | Componente local `states-chart` sobre `shared/ui/chart/ChartView`, leyenda accesible y cobertura |
| Reportes | Copian la misma presunción temporal y omiten `pendingLink` | Mismo snapshot, campos desconocidos explícitos, dos apartados, promedio rotulado y total sin atribuir |

## Clasificación de aceptación

**Ya existente, con regresión obligatoria:** alcance por conector/inventario, pegado de cuatro etiquetas, ambas hojas, minutos enteros/originales, alias, validación expresa, revisiones, exclusión reversible, permisos y archivo privado. La existencia de estas rutas no prueba la nueva semántica temporal.

**Requiere corrección:** período compartido de captura; segundos; fechas asignadas al Excel; DTOs y SQL que suponen fechas; reconocimiento de duplicados; lectura por filtros y rangos; reportes sin atribuir; errores numéricos de etiquetas. Son los escenarios adicionales 1–3, 5–6 y 8 de spec.

**Nuevo:** precisión temporal/identidad por referencia, promedio del informe como excepción documentada, apertura semanal por referencia con dos bloques y pastel con estados ausentes explícitos. Escenarios 4, 6–8 y FR-049–FR-052.

## Phase 0 — Research

Decisiones y alternativas en [research.md](research.md). Se revisaron entidad, revisiones, importación, SQL JDBC, puertos de inventario, controllers/DTOs, V013–V015, formularios/facades, wrapper Chart.js, `DESIGN.md`, ArchUnit y ESLint. No fue necesario investigar tecnologías externas ni cambiar versiones.

## Phase 1 — Diseño backend y persistencia

1. Mantener `Measurement` como cabecera, revisiones como valores y validación/exclusión como hoy. Añadir un valor de dominio temporal pequeño (`MeasurementPeriod` o equivalente) con precisión y referencia, reutilizado en captura, importación y consulta. No crear una segunda jerarquía de mediciones.
2. Precisión `KNOWN_RANGE` o `REFERENCE_WEEK`; metadata heredada `LEGACY_DECLARED` para intervalos asignados por la versión anterior cuya procedencia sea demostrable. La semana de referencia es un `LocalDate` lunes y no un instante. Límites reales solo con `KNOWN_RANGE`.
3. Dos identidades explícitas en el mismo repositorio: conocida por conector/fuente/inicio/fin; referencia por conector/fuente/ROLLING_7D/semana. Restricciones PostgreSQL e índices parciales evitan duplicación incluso en concurrencia. `LEGACY_DECLARED` conserva sus columnas originales y se concilia antes de cruzarlo con la nueva clave.
4. Reutilizar `MeasurementService`, `ImportedMeasurements`, `UptimeImportProcessor` y `PendingLinkService`: alta idéntica no escribe; diferente crea conflicto y nueva revisión justificada. Una revisión vigente sigue computando hasta validación; no fusionar Excel con capturas reales. El parser no debe decidir identidad ni permisos.
5. Validación de referencia: porcentaje de fuente obligatorio sin Ocupado, 0–100, integridad de números y estados informados, procedencia/crudos. No verificar una duración real desconocida ni usar 10080 como minutos evaluados. Mantener las reglas temporales y las tolerancias aprobadas para intervalos reales. Ventana nominal 7 d es una categoría declarada, no un reloj observado.
6. Resumen de referencia: media aritmética de porcentajes de conectores validados e incluidos, a precisión de fuente; presentación dos decimales. KPI etiquetado `REPORT_MEAN`, sin `evaluatedMinutes`. Ranking y promedio por equipo en ese apartado usan los porcentajes de referencia; como hay una medición por conector/semana no se ponderan por horas ficticias. Cada apartado conserva cobertura por conector y sin atribuir aparte.
7. Consulta semanal: última semana de referencia con datos incluidos; separar `knownRange` y `referenceReport`. En el primer bloque, última captura incluida por conector de esa referencia, cálculo por minutos real existente. No sumarlos ni producir un porcentaje general conjunto. Las capturas antiguas sin referencia siguen consultables por rango real y no reciben semana inferida. Sin referencias válidas, conservar la apertura existente por ventanas reales. Semanas oficiales, 24 h y 30 d mantienen sus caminos.
8. Composición de estados calculada en backend con los mismos predicados/corte: cuatro duraciones nullable, total conocido, cantidades de valores conocidos/ausentes y cobertura. No deducir Ocupado o minutos offline desde el uptime. Las sumas usan `long`/SQL `bigint` para evitar overflow de registros observados.
9. Mismo `REPEATABLE READ` para indicadores y contenido de reportes; render fuera de transacción. Extender `UptimeReport` y renderizadores con referencia, precisión, método, composición y `pendingLink`. Extender `ReportPublication` solo con claves de uptime necesarias y validadas; no guardar JSON libre ni referencias privadas.
10. DTOs aditivos cuando sea posible; fechas ahora nullable son cambio de contrato que exige backend/frontend coordinados. OpenAPI se regenera con la prueba de contrato; los tipos TypeScript no se editan a mano. Sin versión paralela de API.

Detalles de restricciones, transición y backfill: [data-model.md](data-model.md). Contratos: [API y UI](contracts/uptime-api.md), [reportes](contracts/reports-and-consumption.md).

## Diseño frontend dentro de SARC

- Conservar `features/uptime/{domain,data-access,application,presentation}`, rutas, navegación, shell, paneles, rejillas, tabla/cards móvil y diálogos. No wizard ni una nueva página para importar.
- `CaptureFacade`: borrador temporal por conector. `measurement-form`: Desde/Hasta reales con precisión de segundos, junto al bloque. La semana seleccionada es contexto; guardar/siguiente limpia valores y límites del conector. Mantener guardia de cambios y errores del backend.
- `ImportDialog`/`imports-page`: rótulo «Semana de referencia del informe», nota «Horario real no informado» para el modelo actual. Vista previa sigue mostrando hoja/fila, clasificación, observación y decisión. Quitar confirmación exacta cuando falten límites reales; no cambiar la importación de Inventario/Consumo.
- `DashboardFacade`: backend resuelve semana y dos apartados; no promedio ni inferencia temporal en Angular. Respuestas tardías no pisan filtros vigentes; refresco conserva datos ante fallo.
- Nuevo componente local `presentation/states-chart` compartido solo dentro de uptime. Usa `ChartView(kind='doughnut')`, leyenda HTML con cuatro estados, `QuantityFormat`, descripción accesible y cobertura. Nulos «No informado»; cero «0 min». Si faltan estados: «Distribución de tiempos informados», con proporciones referidas solo al total conocido, nunca al período entero. Sin suma positiva, texto/leyenda sin círculo artificial.
- En detalle, respetar la proyección vigente por rol; los usuarios de consulta nunca ven estados de una revisión pendiente. En dashboard solo mediciones incluidas, separadas por apartado y mismos filtros.
- `ChartTheme` solo tiene tres series. Ampliar con `series-4` dentro del sistema de tokens existente y su definición claro/oscuro, documentando orden fijo de los cuatro estados. Comprobar contraste y distinguirlos también con texto. No colores sueltos por componente, otra paleta ni dependencia.
- Mantener tipografía, espaciado, bordes y alturas de `DESIGN.md`; no emular el aspecto externo de Enerlink. Revisar solo pantallas afectadas a 360/390/768/1024/1440 × claro/oscuro, foco y lectores de pantalla.

## Históricos y despliegue seguro

Preparar diagnóstico sobre copia desechable desde V015: manual real, Excel de semana declarada, Excel confirmado exacto, correcciones y dos registros candidatos a la misma referencia. Identificar candidatos por lote/procedencia; no por coincidencia casual de un lunes. Preservar valores, UUID, revisiones, decisiones, auditoría, originales y bytes/hash de reportes.

No reetiquetar automáticamente como observado real ni degradar una aprobación exacta previa. Las fechas heredadas de asignación deben declararse como tales; no se publican como evidencia nueva de límites reales. La migración solo añade metadata/estructura y restricciones del caso nuevo. Si una importación encuentra candidatos legacy, detener esa fila con conflicto de conciliación en vez de crear un duplicado. La conciliación posterior requiere decisión explícita y queda fuera de cualquier backfill automático. El despliegue con tales casos exige revisión de esas excepciones; no se resuelven con SQL destructivo.

No arrancar backend dev para comprobar el esquema. Al implementar, hashes V001–V015 y prueba de upgrade; ninguna migración ya aplicada se reescribe. Backend/frontend coordinados por la nueva nulabilidad; no volver a un backend antiguo después de escribir referencias sin horario. Recuperación mediante backup o cambio compatible hacia adelante, conservando documentos emitidos.

## Orden de implementación propuesto

1. Pruebas y fixtures temporales: seis decisiones, idempotencia/concurrencia, privacidad, baseline de datos e historial.
2. Dominio temporal e identidad + migración nueva + repositorio/upgrade. Extender parser y pipeline sin tocar formatos de otros módulos.
3. Servicios de alta, corrección, validación/vinculación; API y tipos. Después, captura por conector y Excel de referencia.
4. Consulta por referencia, separación de agregados, composición y reportes. Un único origen de cálculo.
5. Componente de gráfico local y ajustes puntuales de páginas. Pruebas visuales/accesibilidad y rendimiento.
6. Regresión completa, actualizar evidencia y ADR 0011; generar tareas incrementales y analizar antes de ejecutar este orden.

Los tres hallazgos QA previos se integran en el alcance que atraviesan estos cambios: conteo sin atribuir por período solicitado estable ante filtros, traslado de ese total a PDF/Excel y manejo de número inicial desbordado como fila pendiente con motivo, sin abortar todo el lote. Añadir regresiones concretas; no refactor general de otros módulos.

## Pruebas y riesgos

[quickstart.md](quickstart.md) define escenarios, comandos y resultados esperados. Pruebas de dominio, integración/API, actualización de esquema, concurrencia, privacidad y Angular antes de la implementación correspondiente. La suite previa no sustituye estos casos nuevos.

| Riesgo | Prevención |
|---|---|
| NULL interpretado como fecha/0 | DTO/schema + pruebas de JSON, Angular y exportaciones |
| Doble conteo entre Excel y captura | Dos apartados, claves independientes, sin KPI conjunto |
| Media incorrecta por redondeo | BigDecimal y precisión original; redondeo de presentación |
| Legacy confundido con fechas reales | Procedencia explícita, conflicto de conciliación y sin backfill destructivo |
| Concurrencia | UNIQUE parcial + transacción + versión; prueba con dos cargas |
| Fuga de pendientes | Predicados en backend, mismas revisiones vigentes y matriz de roles |
| Cambios visuales globales | Reuso de ChartView/tokens, pruebas de las gráficas existentes al ampliar una serie |
| Presupuesto/rendimiento | Carga diferida, sin deps, consultas de referencia indexadas, p95 medido |

## Project Structure

```text
specs/005-uptime-disponibilidad/{spec,plan,research,data-model,quickstart}.md
specs/005-uptime-disponibilidad/contracts/{uptime-api,reports-and-consumption}.md
sarc-bakend/src/main/java/com/tidsec/sarc_bakend/modules/uptime/
  domain/{model,policy,repository}/
  application/{command,result,service,port}/
  api/{controller,request,response}/
  infrastructure/{parser,persistence,report}/
sarc-bakend/src/main/resources/db/migration/V016__uptime_referencia_temporal.sql  # propuesta
sarc-frontend/src/app/features/uptime/{domain,data-access,application,presentation}/
  presentation/states-chart/  # nuevo componente local propuesto
sarc-frontend/src/app/shared/ui/chart/{chart-view,chart-theme}.ts
sarc-frontend/src/app/design-system/_theme.scss
sarc-frontend/DESIGN.md
```

## Complexity Tracking

Sin violaciones constitucionales justificables ni nueva infraestructura. La extensión de precisión/identidad y la cuarta serie son necesarias para representar información aprobada que el modelo actual no admite. Reutilizar los registros con fechas ficticias o importar un componente privado de Consumo sería incorrecto; duplicar módulos/servicios/gráficas añadiría complejidad innecesaria.
