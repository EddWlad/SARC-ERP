# Research: Revisión de energía alta y empates del ranking

Evidencia actualizada por lectura de código post002 (`cb9a763`) y documentos el 2026-10-30. No se ejecutaron pruebas ni SQL. Docker no mostró contenedores activos: no se certifica catálogo ni contenido de una base viva. Se contrastaron migraciones V001–V009 y JPA. El usuario confirmó el recorrido de 002; no se conoce cuántas filas Wallbox históricas fallaron por el umbral.

## R1. Comportamiento actual de D10

**Evidencia**: `SessionRules.check` añade `ENERGY_OUT_OF_RANGE` sobre `MAX_ENERGY_KWH`. `TabularSessionParser`
devuelve entonces lectura nula y `ImportRow.invalid` descarta el dato. `requireValid` repite la regla en
`EnergySession.apply`. Ninguna sesión puede superar 1.000 kWh hoy, y el valor rechazado no se conserva.

**Decision**: el umbral deja de ser error. `SessionRules` expone la consulta «requiere revisión» y añade una
observación informativa `ENERGY_ABOVE_THRESHOLD` a la fila, que sigue su clasificación normal.

**Rationale**: FR-001 y la aclaración 1. La fila se confirma sin aprobación por fila.

## R2. Riesgo nuevo: desborde numérico

**Hallazgo**: el umbral protegía también `NUMERIC(12,3)` en `energy_session`, `energy_import_row` y
`energy_session_revision`. Sin él, un valor de 10.000.000.000 kWh haría fallar la inserción y todo el lote.

**Decision**: nueva invalidez `ENERGY_NOT_STORABLE` para valores que excedan la capacidad de la columna
(mayor o igual a 1.000.000.000 kWh).

**Rationale**: es un límite técnico, no de negocio, y evita que una fila tumbe el lote. No contradice D10:
ese valor no puede guardarse como dato. Quedó declarado en los casos límite de la spec (principio II).

**Alternatives considered**: ampliar la columna solo traslada el límite y exige reescribir tablas.

## R3. Cómo modelar la condición de revisión

**Decision**: dos niveles.

- `energy_review` en la sesión, con `PENDING`, `APPROVED`, `REJECTED` o nulo. Es la condición independiente que
  pide la spec.
- Dos valores nuevos de `SessionState`, `ENERGY_REVIEW` y `ENERGY_REJECTED`, derivados por `officialState()`.

**Rationale**: `SessionState.VALIDATED` es hoy el filtro único de indicadores oficiales, usado por calculadora,
informes y consultas. Mantenerlo evita cambiar cada agregado y hace imposible que una sesión por revisar sume
por omisión. `officialState()` ya es la regla única al importar, vincular, reasignar y reincorporar pruebas.

**Precedencia**: `TEST` > `PENDING_LINK` > `ENERGY_REJECTED` > `ENERGY_REVIEW` > `AWAITING_APPROVAL` > `VALIDATED`.

**Consecuencia**: una sesión está en un solo estado y no se cuenta doble. Una por revisar sin equipo figura en
«Pendientes»; el filtro por `energy_review` la encuentra de todos modos.

**Alternatives considered**: solo columna exigiría añadir un predicado a todos los agregados. Solo estados perdería
la decisión cuando la sesión pasa a prueba o pendiente.

## R4. Orden libre entre aprobaciones

**Hallazgo**: `approveSource` exige `state == AWAITING_APPROVAL`. Con la precedencia anterior, una sesión por revisar
no estaría en ese estado y fallaría, en contra de US2.3 («y viceversa»).

**Decision**: `approveSource` valida que la fuente no sea preferente ni esté aprobada, y recalcula con
`officialState()`.

También `linkPending` cuenta como `linked` todo lo que no sea `AWAITING_APPROVAL`. Debe contar `ENERGY_REVIEW` y
`ENERGY_REJECTED` aparte.

## R5. Decisiones como historial

**Decision**: tabla de solo inserción `energy_session_review`. La fila más reciente rige; `energy_review` guarda su
efecto para filtrar.

**Rationale**: FR-004 y aclaración 2 exigen varias decisiones conservadas, consultables tras correcciones. La
versión de la sesión evaluada cubre FR-005, con `requireVersion`, igual que `approveSource`.

**Alternatives considered**: `AuditTrail` no se consulta como dato de negocio y no alimentaría el Excel.

## R6. Efecto de correcciones

**Decision**: `EnergySession.apply` reevalúa tras cambiar lectura:

| Lectura nueva | Efecto |
| --- | --- |
| No supera el umbral o no informada | `energy_review` nulo; decisiones quedan en historial |
| Supera, misma energía normalizada que la sesión actual | Conserva `PENDING`, `APPROVED` o `REJECTED`, sin otra decisión |
| Supera, energía normalizada distinta de la actual | `PENDING`, sin recuperar decisiones antiguas sobre ese valor |

Reimportación idéntica no pasa por `apply`, por lo que conserva toda decisión. Comparar energía antes de copiar campos, no la lectura completa con `sameAs`. Corregir término con iguales kWh conserva también rechazo; revalida otras condiciones y recalcula estado sin perder TEST. El `apply` actual solo copia campos y debe incorporar reevaluación.

## R7. Comportamiento actual de D14

**Evidencia**: `byEquipment` usa `setMaxResults(limit)` y orden `energía desc, equipmentId asc`. El dashboard y el PDF
reciben esa lista; el ranking completo usa `limit = 0`. Excel y PDF numeran con el índice.

**Decision**: consultar siempre el ranking completo y calcular posiciones en `ConsumptionCalculator`. `RankedEquipment`
añade `position`. El Top son las filas con `position <= 5`.

**Rationale**: el orden por nombre requiere datos de inventario que el módulo solo obtiene por `EquipmentDirectory`, ya
consultado aquí. Se comparan valores con `compareTo`, de modo que la escala no separe empates. El volumen es de
cientos de equipos y la consulta ya agrupa.

**Alternatives considered**: `rank()` por SQL nativo daría la misma numeración, pero rompe el enfoque Criteria del
adaptador y no resuelve el nombre.

Un desempate final por identificador solo fija el orden visual de nombres idénticos; no excluye a nadie (FR-014).

## R8. Informes

**Decision**: `ConsumptionSummary` añade totales `energyReview`, `energyRejected` y `approvedAboveThreshold`. PDF los
muestra en resumen y fuera de indicadores. Excel añade columnas «Excepción de energía» y «Justificación» y
`ConsumptionReport` lleva la justificación vigente por sesión aprobada, cargada por tramos en la misma instantánea.

## R9. Vista previa

**Hallazgo**: las filas se filtran solo por clasificación, y FR-010 pide filtrar observadas.

**Decision**: filtro `aboveThreshold` sobre `energy_kwh` en el listado de filas, y contador `energy_review_rows` en el
lote. Sin columna nueva en la fila.

## R10. Dependencia con 002

**Decision**: 002 está integrada; V009 existe y esta feature reserva V010 sin crearla en planificación. Conservar nombres reales post002 `LINKABLE`, `linkableRows` y resultado `LINKED`, sin renombrarlos a LINK_PENDING.

002 hace `energy_kwh` nulable, deja `byEquipment` con `having` y cambia `EnergyTotals`. Esta feature no añade
dependencia técnica fuerte: la revisión ignora energía nula y el cálculo de posiciones usa lo que devuelva `byEquipment`.

## R11. Stack

`pom.xml` no incluye MapStruct ni Spring Batch. Se mantienen records con `from(...)` e `ImportJobs`.

**Rationale**: aunque TRD y prompt los mencionan, agregar frameworks y reemplazar mappers/motor sería transversal. ADR 0005 documenta el motor propio. Se conserva separación de responsabilidades y validación backend; no se presentan como dependencias instaladas.

**Alternatives considered**: introducir ambos ahora ampliaría configuración, mapeos y reinicio de lotes sin necesidad para 003.

## R12. Fuentes normativas y esquema

**Decision**: spec con seis aclaraciones y constitución prevalecen. PRD v1.1 pp. 4–6, 10–11: persistencia/calidad/permisos, RF-003.1–17 y CA-003-01–05. TRD revisión pp. 4–6, 8–11, 15 y 19: módulos, DTO, errores, frontend, esquema/importación, instantáneas y pruebas.

**Evidence**: V005 crea NUMERIC(12,3), no el orientativo NUMERIC(14,3) del TRD p.4. CHECK exige >=0, no <=1.000. V007 añade sourceApproved, revisión de equipo e informes archivados; V009 mantiene nulos energía/término y LINKABLE. `SessionRules.java:32–34` rechaza >1.000; `TabularSessionParser.java:93–96` pierde lectura por cualquier issue. Separar aviso energético de errores verdaderos.

**Rationale**: 1e9 es límite de representación actual declarado en spec, no interpretación física del umbral. No se descarta por >1.000.

**Alternatives considered**: arrancar Compose dev para inspeccionar puede alterar el entorno; comprobar catálogo/Flyway y arranque después en sandbox. No se inventan resultados SQL.

## R13. Concurrencia de decisiones repetidas

**Evidence**: `shared/persistence/BaseAuditableEntity.java:43–44,81–85` usa @Version/requireVersion; `shared/error/GlobalExceptionHandler.java:67–70` devuelve 409. APPROVED→APPROVED puede no ensuciar entidad ni incrementar versión.

**Decision**: cada decisión fuerza incremento optimista mediante puerto/adaptador y flush antes de respuesta. Guardar `session_version` evaluada, distinta de `session_revision`, única por sesión/versión y ordenada descendente. Una transacción escribe sesión, decisión y auditoría; el conflicto revierte las tres. Actor y fecha vienen del servidor.

**Rationale**: protege FR-005 con decisiones iguales y timestamps coincidentes sin incrementar revisión de lectura por decidir.

**Alternatives considered**: timestamp/UUID solos no garantizan orden; dirty checking solo no cubre estado igual.

## R14. Aprobación de fuente en UI

**Evidence**: `presentation/sessions-page/sessions-page.html:99,186` ofrece fuente solo en AWAITING_APPROVAL; `sessions-page.ts:106` anuncia que ya suma. DTO no expone elegibilidad independiente.

**Decision**: `SessionResponse.canApproveSource` calculado por equipo/fuente/sourceApproved, combinado con permiso EDITORS en ambos menús. Mensajes respetan estado devuelto. HTTP sigue en data-access y estado en facade.

**Rationale**: permite ambos órdenes de US2.3 sin duplicar política de fuente en Angular ni ampliar RBAC.

**Alternatives considered**: comprobar más estados en UI duplica reglas y no cubre todas las combinaciones.

## R15. Conteos y reportes

**Decision**: resúmenes por estado exclusivo; filtro energético independiente. `approvedAboveThreshold` es subconjunto oficial, no sumando adicional.

**Evidence**: `ConsumptionCalculator.java:55–67` agrega estados separados; `application/sessions-facade.spec.ts:26` protege separación de filtros de listado/resumen. `ConsumptionReportService.java:54,96–110` requiere ampliar EXCLUDED y cómputo del límite del informe además de renderers. Justificaciones por grupos en la misma instantánea REPEATABLE_READ.

**Rationale**: implementa opción A; una sesión TEST/PENDING_LINK observada se encuentra por filtro sin contarse dos veces.

**Alternatives considered**: totales superpuestos contradicen la aclaración.

## R16. Cierre de hallazgos I1, A1 y G1 del análisis

**Decision (I1)**: definir validez en todo el recorrido. `ParsedSession.java:19–20` exige issues vacío; `ImportProcessor.classifyRow:144` lo consulta y en línea 150 crea otra lista de issues. Cambiar el contrato de consumo para admitir solo ENERGY_ABOVE_THRESHOLD como aviso con lectura válida, conservarlo al clasificar y no lanzarlo desde requireValid. Todo otro error permanece bloqueante; sin lectura nunca es válido. No generalizar a `RowIssue` compartido.

**Rationale**: conservar lectura en parser solamente no basta; evita tanto rechazar observaciones válidas como admitir errores de formato/fechas de la 002.

**Decision (A1)**: contador técnico energyReviewRows = filas interpretables sobre umbral, independiente de clasificación o decisión actual; etiqueta neutral y semántica exacta en contrato/modelo. Conservar aprobación/rechazo al reimportar idénticas. No reescribir contadores de lotes históricos.

**Rationale**: los kWh por sí solos no permiten afirmar que una sesión previamente aprobada volverá a revisión. El contador no es KPI de sesiones pendientes.

**Decision (G1)**: prueba API/Testcontainers con lote mixto reproducible y oráculo numérico en quickstart, ejecutada además de la suite 002. Dos reimportaciones, estado por fila y comparación tablero/PDF/Excel deben demostrar aislamiento funcional de la revisión nueva.

**Alternatives considered**: cambiar el código global de observaciones amplía alcance; un aviso fijo «no sumará» contradice reimportación idempotente; pruebas aisladas de cada caso no sustituyen el lote combinado solicitado.
