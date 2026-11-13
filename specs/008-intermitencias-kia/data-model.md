# Data Model: Intermitencias de puntos de carga KIA

Todas las tablas son nuevas y se crean en `V018__intermitencias_kia.sql` con el prefijo `itm_`. Las columnas de auditoría `created_at`, `updated_at`, `created_by`, `updated_by` y `version` provienen de `BaseAuditableEntity`, igual que en `upt_*`. Los instantes son `TIMESTAMPTZ` y los días son `DATE` en America/Guayaquil.

## Entidades

### itm_window: ventana de intermitencia (cabecera vigente)

| Campo | Tipo | Regla | FR |
|-------|------|-------|----|
| id | UUID PK | | |
| equipment_id | UUID NOT NULL | Identidad de Inventario. No es FK física entre módulos; se valida con `KiaPointDirectory`, igual que en `upt_*` | FR-001, FR-002 |
| point_label | VARCHAR(200) NOT NULL | Nombre y código del punto en la fecha del hecho, conservados (D12) | US3-6 |
| start_at | TIMESTAMPTZ NOT NULL | En minuto exacto (CHECK de segundos = 0) | FR-008, FR-009 |
| end_at | TIMESTAMPTZ NULL | NULL solo si state = OPEN o ANNULLED (anulada estando abierta); si existe, es mayor que start_at | FR-012 |
| duration_minutes | INTEGER NULL | Igual a (end_at − start_at)/60; NULL si OPEN; CHECK ≥ 1 | FR-009 |
| state | VARCHAR(20) NOT NULL | OPEN, REVIEW, CONFIRMED, TEST o ANNULLED | FR-011 |
| review_reasons | JSONB NOT NULL DEFAULT '[]' | Hoy solo `LONG_WINDOW`. `TOTAL_MISMATCH` es una observación de la fila de importación (se resuelve con `acknowledgeTotal`) y nunca una razón de la ventana | FR-021a, FR-027 |
| comment | VARCHAR(500) NULL | | FR-008 |
| source | VARCHAR(10) NOT NULL | MANUAL, XLSX, CSV o PASTE | FR-008 |
| batch_id, row_number, pair_number | UUID, INTEGER, INTEGER NULL | Procedencia; NULL en la captura manual | FR-008, FR-030 |
| current_revision | INTEGER NOT NULL | Apunta a itm_window_revision | FR-017 |
| version | BIGINT | Bloqueo optimista | FR-018 |

**Invariantes**

- `EXCLUDE USING gist (equipment_id WITH =, tstzrange(start_at, coalesce(end_at, 'infinity'), '[)') WITH &&) WHERE (state <> 'ANNULLED')` (FR-014). Aplica a todo estado no anulado, incluida TEST. Una ventana OPEN ocupa `[start_at, infinito)`: mientras esté abierta, ninguna ventana posterior del mismo punto puede guardarse, como describe FR-014.
- `UNIQUE (equipment_id, start_at, end_at) WHERE state <> 'ANNULLED'` (FR-015).
- Una ventana OPEN no tiene `end_at`; solo una OPEN o ANNULLED puede no tenerlo, porque una ventana abierta que se anula conserva `end_at` NULL (`ck_itm_window_open`).
- No hay `DELETE`: las ventanas se anulan (FR-017).

**Índices**: `(equipment_id, start_at)`, `(start_at)`, `(end_at)` para las consultas de intersección con un rango, y `(state)`.

### itm_window_revision: historial inmutable

| Campo | Regla |
|-------|-------|
| id UUID PK, window_id UUID NOT NULL, revision INTEGER NOT NULL | `UNIQUE (window_id, revision)` |
| start_at, end_at, duration_minutes, state, comment, review_reasons | Valores de esa revisión (`updatable = false`) |
| change | CREATED, CORRECTED, CLOSED, REVIEW_CONFIRMED, MARKED_TEST, UNMARKED_TEST, ANNULLED o REPLACED_BY_IMPORT |
| justification VARCHAR(500) | Obligatoria en todo cambio, salvo CREATED y CLOSED: cerrar una ventana abierta es el flujo normal de captura y no corrige nada (FR-017, FR-021a) |
| actor_id UUID NOT NULL, at TIMESTAMPTZ NOT NULL | |

### itm_import_batch: lote (extiende BaseImportBatch)

Hereda `origin` (FILE o PASTE), `file_name`, `checksum`, `original_ref`, `status`, `failure_*`, `applied_rows` y `confirmed_*`. Agrega:

| Campo | Regla |
|-------|-------|
| format | XLSX, CSV o PASTE |
| total_rows, total_windows | Conteos de la interpretación |
| new_count, identical_count, conflict_count, invalid_count, pending_link_count, out_of_scope_count, review_count, skipped_count | Conteos por clasificación (FR-025), que se recalculan con cada edición |
| earliest_day, latest_day DATE | Rango de días tocados; sirve para avisar al marcar un día (FR-045) |

El archivo original se guarda en Cloudinary, en `imports/intermittency`, con su huella SHA-256 (FR-030).

### itm_import_row: fila de lote con vista previa editable

| Campo | Regla |
|-------|-------|
| id, batch_id, row_number | `UNIQUE (batch_id, row_number)` |
| raw JSONB | Valores originales por columna, tal como se leyeron; no se modifican (FR-026) |
| edits JSONB | Lista de `{campo/ventana, valorAnterior, valorNuevo, actor, at}` (FR-026) |
| interpretation JSONB | Ventanas interpretadas `[{pair, startAt, endAt, minutes, classification, issues[], decision}]`, `fileTotalMinutes`, `computedTotalMinutes` y `totalAcknowledged` |
| equipment_id UUID NULL | Punto resuelto: por código o por elección explícita (`linked_by`, `linked_at`) |
| classification | Peor clasificación de sus ventanas: INVALID > PENDING_LINK > OUT_OF_SCOPE > CONFLICT > REVIEW > IDENTICAL > NEW |
| decision | APPLY, SKIP o KEEP_PENDING; con SKIP es obligatorio `skip_reason` |
| resolved_by, resolved_at | NULL salvo cuando una fila KEEP_PENDING de un lote COMPLETED se resuelve después (vincular y aplicar, u omitir); la resolución queda también en `edits` y en la auditoría (FR-028) |
| touched_days DATE[] | Días tocados por sus ventanas interpretadas o, si no hay ninguna, la fecha de la fila (FR-045) |
| version | Bloqueo optimista para las ediciones simultáneas |

**Clasificaciones de una ventana interpretada**

| Clasificación | Cuándo | Código del motivo |
|---------------|--------|-------------------|
| NEW | Válida, sin coincidencia con ninguna ventana guardada | — |
| IDENTICAL | Mismo punto, inicio y fin que una ventana no anulada. Es la «Duplicada» de FR-025, rotulada «Duplicada (ya registrada)»; la idéntica dentro del mismo lote es `DUPLICATE_IN_BATCH`, rotulada «Duplicada en el lote» | `ALREADY_RECORDED` |
| CONFLICT | Se solapa con una ventana guardada, o con otra del mismo lote | `OVERLAPS_EXISTING`, `OVERLAP_IN_BATCH`, `DUPLICATE_IN_BATCH` |
| INVALID | Error de dato | `DATE_FORMAT`, `TIME_FORMAT`, `SECONDS_AMBIGUOUS`, `END_BEFORE_START`, `ZERO_DURATION`, `INCOMPLETE_PAIR`, `FUTURE_INSTANT` |
| PENDING_LINK | Punto desconocido | `UNKNOWN_CODE`, `AMBIGUOUS_CODE`, `DELETED_EQUIPMENT`, `MISSING_CODE` |
| OUT_OF_SCOPE | Equipo que no es un punto de carga KIA | `NOT_KIA_CHARGING_POINT` |
| REVIEW | Válida, pero con una observación | `TOTAL_MISMATCH`, `LONG_WINDOW` |

Las observaciones informativas que no bloquean son `NAME_DIFFERS`, `INACTIVE_EQUIPMENT` y `TOTAL_MISSING`.

Un par incompleto (`INCOMPLETE_PAIR`) admite la decisión `OPEN`: crear la ventana abierta con su inicio, solo si ese inicio no está en el futuro (FR-021b) y la ventana no está marcada como prueba. Si lo está, la decisión se rechaza con `OPEN_TEST_NOT_ALLOWED` (FR-016). La ventana abierta se clasifica como `[inicio, infinito)` frente a las demás del mismo punto: las posteriores del lote quedan `OVERLAP_IN_BATCH` y las guardadas, `OVERLAPS_EXISTING` (FR-014, contracts/import-format.md, regla 4).

### itm_reviewed_day: día revisado

| Campo | Regla |
|-------|-------|
| day DATE PK | Un registro por día; la red es implícita (KIA) |
| active BOOLEAN NOT NULL | `true` mientras la marca está vigente |
| marked_by, marked_at | Se actualizan con cada nueva marca |
| version | Bloqueo optimista |

**Invariante de dominio**: solo se marca un día ya terminado, es decir, `day < hoy` local (FR-043, FR-044).

### itm_reviewed_day_event: historial de marcas

`id`, `day`, `action` (MARKED o WITHDRAWN), `justification` (obligatoria en WITHDRAWN), `actor_id`, `at` y `pending_batches_warned` (cantidad de lotes en vista previa que tocaban ese día cuando se marcó).

### Entidades derivadas (no se guardan)

- **Porción de período**: `(window, día o rango, minutos)`. La calcula `PeriodSlicer` (FR-013). En la serie diaria, una ventana cuenta en cada día con porción mayor que 0 (FR-035).
- **Resumen oficial** e indicadores no oficiales (FR-033 a FR-036).
- **Disponibilidad estimada por punto y agregada** (FR-042 a FR-046a).
- **Informe emitido**: se guarda en `report_issued` con `kind = INTERMITTENCY` (FR-040).

## Transiciones de estado de una ventana

| Desde | Acción | Hacia | Condición | Rol |
|-------|--------|-------|-----------|-----|
| — | Captura o confirmación, cerrada, menos de 240 min | CONFIRMED | Válida, sin conflicto y sin instante futuro | Administrador o Analista |
| — | Captura o confirmación, cerrada, 240 min o más | REVIEW | Igual que la anterior; razón `LONG_WINDOW` | Administrador o Analista |
| — | Captura o confirmación sin fin | OPEN | Inicio no futuro | Administrador o Analista |
| — | Confirmación de lote de una ventana marcada como prueba en la vista previa | TEST | Cerrada, válida, sin conflicto y sin instante futuro; se guarda `review_reasons` (por ejemplo `LONG_WINDOW`) para cuando se quite la marca. Una ventana sin fin con la marca de prueba y la decisión OPEN se rechaza con `OPEN_TEST_NOT_ALLOWED` (FR-016, FR-026) | Administrador o Analista |
| OPEN | Cerrar | CONFIRMED o REVIEW | El fin es mayor que el inicio, no está en el futuro y no hay solapamiento; queda en REVIEW si dura 240 min o más | Administrador o Analista |
| REVIEW | Confirmar con justificación | CONFIRMED | Lo puede confirmar el mismo autor | Administrador o Analista |
| CONFIRMED o REVIEW | Corregir con justificación | CONFIRMED o REVIEW | Se vuelven a aplicar las validaciones; puede volver a REVIEW | Administrador o Analista |
| CONFIRMED o REVIEW | Marcar como prueba, con justificación | TEST | Desde REVIEW se conservan sus `review_reasons` para cuando se quite la marca; no hace falta confirmarla antes, así nunca computa en lo oficial | Administrador o Analista |
| TEST | Quitar la marca de prueba, con justificación | CONFIRMED o REVIEW | Se vuelven a aplicar las validaciones | Administrador o Analista |
| Cualquiera menos ANNULLED | Anular con justificación | ANNULLED | | Administrador o Analista |

Cada transición crea una `itm_window_revision` y un evento de `AuditTrail.record` (FR-052).

Marcar un **punto** como prueba (FR-016) es una operación en lote sobre un rango de fechas: marca como TEST todas sus ventanas CONFIRMED o REVIEW que intersecan el rango, con una justificación única, y crea una revisión MARKED_TEST por ventana. Desmarcarlo sobre el mismo rango aplica UNMARKED_TEST a sus ventanas TEST, que vuelven a CONFIRMED o REVIEW. No se guarda ninguna marca permanente del punto: las ventanas registradas después se tratan normalmente. Las ventanas OPEN del rango no se marcan, porque una ventana TEST siempre está cerrada; al cerrarlas, el editor las marca como prueba si corresponde. La respuesta informa cuántas ventanas OPEN del rango quedaron sin marcar. Inventario no se modifica.

«Pendiente de vincular» no es un estado de `itm_window`: es la clasificación PENDING_LINK de una `itm_import_row` cuyo punto no se conoce (FR-011).

## Consultas principales

- **Ventanas del rango**: `start_at < :to AND coalesce(end_at, :cutOff) > :from AND equipment_id IN (:ids)`.
- **Filas que bloquean días** (lote en COMPLETED, decisión KEEP_PENDING y `touched_days && :días`):
  - clasificación PENDING_LINK: bloquea el día para **todos** los puntos (FR-045);
  - clasificación CONFLICT: bloquea el día solo para su `equipment_id` (punto-día en conflicto, FR-045).
- **Filas pendientes por resolver**: lote en COMPLETED y decisión KEEP_PENDING, sin `resolved_at`.
- **Días revisados**: `itm_reviewed_day WHERE active AND day BETWEEN …`.

## Volumen

Se esperan unas 6.000 ventanas al año, unos 1.000 lotes al año y 365 días revisados al año. Las agregaciones se calculan en memoria (R8 y R9), con un límite de 366 días por consulta.
