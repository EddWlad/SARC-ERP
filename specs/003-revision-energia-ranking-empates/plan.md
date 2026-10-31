# Implementation Plan: Revisión de energía alta y empates del ranking

**Branch**: `codex/003-revision-energia-ranking-empates` (rama Git real; selector de Spec Kit: `003-revision-energia-ranking-empates`)
**Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)
**Input**: `specs/003-revision-energia-ranking-empates/spec.md`, con las 6 aclaraciones aprobadas del 2026-10-30; prevalece sobre este plan, PRD y TRD en las reglas específicas de la feature.

**Estado**: diseño actualizado sobre Feature 002 en `cb9a763`. Sin implementación ni ejecución de pruebas de producto durante esta planificación. `tasks.md` conserva su versión anterior y debe actualizarse con este diseño antes de implementar.

## Summary

Implementa D10 y D14 en consumo.

- **D10**: una lectura sobre 1.000 kWh deja de ser invalidez. La sesión se guarda en `ENERGY_REVIEW`, fuera de los
  indicadores, y una decisión con justificación, en tabla de historial, la libera o la excluye.
- **D14**: el ranking se calcula completo, con posiciones compartidas y salto. El Top son las posiciones hasta 5.

El diseño aprovecha que `VALIDATED` ya es el único filtro de indicadores oficiales y que `officialState()` es la regla
única de estado. Esta fase solo documenta diseño: no se ejecutaron pruebas ni se tocó código o datos.

## Estado de los criterios

Por lectura de código.

| Grupo | Criterios |
| --- | --- |
| Ya cumplido parcialmente, conservar | US2.1 coherencia y formato; US2.4 marca de prueba; FR-008 idempotencia y revisión de correcciones; FR-011 HALF_UP; FR-015 fuente única de cálculo; FR-016 menos de cinco/exclusión sin energía de 002; FR-017 permisos, históricos y archivados. Estas partes no prueban cumplimiento completo del requisito ampliado |
| Requiere corrección | US1.1/5: >1.000 invalida y pierde lectura; US1.7/9: aplicar corrección debe recalcular estado y conservar decisión si energía no cambia; US2.2–4/7–8: integrar revisión en vínculos, aprobaciones y prueba; US3.1–6: límite por UUID, numeración y consistencia del Top |
| Nuevo | US1.2–4/8: decidir con justificación, historial, permisos y concurrencia; US1.6: preservar decisiones al reimportar; US2.5–6: totales energéticos separados e informes con justificaciones; FR-010: filtros; FR-013–014: nota de empate y posiciones compartidas |

### Fuentes y estado observado

- PRD v1.1: pp. 4–6 (persistencia, aprobación, calidad y RBAC), 10–11 (RF-003.1–17, CA-003-01–05). Se preservan clave por fuente/ámbito/sesión, corrección aprobada y misma base de cálculo para tablero/PDF/Excel.
- TRD v1.0 revisión técnica: pp. 4–6 (módulos, DTO, errores, capas Angular), 8–11 (esquema, importación y métricas), 15 (instantánea y archivo), 19 (verificación). Constitución D10/D14 y spec aprobada concretan revisión energética y empates.
- Repositorio real: V005 crea `NUMERIC(12,3)`, V007 añade aprobación de fuente/reasignación y reportes archivados; V009 permite energía/término nulos y usa `LINKABLE`, `linkableRows`, no `LINK_PENDING` como clasificación nueva. Mantener los nombres implementados por 002.
- Docker consultado en modo lectura: no hay contenedores activos. No se consultó un catálogo PostgreSQL vivo ni su historial Flyway. El esquema analizado es el reproducible por V001–V009 más los mappings JPA; verificar catálogo y `ddl-auto=validate` en PostgreSQL 18 desechable durante implementación. No levantar ni migrar la base local en esta fase.
- El usuario confirmó el recorrido real de 002; la anomalía horaria del archivo externo queda fuera del alcance y no autoriza omitir validaciones temporales. La casilla T075 de 002 sigue pendiente documentalmente; no modificarla desde esta feature.

## Technical Context

**Language/Version**: Java 25; TypeScript con Angular 22.

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA/Hibernate, Jakarta Validation, Flyway; Angular Signals,
Signal Forms, Material 3. El TRD y el prompt mencionan MapStruct/Spring Batch, pero no existen en `pom.xml`: el código usa DTO records con `from(...)`/`toCommand(...)` y `ImportJobs` por tramos (ADR 0005). Este plan conserva esos mapeos explícitos, SOLID y validaciones backend; no introduce ambos frameworks ni una conversión general de mappers para esta feature. Se registra la discrepancia en research.md (R11).

**Storage**: PostgreSQL 18; migración nueva V010 después de V009 existente de 002. Diseño de esquema, no migración aplicada durante planificación.

**Testing**: JUnit, AssertJ, MockMvc, Testcontainers, ArchUnit, `OpenApiContractTest`; Vitest.

**Target Platform**: aplicación web existente.

**Project Type**: monolito modular; `api -> application -> domain <- infrastructure`.

**Performance Goals**: sin SLA nuevo. El Top agrupa todos los equipos del período (cientos) y añade una consulta de
aprobadas sobre umbral.

**Constraints**: una sola regla de estado; decisiones de solo inserción; umbral fijo; sin reescribir históricos.

**Scale/Scope**: módulo de consumo, sus informes y pantallas. Inventario no cambia.

No quedan elementos NEEDS CLARIFICATION.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Diseño | Antes | Después |
| --- | --- | --- | --- |
| I. Especificación primero | Spec aclarada; sin implementación | PASS | PASS |
| II. Reglas explícitas | Spec cubre reglas y seis aclaraciones: decisiones conservadas por energía, conteos excluyentes y límite de representación separado del umbral | PASS | PASS |
| III. Migraciones inmutables | Solo migración nueva | PASS | PASS |
| D10 | Observación, decisión, justificación y aprobación antes de sumar | Objetivo | PASS |
| D11 | Umbral y sumas sobre valores normalizados | PASS | PASS |
| D12 | Archivados intactos | PASS | PASS |
| D13 | Sin cambio | PASS | PASS |
| D14 | Empatados incluidos, indicados y sin exclusión por UUID | Objetivo | PASS |

PASS significa conformidad del diseño, no pruebas ejecutadas. No quedan excepciones constitucionales ni decisiones funcionales abiertas dentro de este alcance. Antes de implementar se debe actualizar `tasks.md` y ejecutar análisis de consistencia; el diseño no reduce D10/D14 por conservar el código actual.

## Project Structure

### Documentation (this feature)

```text
specs/003-revision-energia-ranking-empates/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/api-changes.md
└── tasks.md             # lo genera speckit-tasks
```

### Source Code

```text
sarc-bakend/
├── src/main/resources/db/migration/V010__revision_energia.sql
├── src/main/java/com/tidsec/sarc_bakend/modules/consumption/
│   ├── domain/model/session/    EnergySession, SessionState, EnergyReview y EnergyReviewStatus (nuevos), SessionRevision
│   ├── domain/policy/           SessionRules
│   ├── domain/repository/       SessionCriteria, EnergySessionRepository
│   ├── application/             SessionService, SessionQueryService, ConsumptionCalculator, ConsumptionReportService, ImportProcessor
│   ├── infrastructure/          repositorio, SessionPredicates, ConsumptionPdfRenderer, ConsumptionExcelRenderer, ConsumptionLabels
│   └── api/                     EnergySessionController, nuevos DTO, respuestas ampliadas
├── docs/adr/0010-revision-de-energia-y-empates.md
└── docs/api/openapi.json

sarc-frontend/src/app/features/consumption/
├── domain/consumption.ts, energy-import.ts
├── data-access/, application/sessions-facade.ts
└── presentation/   sessions-page, import-batch-page, dashboard-page, top-equipment, session-state-badge, diálogo de revisión (nuevo)
```

**Structure Decision**: estructura existente. Un agregado secundario en `model/session` y un diálogo nuevo.

## Diseño por bloques

### B1. Modelo y migración

V010, enums, entidad de decisión y repositorio, según [data-model.md](data-model.md).

### B2. Reglas de sesión

1. `SessionRules`: consulta de revisión, `ENERGY_NOT_STORABLE`, fin del rechazo por umbral.
2. `EnergySession`: precedencia en `officialState()`, reevaluación en `apply`, `decideEnergy`, `approveSource` por condición. Comparar energía anterior/nueva normalizada antes de mutar: igual conserva PENDING/APPROVED/REJECTED; distinta y alta reinicia PENDING; ausente o no superior limpia condición. Nunca rescatar una aprobación vieja al volver a un valor histórico.
3. `SessionRevision` conserva `energy_review`.

### B3. Importación

Observación en fila, contador del lote y filtro `aboveThreshold`. La clasificación no cambia.

Contrato completo de validez (I1): `ParsedSession.isValid()` exige lectura presente y ausencia de errores bloqueantes; solo `ENERGY_ABOVE_THRESHOLD` se admite como aviso no invalidante en este módulo. Cualquier otro código del parser sigue bloqueando. Ajustar `TabularSessionParser` para conservar lectura cuando únicamente hay ese aviso, `SessionRules.requireValid` para no lanzarlo como error y `ImportProcessor.classifyRow` para conservar los avisos de `parsed.issues()` al añadir sus propias observaciones. No cambiar `RowIssue` compartido ni aceptar como válidas filas sin lectura. Probar parser→ParsedSession→clasificación→confirmación, no solo parser.

Semántica del contador (A1): `energyReviewRows` conserva su nombre técnico, pero cuenta filas interpretables con lectura normalizada >1.000, incluida repetida/idéntica/duplicada si conserva lectura; no es número de sesiones pendientes de decisión. Etiqueta «Lecturas superiores a 1.000 kWh», filtro `aboveThreshold` con el mismo universo. Se calcula al preparar la vista previa y no se reescribe por decisiones posteriores ni sobre lotes históricos. Aviso neutral sobre el umbral; estado/decisión actual de la sesión determina si se exige nueva revisión. No prometer que una idéntica aprobada dejará de sumar ni que una rechazada perderá su decisión.

### B4. Casos de uso y API

1. `SessionService.decideEnergy`, con versión, justificación y auditoría `ENERGY_SESSION_REVIEWED` atómicas. Cada decisión aceptada incrementa `version`, incluso APPROVED→APPROVED: puerto de escritura con incremento optimista forzado y flush antes de devolver DTO. `revision` de lectura no se incrementa por decidir; historial guarda ambas referencias, `sessionRevision` y `sessionVersion`, para orden inequívoco y protección de concurrencia. Dos solicitudes con igual versión no pueden insertar dos decisiones válidas; el conflicto revierte sesión, decisión y auditoría.
2. `SessionQueryService.energyReviews`.
3. `linkPending` cuenta `awaitingEnergyReview`.
4. Controlador, DTO y filtro `energyReview`.
5. `SessionResponse.canApproveSource` calculado por backend a partir de equipo vinculado, fuente no preferente y `sourceApproved == false`, independientemente del estado principal. No representa autorización: `@PreAuthorize(ConsumptionPermissions.EDITORS)` sigue siendo obligatorio. Usarlo en ambos menús Angular para permitir fuente antes o después de energía.
6. Reutilizar `GlobalExceptionHandler`: validación sintáctica 400, negocio 422, no encontrado 404, permiso 403, `CONCURRENT_MODIFICATION` 409. Justificación recortada 1–500 se valida también en dominio/caso de uso; no trasladar reglas al formulario ni crear excepciones genéricas nuevas.

### B5. Ranking

1. `byEquipment` sin límite.
2. `ConsumptionCalculator`: orden, posiciones y corte.
   Construir ranking completo una vez con energía oficial y nombres actuales; Top = posiciones <=5. El ranking completo Excel sigue completo; el subconjunto Top de los informes coincide con el tablero. UUID solo estabiliza nombres iguales y nunca cambia pertenencia al corte.
3. `RankedEquipment.position` en respuesta.

### B6. Informes

Totales nuevos, `EXCLUDED` con los dos estados, numeración por `position`, nota de empate y columnas de Excel con la
justificación vigente, leída en la misma instantánea.

Los totales por estado son excluyentes: TEST > PENDING_LINK > ENERGY_REJECTED/ENERGY_REVIEW > AWAITING_APPROVAL > VALIDATED. `energyReview`/`energyRejected` resumen solo esos estados principales; el filtro `energyReview` consulta la condición energética y sí encuentra TEST/PENDING_LINK. `approvedAboveThreshold` es subconjunto de VALIDATED y nunca se añade otra vez al total. Mantener filtros por fecha de inicio y energía conocida/ausente de 002. Las justificaciones se consultan por grupos en la misma lectura REPEATABLE_READ de los datos; renderizado y archivo fuera de esa transacción como hoy. Ampliar EXCLUDED y el cálculo del límite de filas de `ConsumptionReportService`, no solo el contenido de los renderers.

### B7. Frontend

1. Tipos generados; etiquetas y badge de los estados.
2. Histórico: filtro, acciones aprobar y rechazar, diálogo con justificación e historial.
3. Importación: aviso, contador y filtro.
   El parser conserva lectura cuando solo hay aviso energético; `issues` no vacío ya no puede equivaler por sí solo a INVALID. Separar errores bloqueantes y observación de energía sin cambiar `RowIssue` global ni otras fuentes de errores. Mantener las aprobaciones ordinarias de corrección y fuente no preferente.
4. Tablero: tarjetas por revisar y rechazadas.
5. `top-equipment`: posición y nota; no asumir cinco elementos.
6. Accesibilidad AXE y WCAG AA en diálogo y estado expresado con texto.
7. Fuente: menús usan `canApproveSource && canEdit`, no solo `state === AWAITING_APPROVAL`; tras respuesta, no anunciar «ya suma» si la sesión sigue por revisar, rechazada o TEST. La facade refresca página y resumen con la versión devuelta, mantiene filtros y muestra error obsoleto sin éxito aparente.

### B8. Documentación

ADR 0010. Corregir el Javadoc de `SessionRules`, que afirma que superar el valor es error del dato.

## Pruebas

| Área | Casos |
| --- | --- |
| Reglas | Límites de umbral con HALF_UP, negativo, no almacenable, energía ausente |
| Dominio | Tabla de precedencia completa; reevaluación; orden libre de aprobaciones; prueba y reincorporación |
| Importación | Ambas fuentes; fila observada; reimportación idéntica; revisión obsoleta; lote no cae por valor enorme |
| API | Permisos, justificación, conflicto, historial, filtros |
| Agregados, Testcontainers | Nada no oficial suma; aprobada suma una vez; sin doble conteo |
| Ranking | Todos los casos de quickstart; `compareTo`; independencia del UUID |
| PostgreSQL | Migración sobre datos previos; CHECK `VALIDATED`; inserción de decisión |
| Informes | Coincidencia con tablero, columnas, nota, posiciones |
| Contrato | `OpenApiContractTest` |
| Arquitectura | `ArchitectureTest` |
| Angular | Etiquetas exhaustivas, diálogo, más de cinco en Top, nota visible solo con empate |
| Regresión | Lecturas normales, ranking sin empate, 002 |

Casos adicionales obligatorios por las últimas aclaraciones y el código real:

- Aprobada y rechazada a 1.500 kWh: corregir únicamente término conserva decisión, no inserta otra y revalida las demás condiciones. Cambiar a 1.800 reinicia revisión; volver a 1.500 no recupera una aprobación anterior automáticamente.
- Decidir dos veces APPROVED con la misma versión (también en paralelo) produce una sola aplicación; repetir con versión nueva permite una nueva decisión justificada. Probar rollback completo y orden de historial con timestamps iguales usando versión de sesión.
- TEST + pendiente + revisión aparece solo en TEST; pendiente + revisión solo en PENDING_LINK; el filtro energético encuentra ambos. La suma de categorías principales no cuenta sesiones duplicadas y el subtotal de aprobadas no se agrega al total.
- UI permite aprobar fuente mientras hay revisión energética, sin ampliar roles; respuesta que aún no es VALIDATED no muestra mensaje de incorporación oficial.
- Parser: fila alta mantiene lectura y aviso, inválida real no llega a persistencia; mezcla de fila no representable y válida no derriba lote. Cero/ausencia y límites se evalúan tras HALF_UP.
- Pruebas unitarias de `SessionRules`, `EnergySession` y calculadora; integración PostgreSQL de V010, agregados y concurrencia; API para CSRF/roles/validación/versiones; Angular para permisos, ambos menús, filtros, estados, diálogo y Top ampliado; PDF/Excel para cifras, posiciones, justificación vigente y copias archivadas.
- Regresión conjunta G1: ejecutar el lote mixto exacto de quickstart (normal, cero, ausencia, alta, ambigua, LINKABLE, duplicada, corrección y error real). Comparar clasificación por fila, siete sesiones persistidas, cinco oficiales/34 kWh conocidos/una sin lectura, una energética por revisar y una pendiente de vínculo. Reimportarlo dos veces no altera sesiones, revisiones, decisiones ni indicadores. Mantener también toda la suite de 002; este caso combinado no la sustituye.

Validación manual en [quickstart.md](quickstart.md).

## Datos existentes y despliegue

1. La migración no modifica filas. El código actual impide altas superiores al umbral, pero eso no prueba ausencia de datos introducidos fuera de la aplicación: verificar condiciones en el entorno objetivo antes de desplegar. No se inspeccionó una base viva en esta planificación.
2. Filas ya rechazadas se recuperan reimportando la fuente. No se verificó cuántas existen.
3. Un valor nuevo de `SessionState` requiere desplegar backend y frontend juntos.
4. Cambios en totales y ranking solo afectan a informes nuevos.

**Riesgo**: revertir tras guardar sesiones nuevas exige tratarlas antes, porque el esquema anterior no admite esos estados.

## Orden de implementación y validación

1. Actualizar tareas conforme a este plan y analizar consistencia con spec. No comenzar desde la regla de correcciones antigua de T015.
2. Preparar sandbox PostgreSQL 18, almacenamiento mock/temporal y puertos propios; registrar línea base. No usar el Compose local del usuario ni sus lotes.
3. Escribir pruebas dirigidas, implementar V010 y mappings juntos, validar migración V009→V010 y arranque; no exigir `ddl-auto=validate` antes de mapear columnas nuevas.
4. Reglas y flujo de importación; decisiones atómicas y consultas; estados y agregados; DTO/contrato generado; informes y Angular; ranking completo y empates. Reutilizar componentes y puertos existentes.
5. Ejecutar backend unitarias/integración/API, contrato sin modo update y ArchUnit; frontend tests, lint y build. Recorrido de navegador aislado y comparación PDF/Excel; detener únicamente recursos creados para QA.

No se crea V010, endpoints ni código como parte de este comando. Los pasos describen trabajo posterior.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
