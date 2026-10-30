# Data Model: Vinculación Enerlink y sesiones incompletas

Cambios sobre el esquema V008. Una sola migración nueva: `V009__vinculacion_enerlink_sesiones.sql`.
V001–V008 no se editan.

## Migración V009

| Tabla | Cambio | Motivo |
| --- | --- | --- |
| `energy_session` | `ended_at` y `energy_kwh` sin `NOT NULL` | FR-008 a FR-010 |
| `energy_session` | Nueva `link_issue VARCHAR(30)` con CHECK de valores | FR-006 |
| `energy_session` | CHECK: `link_issue` nulo si `equipment_id` no es nulo | Coherencia |
| `energy_session_revision` | `ended_at` y `energy_kwh` sin `NOT NULL` | FR-016 |
| `energy_import_row` | CHECK de clasificación añade `LINKABLE` | Clarificación 5 |
| `energy_import_row` | CHECK de resultado añade `LINKED` | Clarificación 5 |
| `energy_import_batch` | Nueva `linkable_rows INTEGER NOT NULL DEFAULT 0` | Vista previa |
| `asset_import_row` | CHECK de clasificación añade `IDENTITY_COMPLETED` | FR-001 |
| `asset_import_row` | CHECK `ck_asset_import_row_existing` incluye `IDENTITY_COMPLETED` | Fila apunta a equipo |
| `asset_import_row` | CHECK de resultado añade `IDENTITY_ADDED` | Distingue de actualización |
| `asset_import_batch` | Nueva `identity_completed_rows INTEGER NOT NULL DEFAULT 0` | Vista previa |

No cambian: `ck_energy_session_span` y `ck_energy_session_energy` (un CHECK con NULL se cumple),
`uq_energy_session_source`, `uq_asset_external_identity`, `report_issued`. `start_date` y `started_at`
siguen obligatorios. La migración no hace `UPDATE` sobre sesiones, lotes ni identidades.

**Compatibilidad**: filas antiguas siguen válidas. Sesiones pendientes previas quedan con `link_issue`
nulo hasta la siguiente vinculación. Es aditiva, pero con energía nula guardada no admite volver a
`NOT NULL` sin decidir qué hacer con esas filas; un rollback funcional requiere exportarlas antes.

## Entidades

### ExternalIdentity (sin cambio de columnas)

- Enerlink exige ámbito (ACTIVO) e identificador (CARGADOR) no vacíos.
- Originales conservados; comparación por `ExternalKey.comparable()`.
- `verify` sigue siendo de una sola vez; repetir el par es no-op.

Reglas de fila de plantilla, en orden:

| Situación | Resultado | Código |
| --- | --- | --- |
| Ambos vacíos | Sin efecto sobre identidades | — |
| Solo ACTIVO | `INVALID` | `ENERLINK_CHARGER_REQUIRED` (existente) |
| Solo CARGADOR | `INVALID` | `ENERLINK_SCOPE_REQUIRED` (nuevo) |
| Par equivalente en otro equipo | `CONFLICT` | `EXTERNAL_IDENTITY_IN_USE` (existente) |
| Equipo con identidad Enerlink verificada y par distinto | `CONFLICT` | `ENERLINK_IDENTITY_DIFFERS` (nuevo) |
| Mismo par ya verificado en el equipo | Sin efecto | — |
| Mismo par sin verificar en el equipo | Verifica | — |
| Par nuevo | Registra verificada | — |

Una fila `INVALID` o `CONFLICT` no escribe nada; las demás continúan (Clarificación 2).

### EquipmentRowClassification

Añade `IDENTITY_COMPLETED`: equipo existente cuyo único cambio es registrar o verificar identidades
Enerlink. Una identidad de otra plataforma sigue exigiendo aprobación (FR-019).
`writesEquipment()` lo incluye, `requiresApproval()` no. Al aplicar no llama a `update`, no cambia
`version` del equipo ni registra `EQUIPMENT_UPDATED`.

Una fila con identidad y otros cambios sigue `MODIFIED`: se aprueba completa y, sin aprobación, no aplica
ninguno de sus cambios ni registra la identidad (FR-001).

`EquipmentRowCounts` añade `identityCompleted`. `EquipmentRowOutcome` añade `IDENTITY_ADDED`, contado
en `countWritten`.

### EnergySession

| Campo | Regla |
| --- | --- |
| `startedAt`, `startDate` | Obligatorios; base del conteo |
| `endedAt` | Nulo = no informado; nunca se sustituye |
| `energyKwh` | Nulo = no informado; 0 es lectura conocida; HALF_UP a 3 decimales |
| `linkIssue` | Solo en sesión Enerlink sin equipo |

`SessionState` no cambia. Transiciones añadidas:

```text
PENDING_LINK --link(equipo, aprobada)--> VALIDATED | AWAITING_APPROVAL   (linkIssue = null)
PENDING_LINK --recalcular motivo-------> PENDING_LINK                    (linkIssue actualizado)
```

`markUnlinkable(LinkIssue)` guarda el motivo tras `record` o `reassign` si la sesión queda sin equipo, y
vincular lo limpia. `correct` deja de exigir mismo cargador si la sesión no tiene equipo.
`identifiesSameCharger` compara con `ExternalKey.comparable()` en Enerlink y conserva la regla actual
en Wallbox.

### SessionReading y SessionRules

- `sameAs` compara energía nula sin fallar: nulo solo es igual a nulo.
- `check`: `END_BEFORE_START` solo con término; `NEGATIVE_ENERGY` y `ENERGY_OUT_OF_RANGE` solo con energía.
- `requireValid`: solo exige inicio.

### RowClassification

Añade `LINKABLE`: sesión existente sin equipo, misma lectura, par que ahora resuelve a un único
equipo. `writesSession()` lo incluye. Requiere aprobación solo si la fuente no es preferente.

Orden de clasificación para sesión existente:

| Sesión | Lectura | Par resuelve | Clasificación |
| --- | --- | --- | --- |
| Con equipo, otro cargador | — | — | `EQUIPMENT_CHANGED` |
| Con equipo, mismo cargador | Igual | — | `IDENTICAL` |
| Con equipo, mismo cargador | Distinta | — | `MODIFIED` |
| Sin equipo | Igual | Sí | `LINKABLE` |
| Sin equipo | Igual | No | `IDENTICAL` |
| Sin equipo | Distinta | Sí o no | `MODIFIED`; si resuelve, vincula al aplicar |

`RowCounts` añade `linkable` para `LINKABLE`; el campo actual `pendingLink` no cambia.
`RowOutcome` añade `LINKED`, contado en `countWritten`.

Quitar un valor conocido añade `SESSION_VALUE_REMOVED` a la fila `MODIFIED`, nombrando el campo.

### Resolución de identidades

`shared/catalog` añade:

```text
IdentityMatch(status, equipment)
status: UNIQUE | AMBIGUOUS | NOT_VERIFIED | NOT_REGISTERED
EquipmentDirectory.matchIdentities(platform, keys) -> Map<ExternalKey, IdentityMatch>
```

Regla: se agrupan todas las identidades de la plataforma por clave comparable. Una clave verificada en
un equipo es `UNIQUE`; en dos o más, `AMBIGUOUS`; existente sin verificar, `NOT_VERIFIED`; sin
registro, `NOT_REGISTERED`. `findByVerifiedIdentities` queda para Wallbox sin cambios (FR-019).

`ExternalKey` añade `isComplete()`. Una clave incompleta da `IDENTITY_INCOMPLETE` sin consultar.

### Indicadores

| Tipo | Campos nuevos o cambios |
| --- | --- |
| `EnergyTotals` | `sessionsWithoutEnergy`; `energyKwh` nulo si hay sesiones sin lecturas |
| `DailyEnergy` | `sessionsWithoutEnergy`; `energyKwh` nulo igual criterio |
| `ConsumptionSummary` | `averageKwhPerSession` nulo sin lecturas; `equipmentWithoutEnergy` |
| `RankedEquipment` | `energyKwh` siempre con valor; `sessions` cuenta todas las del equipo |

Consultas: `sum(energyKwh)` sin `coalesce`, `count(energyKwh)` para lecturas y `having count(energyKwh) > 0`
en `byEquipment`.
