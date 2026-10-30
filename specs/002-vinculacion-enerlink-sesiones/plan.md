# Implementation Plan: Vinculación Enerlink y sesiones incompletas

**Branch**: `002-correcion-comparacion-nombres-plataforma-consumo` (rama Git real; `.specify/feature.json`
resuelve la feature como `002-vinculacion-enerlink-sesiones`)
**Date**: 2026-10-30 | **Spec**: [spec.md](spec.md)
**Input**: `specs/002-vinculacion-enerlink-sesiones/spec.md`, con las 5 aclaraciones del 2026-10-30

## Summary

El incidente tiene dos causas independientes, ninguna atribuible a una sola capa:

1. **Identidades**: la carga masiva sí procesa «Enerlink activo» y «Enerlink cargador», pero en equipos
   existentes los clasifica como `MODIFIED`. Esas filas exigen aprobación individual y, sin ella, se
   omiten al confirmar. Por eso 95 identidades quedaron `SKIPPED`, «Identidades externas» aparece vacía
   y 1.583 sesiones quedaron pendientes.
2. **Sesiones incompletas**: parser, dominio, esquema, agregados, informes y Angular suponen que energía
   y término siempre existen. Las 83 inválidas fallaron por eso.

El enfoque es mínimo: nueva clasificación para filas que solo completan identidad, comparación D05 en
todos los puntos de decisión, motivos específicos de pendiente, vinculación al reimportar y ausencia
real (`NULL`) desde el parser hasta los informes. Una migración nueva (V009). Sin endpoints, permisos,
dependencias ni módulos nuevos.

Esta fase solo documenta diseño. No se ejecutaron pruebas ni se modificaron fuentes o datos.

## Causas reales

Detalle y evidencia en [research.md](research.md).

| Síntoma | Causa | Capa | Ref. |
| --- | --- | --- | --- |
| «Identidades externas» vacía | 95 filas `MODIFIED` sin aprobar omitidas; solo 4 identidades creadas en 226 equipos | Aplicación (regla de aprobación) | R1 |
| 1.583 pendientes | Falta de datos por lo anterior: los 45 pares del archivo existen en la plantilla | Efecto de R1 | R2 |
| 83 inválidas | Vacío tratado como error: 83 por energía, 7 además por término | Parser, dominio, esquema | R3 |
| Riesgos no observados aún | Comparación exacta, un solo componente, «último gana» | Inventario y resolución | R4 |

## Estado de los criterios

Estado por lectura del código, no por ejecución.

### Ya cumplidos: conservar y proteger con regresión

| Criterio | Evidencia |
| --- | --- |
| FR-012, conteo por día de inicio | `start_date`, `ix_energy_session_period` |
| FR-015, sin duplicar por ID de origen | `uq_energy_session_source`, `seen`, `existing` |
| FR-017, reasignación explícita | `EQUIPMENT_CHANGED`, `reassigned` |
| FR-020, primera hoja y clasificación | `TabularReader` usa `getSheetAt(0)` |
| US1.5, vacíos no borran | `EquipmentLineInterpreter` |
| US4.5, revisión obsoleta | `existingSessionRevision` |
| US4.6, reportes archivados | `report_issued` inmutable |
| FR-007, permisos y aprobación de fuente | `SessionService`, `officialState` |
| Normalización D05 al resolver | `ExternalKey.comparable()` |

### Requieren corrección

| Criterio | Problema | Corrección |
| --- | --- | --- |
| FR-001, US1.2 | Completar identidad exige aprobación | `IDENTITY_COMPLETED` |
| FR-002 | Una aprobación pasa por `update` | La nueva clasificación no escribe el equipo |
| FR-003 | Exacto en inventario; `equalsIgnoreCase` en sesión | `ExternalKey.comparable()` |
| FR-004, Aclaraciones 1 y 2 | Solo CARGADOR pasa; par distinto no bloquea | Nuevas reglas en intérprete y evaluador |
| FR-005 | Equipo de la vista previa no se revalida | Resolver de nuevo al aplicar |
| FR-006, Aclaración 3 | «Último gana»; sin CARGADOR es inválida; motivo genérico | `matchIdentities`, `link_issue` |
| FR-016, US4.3 | Lectura nula rompe comparación; vaciar no se distingue | `sameAs`, `SESSION_VALUE_REMOVED` |
| FR-018 | Vinculación auditada solo en agregado | Auditoría por sesión |

### Funcionalidad nueva

| Criterio | Alcance |
| --- | --- |
| FR-008 a FR-011 | Energía y término ausentes en todo el recorrido, solo Enerlink |
| FR-013, FR-014 | Energía conocida, cobertura, «Sin datos», exclusión del ranking con nota |
| FR-007, Aclaración 5 | `LINKABLE` al reimportar |
| US3.5, SC-006 | «No informado» en consulta y exportación |

## Technical Context

**Language/Version**: Java 25; TypeScript con Angular 22.

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA/Hibernate, Jakarta Validation, Flyway;
Angular Signals, Material 3 y Sass. MapStruct y Spring Batch **no están en `pom.xml`**: los DTO usan
records con `from(...)` y los trabajos `ImportJobs` por tramos. Se conservan; ver R10.

**Storage**: PostgreSQL 18 en Docker Compose; Flyway V001–V008, se añade V009. Originales en Cloudinary.

**Testing**: JUnit, AssertJ, MockMvc, Testcontainers, ArchUnit, `OpenApiContractTest`; Vitest.

**Target Platform**: aplicación web existente.

**Project Type**: monolito modular; `api -> application -> domain <- infrastructure`.

**Performance Goals**: sin SLA nuevo. Importar y confirmar 1.724 filas sin consulta por fila adicional.
La resolución carga una vez por lote o tramo las identidades Enerlink, cientos de filas.

**Constraints**: solo Enerlink admite vacíos; sin valores sustitutos; sin reescribir históricos ni
reportes; módulos comunicados por `shared/catalog`.

**Scale/Scope**: inventario y consumo, sus DTO y pantallas relacionadas. 226 equipos y 1.724 filas
como referencia.

No quedan elementos NEEDS CLARIFICATION.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Regla | Diseño | Antes | Después |
| --- | --- | --- | --- |
| I. Especificación primero | Spec aclarada; sin implementación | PASS | PASS |
| II. Reglas explícitas | Reglas nuevas en data-model y spec; la tabla de clasificación debe trazarse a pruebas | PASS | PASS |
| III. Migraciones inmutables | Solo V009 | PASS | PASS |
| D04, D05 | Originales conservados; una sola forma de comparar | PASS | PASS |
| D09 | Sin cambio de lectura | PASS | PASS |
| D10 | No se cumple hoy; pendiente registrado en spec y resuelto por 003 | FAIL registrado | FAIL registrado |
| D11 | HALF_UP en `SessionReading`; sumas sobre valores normalizados | PASS | PASS |
| D12 | Informes nuevos con nombres actuales; archivados intactos | PASS | PASS |
| D13 | Sin cambio | PASS | PASS |
| D14 | Exclusión aclarada; empates no se cumplen hoy, pendiente registrado en spec y resuelto por 003 | FAIL registrado | FAIL registrado |

### Diferencias preexistentes fuera de alcance

Registradas en la spec según el flujo constitucional 4. Esta feature no las empeora ni las corrige; se
especifican en [003-revision-energia-ranking-empates](../003-revision-energia-ranking-empates/spec.md).
No cumplirlas hoy es una infracción conocida: «FAIL registrado» significa que no bloquea esta feature
solo mientras 003 siga como trabajo pendiente.

- **D10**: `SessionRules` rechaza energía superior a 1.000 kWh como `INVALID`, mientras la constitución
  exige observación revisable. Corregirlo afecta a Wallbox, que FR-019 deja fuera, y requiere guardar
  justificación. FR-011 conserva el comportamiento actual. El archivo del incidente no tiene casos.
- **D14**: `byEquipment` aplica `setMaxResults(5)` y desempata por UUID, por lo que un equipo empatado en
  quinto lugar puede quedar fuera. Se conserva; solo se añade la exclusión de FR-014.

## Project Structure

### Documentation (this feature)

```text
specs/002-vinculacion-enerlink-sesiones/
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
├── src/main/resources/db/migration/V009__vinculacion_enerlink_sesiones.sql
├── src/main/java/com/tidsec/sarc_bakend/
│   ├── shared/catalog/            EquipmentDirectory, ExternalKey, IdentityMatch (nuevo)
│   └── modules/
│       ├── inventory/             intérprete, evaluador, procesador, directorio, identidad
│       └── consumption/
│           ├── infrastructure/parser/   TabularSessionParser, EnerlinkSessionParser
│           ├── domain/                  EnergySession, SessionReading, SessionRules, ImportRow
│           ├── application/             ImportProcessor, EquipmentResolver, SessionService, ConsumptionCalculator
│           ├── infrastructure/          repositorio, renderers PDF y Excel, ConsumptionLabels
│           └── api/response/            SessionResponse, ConsumptionSummaryResponse, Import*Response
├── docs/adr/0009-sesiones-enerlink-incompletas.md
└── docs/api/openapi.json

sarc-frontend/src/app/features/
├── inventory/      domain/equipment-import.ts, página de lote
└── consumption/    domain, dashboard, top-equipment, share-chart, daily-table, sessions-page, import-batch-page
```

**Structure Decision**: se usa la estructura existente. Solo se crea un record en `shared/catalog` porque
inventario y consumo lo comparten.

## Diseño por bloques

Orden sugerido por dependencias.

### B1. Esquema

V009 según [data-model.md](data-model.md). Primero, porque `ddl-auto=validate` exige alinearla con
las entidades.

### B2. Identidades en inventario

1. `EquipmentLineInterpreter`: `ENERLINK_SCOPE_REQUIRED`.
2. Índice de identidades Enerlink por clave comparable, cargado una vez por clasificación y por tramo;
   no por fila, ya que el lote admite 5.000.
3. `EquipmentRowEvaluator`: tabla de reglas de data-model y `IDENTITY_COMPLETED` si `changes` del equipo
   está vacío y hay identidades por registrar o verificar.
4. `firstRepeatedKey`: comparar Enerlink con `ExternalKey.comparable()`.
5. `EquipmentImportProcessor.applyRow`: rama `IDENTITY_COMPLETED`, solo `registerIdentities`, con la
   reevaluación vigente.
6. Consultas `nextToApply` y `countWritten`, conteos del lote y respuesta.
7. `ExternalIdentityService.register`: ámbito obligatorio y conflicto normalizado en Enerlink; el diálogo
   `external-identity-dialog` exige ese ámbito.
8. Una fila con otros cambios sigue `MODIFIED`. Sin aprobar no aplica nada, tampoco su identidad, y
   `CONFIRM_MESSAGE` lo advierte (FR-001).

### B3. Resolución y motivos

1. `IdentityMatch`, `ExternalKey.isComplete()` y `EquipmentDirectory.matchIdentities`.
2. `EquipmentLookup.ByIdentity` en Enerlink aunque falte un componente; el parser deja de marcar
   `EQUIPMENT_REFERENCE_REQUIRED` solo para Enerlink.
3. `EquipmentResolver` devuelve equipo o motivo. Wallbox sigue por `BySerial`.

### B4. Sesiones incompletas

1. `TabularSessionParser`: método protegido que declara si vacío es admisible, verdadero solo en Enerlink.
   Texto inválido sigue siendo error.
2. `SessionReading.sameAs`, `SessionRules`, `EnergySession`, `SessionRevision` e `ImportRow` con nulos.

### B5. Clasificación y aplicación

1. `ImportProcessor.classifyRow`: tabla de data-model.
2. `applyNextChunk`: resolver de nuevo por tramo (R7).
3. `applyRow`: rama `LINKABLE` antes de comparar lecturas, porque hoy una lectura igual termina en
   `UNCHANGED`; `MODIFIED` que vincula. Ambas con `link(equipment, approved)` y `ENERGY_SESSION_LINKED`.
4. `SessionService.linkPending`: actualizar `link_issue` en las que siguen pendientes y auditar por sesión.
5. Mantener la regla en `isApplicable` y la consulta `nextToApply` alineadas.

### B6. Indicadores e informes

1. `EnergySessionRepositoryAdapter`: suma sin `coalesce`, conteo de lecturas y `having` en ranking.
2. `ConsumptionCalculator`: promedio, días, `equipmentWithoutEnergy`.
3. `ConsumptionLabels`, `ConsumptionPdfRenderer`, `ConsumptionExcelRenderer`: etiquetas, notas y filas sin
   `List.of` con nulos.

### B7. Frontend

1. Tipos generados; los `Record` exhaustivos obligan a cubrir clasificaciones y resultados.
2. Nuevas etiquetas y conteos en `equipment-import.ts` y `energy-import.ts`; `CONFIRM_MESSAGE`.
3. `linkIssue` con texto en histórico e importación.
4. `dashboard-page`: «Energía conocida», «Sin datos» y sesiones sin energía.
5. `top-equipment`: nota de excluidos. `share-chart`, `daily-table`: nulos.
6. `sessions-page`, `import-batch-page`: «No informado», sin rango de hora incompleto.
7. `sessions-facade` y `sessions-page`: resumen oficial del período con `summaryRequest`, mismo período y
   filtros de la lista, sin estado ni texto (US3.2, FR-013). No requiere endpoint nuevo.
8. Accesibilidad: el estado se expresa con texto, no solo con color.

### B8. Documentación

ADR 0009: sesiones con energía o término ausentes, `IDENTITY_COMPLETED` y `LINKABLE`. Actualizar
Javadoc que afirme que toda sesión tiene energía. `openapi.json` regenerado.

## Pruebas

Cada requisito nuevo trae pruebas (`sarc-bakend/CLAUDE.md`). Base existente: `EquipmentLineInterpreterTest`,
`EquipmentImportApiTest`, `SessionParsersTest`, `EnergySessionTest`, `ConsumptionApiTest`, `InventoryApiTest`,
`OpenApiContractTest`, `ArchitectureTest`; `import-batch-facade.spec`, `consumption-api.spec`,
`equipment-imports-api.spec`.

| Área | Casos |
| --- | --- |
| Interprete y evaluador | Tabla de reglas completa, solo un componente, D05, idempotencia |
| Importación de equipos | Identidad sin aprobación, `version` intacta, conflicto bloquea fila y no el lote, mezcla sigue `MODIFIED` |
| Parsers | Vacíos en Enerlink admitidos; Wallbox rechaza; texto y negativos rechazados; espacios como vacío |
| Dominio | `sameAs` con nulos, 0 frente a nulo, HALF_UP, término igual al inicio, término anterior |
| Resolución | Cuatro estados, ambigüedad, identidad sin verificar |
| Importación de sesiones | Tabla de clasificación, las cinco de US2.1, inventario cambiado al confirmar, revisión obsoleta |
| Agregados, Testcontainers | Matriz A–E: 5 sesiones, 3,750 kWh, 2 sin energía; solo ausentes; cero real |
| Ranking | Exclusión, 0 kWh participa, nota |
| PostgreSQL | V009 sobre datos V008; `ddl-auto=validate`; CHECK de nuevos valores; datos previos intactos |
| Informes | PDF y Excel sin error con nulos, cifras iguales al tablero |
| Contrato | `OpenApiContractTest` |
| Angular | Etiquetas, «Sin datos», «No informado», cero frente a nulo, cobertura |
| Regresión | Wallbox, KIA por Enerlink, pruebas, conectores, KIA sin código, reportes archivados |

La prueba de SC-007 usa un archivo sintético con la misma forma: los datos reales permanecen fuera del
repositorio. Validación manual en [quickstart.md](quickstart.md).

## Datos existentes y despliegue

1. V009 no modifica filas. Equipos, sesiones y lotes completados permanecen.
2. Descartar el lote `READY` `eba003e1`; sus 83 inválidas no guardaron lectura.
3. Reimportar la plantilla y luego el archivo de sesiones. Quedan unas 95 identidades, de ellas al menos
   93 solo identidad.
4. Si hubiera sesiones pendientes en otro entorno, ejecutar «Vincular pendientes» o reimportar.

**Riesgo**: V009 permite nulos. Revertirla tras guardar sesiones incompletas exige tratarlas antes.

## Complexity Tracking

Sin violaciones constitucionales que justificar.
