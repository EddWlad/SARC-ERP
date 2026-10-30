# Research: Vinculación Enerlink y sesiones incompletas

Evidencia obtenida por lectura de código y consultas de solo lectura sobre la base dev
(Flyway V008) el 2026-10-30. No se ejecutaron pruebas ni se modificaron datos. Las cifras
describen ese entorno, no producción.

## R1. Por qué «Identidades externas» aparece vacía

**Evidencia**

- Hay 226 equipos y 4 identidades Enerlink, todas verificadas.
- El lote completado `681664d7` trae 99 filas con par completo: 4 `NEW` con resultado `CREATED`
  y 95 `MODIFIED` con `SKIPPED`, `approved = false`, detalle `Sin aprobar`.
- Al menos 93 de esas 95 proponen solo «nueva identidad». Las otras dos no coinciden con el
  patrón de texto usado y deben revisarse al reimportar.
- Los lotes anteriores no traían columnas Enerlink informadas (0 filas con par).

**Causa raíz**: la lectura, interpretación y persistencia funcionan. Los equipos ya existían, así
que `EquipmentRowEvaluator` marca «nueva identidad» como `MODIFIED`; `EquipmentImportRow`
exige aprobación fila por fila y `markUnapprovedAsSkipped` omite el resto al confirmar. La pantalla
solo ofrece «Aprobar los visibles», limitado a la página actual. No es un fallo de consulta en
Angular ni de base de datos.

**Decision**: nueva clasificación `IDENTITY_COMPLETED` para filas cuyo único cambio es registrar o
verificar identidades. Se aplica al confirmar, sin aprobación por fila y sin escribir el equipo.

**Rationale**: FR-001 define que la confirmación autorizada verifica la identidad; US1.2 exige
conservar el resto. Una fila con otros cambios sigue siendo `MODIFIED`.

**Alternatives considered**: aprobar en masa todas las páginas mantiene la omisión silenciosa si se
olvida el paso. Aprobar automáticamente todo `MODIFIED` escribiría cambios no revisados.

## R2. Por qué 1.583 sesiones quedaron pendientes

**Evidencia**

- Lote `eba003e1` (estado `READY`): 1.724 filas, IDs únicos; 58 `NEW`, 1.583 `PENDING_LINK`, 83
  `INVALID`, todas las pendientes con `EQUIPMENT_NOT_IDENTIFIED`.
- Las 1.641 filas válidas usan 45 pares. Los 45 coinciden, tras normalizar D05, con un único par
  de la plantilla. Las 58 `NEW` corresponden a la única identidad usada entre las 4 registradas.
- Todos los equipos coincidentes son JAC (41 puntos de carga con 1.099 filas y 4 electrolineras
  con 542), por lo que Enerlink es su fuente preferente y no requieren aprobación de fuente.

**Causa raíz**: consecuencia directa de R1. La resolución por `ExternalKey.comparable()` sí
funciona; faltan los datos.

**Decision**: no cambiar el algoritmo de normalización. Corregir R1 y reimportar.

## R3. Por qué 83 sesiones fueron inválidas

**Evidencia**: las 83 tienen `NUMBER_REQUIRED` («Energia: Falta el valor numerico»); 7 también
`DATE_REQUIRED` («Termino: Falta la fecha»). Ninguna tiene `SESSION_ID_REQUIRED`, error de inicio ni
`EQUIPMENT_REFERENCE_REQUIRED`. Por tanto traen ID, INICIO legible y CARGADOR. Ninguna combina
energía conocida con término vacío.

**Limitación**: `ImportRow.invalid` no guarda ACTIVO, CARGADOR ni valores. No se puede afirmar a qué
equipos pertenecen hasta reimportar; el original está en Cloudinary, no en el repositorio.

**Causa raíz en varias capas**

| Capa | Bloqueo |
| --- | --- |
| Parser | `TabularSessionParser.read` convierte vacío en error mediante `DecimalText` y `DateTimeText` |
| Dominio | `SessionRules.check` y `requireValid` exigen ambos valores; `SessionReading.sameAs` falla con energía nula |
| Esquema | `ended_at` y `energy_kwh` son `NOT NULL` en `energy_session` y `energy_session_revision` |
| Agregados | La suma `coalesce(..., 0)` convierte ausencia total en 0 |
| Informes | `List.of` no admite nulos y `dateTime.format(null)` falla en Excel |
| Angular | `quantities.kwh` recibe números no nulos; los tipos generados lo exigen |

**Decision**: tolerancia declarada por fuente mediante un método del parser; solo Enerlink admite
vacíos (FR-019). El dominio admite nulos y el parser de Wallbox sigue exigiéndolos.

**Alternatives considered**: `SessionRules` por fuente acoplaría la regla al dominio común.
Valores centinela contradicen FR-009 y FR-010.

## R4. Brechas de identidad contra FR-003 y FR-004

| Hallazgo | Ubicación | Consecuencia |
| --- | --- | --- |
| Solo CARGADOR crea identidad con ámbito vacío | `EquipmentLineInterpreter.identities` | Viola par completo |
| Conflicto por clave exacta | `EquipmentRowEvaluator`, `ExternalIdentityService.register` | Pares equivalentes D05 pasan |
| Repetición en lote sin quitar tildes | `firstRepeatedKey` | Dos filas equivalentes pasan |
| Par verificado distinto en el mismo equipo no se bloquea | `EquipmentRowEvaluator` | Viola Clarificación 1 |
| Último gana ante duplicados normalizados | `EquipmentDirectoryService.findByVerifiedIdentities` | Atribución arbitraria, viola FR-006 |
| Sesión con solo CARGADOR busca ámbito vacío | `EnerlinkSessionParser` | Vinculación por un componente |
| Sin CARGADOR es inválida | `TabularSessionParser` | Viola Clarificación 3 |
| Mismo cargador con `equalsIgnoreCase` | `EnergySession.identifiesSameCharger` | Una tilde genera falso cambio de equipo |

**Decision**: reutilizar `ExternalKey.comparable()` como única forma de comparación Enerlink, sin
columna normalizada ni índice único nuevo.

**Rationale**: PostgreSQL no replica la normalización sin `unaccent`; duplicarla en SQL crea dos
reglas. El volumen es de cientos de identidades y `EquipmentDirectoryService` ya usa este enfoque.
La ambigüedad debe seguir siendo un estado detectable (US2.3).

**Alternatives considered**: índice único funcional con `unaccent` añade extensión y bloquearía
migrar datos duplicados.

## R5. Motivo específico de pendiente

**Decision**: guardar `link_issue` en `energy_session`, con valores `IDENTITY_INCOMPLETE`,
`IDENTITY_NOT_REGISTERED`, `IDENTITY_NOT_VERIFIED` e `IDENTITY_AMBIGUOUS`, nulo fuera de pendientes
Enerlink. Se recalcula al importar y al ejecutar vinculación.

**Rationale**: `energy_import_row.issues` pertenece a la fila y al lote, y el histórico consulta la
sesión. Derivar el motivo al leer añadiría consultas al inventario en cada página.

**Alternatives considered**: calcularlo al leer haría el histórico más lento y acoplado.

## R6. Reimportación de sesiones pendientes (Clarificación 5)

**Decision**

- Sesión pendiente, lectura idéntica y par que ahora resuelve: `LINKABLE`, se vincula al confirmar.
- Sesión pendiente con lectura distinta: `MODIFIED`, requiere aprobación, conserva revisión y vincula
  si el par resuelve.
- `EQUIPMENT_CHANGED` queda solo para sesiones con equipo. Sin equipo no hay reasignación que proteger.

**Rationale**: reutiliza la regla de aprobación y no añade columnas para decidirla. Evita que un par
antes vacío se trate como cambio de cargador.

## R7. Revalidación al confirmar

**Hallazgo**: `ImportProcessor.applyRow` usa el equipo guardado en la vista previa.

**Decision**: al aplicar cada tramo se resuelven de nuevo los pares. Si el equipo revisado ya no es la
coincidencia única, se guarda pendiente con su motivo y el detalle lo explica. Si una fila pendiente
ahora resuelve, se vincula.

## R8. Trazabilidad de vinculaciones

**Hallazgo**: `linkPending` audita solo un agregado, sin sesión ni equipo.

**Decision**: registrar `ENERGY_SESSION_LINKED` por sesión con equipo, par y origen (`PENDING_LINK_OPERATION`
o lote), y mantener el agregado. No requiere esquema.

## R9. Indicadores con energía ausente

**Decision**

- `EnergyTotals` añade `sessionsWithoutEnergy`. `energyKwh` es nulo si hay sesiones y ninguna lectura;
  sigue en 0 si no hay sesiones.
- `averageKwhPerSession` divide energía conocida entre sesiones con lectura; nulo sin lecturas.
- El ranking excluye equipos sin lectura y `ConsumptionSummary` informa `equipmentWithoutEnergy`.
- Los días sin sesiones siguen en 0; un día solo con sesiones sin energía es «Sin datos».
- Porcentajes y gráficos usan energía conocida. Un nulo se dibuja como 0 solo en la geometría, nunca
  en el texto.

**Rationale**: `ConsumptionCalculator` ya es la fuente única de tablero, PDF y Excel, lo que da FR-013
sin duplicar reglas.

**Alternatives considered**: dividir entre todas las sesiones trataría la ausencia como cero.

## R10. Stack real frente a la descripción de la solicitud

**Evidencia**: `pom.xml` no contiene MapStruct ni Spring Batch. Los DTO usan records con `from(...)` y
los trabajos usan `ImportJobs` en segundo plano por tramos (ADR 0005 y 0007).

**Decision**: conservar esos patrones. No se añade ninguna dependencia.

## R11. Lotes existentes

**Decision**: la migración no reescribe lotes ni sesiones. El lote `READY` del incidente debe
descartarse y volver a cargarse, ya que sus 83 inválidas no guardaron lectura y no pueden recuperarse.
Los lotes completados con `SKIPPED` se conservan como historia.
