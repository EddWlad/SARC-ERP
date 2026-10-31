# Data Model: Revisión de energía alta y empates del ranking

Una migración nueva, `V010__revision_energia.sql`, asumiendo que 002 aporta V009. No edita migraciones aplicadas.

## Migración

| Tabla | Cambio |
| --- | --- |
| `energy_session` | Nueva `energy_review VARCHAR(20)`, CHECK `PENDING`, `APPROVED`, `REJECTED` |
| `energy_session` | `ck_energy_session_state` añade `ENERGY_REVIEW` y `ENERGY_REJECTED` |
| `energy_session` | CHECK: `VALIDATED` exige `energy_review` nulo o `APPROVED` |
| `energy_session` | Índice parcial por `energy_review` no nulo |
| `energy_session_revision` | Nueva `energy_review VARCHAR(20)`: condición de esa versión |
| `energy_import_batch` | Nueva `energy_review_rows INTEGER NOT NULL DEFAULT 0` |
| `energy_session_review` | Tabla nueva |

No cambia `ck_energy_session_energy` ni hace `UPDATE`. El código actual rechaza altas superiores al umbral; no se certifica el contenido de una base viva. Validar catálogo y Flyway en el sandbox antes de aplicar V010, sin tocar V001–V009.

### energy_session_review

Solo inserción.

| Columna | Tipo | Regla |
| --- | --- | --- |
| `id` | UUID | PK |
| `session_id` | UUID | FK a `energy_session` |
| `session_revision` | INTEGER | Revisión evaluada |
| `session_version` | BIGINT | Versión de concurrencia evaluada, obligatoria; única por sesión |
| `decision` | VARCHAR(20) | `APPROVED` o `REJECTED` |
| `justification` | VARCHAR(500) | Obligatoria, no vacía |
| `energy_kwh` | NUMERIC(12,3) | Lectura evaluada, obligatoria |
| `decided_by` | UUID | Puede ser nulo como otros campos de actor |
| `decided_at` | TIMESTAMPTZ | Obligatoria |

Índice `(session_id, session_version DESC)` y UNIQUE `(session_id, session_version)`. La versión evaluada determina el orden de decisiones aceptadas aunque coincidan los timestamps; `decided_at` conserva la fecha del hecho. `session_revision` identifica la lectura, no sustituye a la versión de concurrencia. El actor puede ser nulo por compatibilidad de almacenamiento; una decisión humana autenticada toma el actor actual y no permite que el cliente lo elija.

Toda decisión aceptada exige incremento de `EnergySession.version`, aun sin cambio de estado. Sesión, decisión e auditoría se escriben en una transacción; bloqueo optimista con incremento forzado y flush garantiza conflicto y rollback de la operación perdedora. No incrementar `revision` de lectura solo por decidir. La respuesta devuelve la versión actualizada.

## Entidades

### EnergyReviewStatus (nuevo enum en `domain/model/session`)

`PENDING`, `APPROVED`, `REJECTED`. Nulo = sin observación.

### SessionState

Añade `ENERGY_REVIEW` y `ENERGY_REJECTED`. Solo `VALIDATED` suma.

`officialState()`:

```text
sin equipo                       -> PENDING_LINK
energy_review = REJECTED         -> ENERGY_REJECTED
energy_review = PENDING          -> ENERGY_REVIEW
fuente no preferente sin aprobar -> AWAITING_APPROVAL
resto                            -> VALIDATED
```

`TEST` se aplica aparte, como hoy, y al desmarcar se recalcula.

### EnergySession

| Operación | Regla |
| --- | --- |
| `record` | Evalúa la lectura |
| `decideEnergy(decisión, actor, fecha)` | Exige `energy_review` no nulo; devuelve `EnergyReview`; recalcula estado salvo en `TEST` |
| `apply` | Reevalúa según tabla de correcciones |
| `approveSource` | Exige fuente no preferente sin aprobar; recalcula estado |

Correcciones:

| Lectura nueva | `energy_review` |
| --- | --- |
| No supera el umbral o sin energía | nulo |
| Supera y no cambia frente a la energía normalizada actual | Conserva condición actual `PENDING`, `APPROVED` o `REJECTED`, sin otra decisión |
| Supera y cambia frente a la energía normalizada actual | `PENDING`, independientemente de decisiones históricas sobre ese valor |

Comparar antes de mutar la lectura. Conservar decisión no significa omitir validación temporal, vínculo, fuente ni prueba. La versión previa queda en `energy_session_revision` con su condición energética; el historial de decisiones no cambia. Volver a un valor previamente aprobado después de cambiar de lectura no restaura automáticamente su aprobación.

Transiciones:

```text
(nueva sobre umbral)  -> ENERGY_REVIEW
ENERGY_REVIEW  --aprobar--> VALIDATED | AWAITING_APPROVAL
ENERGY_REVIEW  --rechazar-> ENERGY_REJECTED
ENERGY_REJECTED --aprobar--> VALIDATED | AWAITING_APPROVAL
VALIDATED      --rechazar-> ENERGY_REJECTED      (solo si energy_review = APPROVED)
```

### SessionRules

| Valor normalizado | Resultado |
| --- | --- |
| Negativo | `NEGATIVE_ENERGY`, inválida |
| Mayor o igual a 1.000.000.000 | `ENERGY_NOT_STORABLE`, inválida |
| Mayor que 1.000 | Observación `ENERGY_ABOVE_THRESHOLD` |
| Resto | Sin observación |

`requireValid` deja de rechazar por umbral. `ENERGY_OUT_OF_RANGE` desaparece.

### Fila de importación

`ENERGY_ABOVE_THRESHOLD` no invalida y no cambia `requiresApproval`. `ImportRow` conserva la lectura. `ImportBatch` añade
el contador. Las filas ya guardadas con `ENERGY_OUT_OF_RANGE` se muestran como están.

`ParsedSession.isValid()` exige lectura no nula y ningún código bloqueante: únicamente ENERGY_ABOVE_THRESHOLD puede acompañar una lectura válida desde el parser de consumo. Otros códigos continúan invalidando. El clasificador copia el aviso del parser a la fila antes de añadir motivos de clasificación; una lectura ausente no se vuelve válida por tener únicamente avisos. `SessionRules.requireValid` conserva controles reales y no rechaza por este aviso; `RowIssue` compartido no se modifica.

`energy_review_rows INTEGER NOT NULL DEFAULT 0` / `energyReviewRows` cuenta filas de vista previa interpretables con energía normalizada >1.000, no sesiones pendientes de decisión. Incluye idénticas o duplicadas si conservan lectura; excluye inválidas sin lectura. Es una medida transversal a la clasificación: no sumarla a new/modified/etc. Etiqueta «Lecturas superiores a 1.000 kWh». El valor se fija con la vista previa; decisiones posteriores no reescriben el lote. Sesiones aprobadas/rechazadas idénticas conservan su decisión aunque se cuenten como lectura alta.

### Ranking

`RankedEquipment` añade `position`:

- Orden: energía descendente; nombre del equipo; identificador solo para nombres iguales.
- Posición: igual energía comparte; el siguiente toma su índice más uno (1, 2, 3, 4, 4, 4, 7).
- Top: `position <= 5`.
- Más de cinco por empate si `top.size() > 5`.

`EquipmentEnergy` no cambia. `byEquipment` deja de recibir límite.

### Indicadores

`ConsumptionSummary` añade `energyReview`, `energyRejected` y `approvedAboveThreshold`, todos `EnergyTotals`.
`SessionCriteria` añade `energyReview`, usado en listado y en el total de aprobadas.
`PendingLinkResult` añade `awaitingEnergyReview` para sesiones vinculadas que siguen sin sumar por energía.

Los totales `energyReview` y `energyRejected` filtran por estado principal, no solo por `energy_review`. TEST y PENDING_LINK prevalecen y no se cuentan otra vez. `approvedAboveThreshold` filtra VALIDATED más condición APPROVED y es un subconjunto informativo del total oficial. El filtro de histórico por `energyReview` es independiente del estado principal.

`SessionResponse` incorpora `canApproveSource`: verdadero para equipo vinculado con fuente no preferente y aprobación de fuente aún ausente, aun si el estado principal es revisión energética o TEST. Es capacidad del registro, no permiso de usuario; backend mantiene RBAC y Angular la combina con permiso de edición.
