-- Feature 008: intermitencias de puntos de carga KIA. Solo tablas nuevas (prefijo itm_); no altera ni carga datos
-- de las existentes. La regla de no solapamiento vive en el dominio (WindowWriteGuard) y aqui como ultima
-- barrera: la restriccion de exclusion necesita btree_gist para comparar el equipo con igualdad.
--
-- Hasta que T006 compruebe esta migracion contra una copia de produccion, solo se aplica en bases desechables.
-- Si ya se aplico en una base persistente, cualquier cambio va en V019: una migracion aplicada no se reescribe.

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Lotes de importacion (extienden BaseImportBatch, como upt_import_batch).
CREATE TABLE itm_import_batch (
    id                UUID         PRIMARY KEY,
    origin            VARCHAR(10)  NOT NULL,
    file_name         VARCHAR(255),
    checksum          VARCHAR(64)  NOT NULL,
    original_ref      VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    failure_code      VARCHAR(60),
    failure_message   VARCHAR(500),
    applied_rows      INTEGER      NOT NULL DEFAULT 0,
    confirmed_at      TIMESTAMPTZ,
    confirmed_by      UUID,
    format            VARCHAR(10)  NOT NULL,
    total_rows        INTEGER      NOT NULL DEFAULT 0,
    total_windows     INTEGER      NOT NULL DEFAULT 0,
    new_count         INTEGER      NOT NULL DEFAULT 0,
    identical_count   INTEGER      NOT NULL DEFAULT 0,
    conflict_count    INTEGER      NOT NULL DEFAULT 0,
    invalid_count     INTEGER      NOT NULL DEFAULT 0,
    pending_link_count INTEGER     NOT NULL DEFAULT 0,
    out_of_scope_count INTEGER     NOT NULL DEFAULT 0,
    review_count      INTEGER      NOT NULL DEFAULT 0,
    skipped_count     INTEGER      NOT NULL DEFAULT 0,
    earliest_day      DATE,
    latest_day        DATE,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL,
    CONSTRAINT ck_itm_import_batch_origin CHECK (origin IN ('FILE', 'PASTE')),
    CONSTRAINT ck_itm_import_batch_format CHECK (format IN ('XLSX', 'CSV', 'PASTE')),
    CONSTRAINT ck_itm_import_batch_status CHECK (
        status IN ('PARSING', 'READY', 'CONFIRMING', 'COMPLETED', 'FAILED', 'DISCARDED')),
    CONSTRAINT ck_itm_import_batch_days CHECK (
        earliest_day IS NULL OR latest_day IS NULL OR earliest_day <= latest_day)
);

CREATE INDEX ix_itm_import_batch_created ON itm_import_batch (created_at DESC);

-- Ventana de intermitencia: cabecera vigente. equipment_id no es una clave foranea entre modulos: se valida con
-- KiaPointDirectory y el equipo nunca se borra fisicamente.
CREATE TABLE itm_window (
    id               UUID         PRIMARY KEY,
    equipment_id     UUID         NOT NULL,
    point_label      VARCHAR(200) NOT NULL,
    start_at         TIMESTAMPTZ  NOT NULL,
    end_at           TIMESTAMPTZ,
    duration_minutes INTEGER,
    state            VARCHAR(20)  NOT NULL,
    review_reasons   JSONB        NOT NULL DEFAULT '[]',
    comment          VARCHAR(500),
    source           VARCHAR(10)  NOT NULL,
    batch_id         UUID         REFERENCES itm_import_batch (id),
    row_number       INTEGER,
    pair_number      INTEGER,
    current_revision INTEGER      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_by       UUID,
    updated_by       UUID,
    version          BIGINT       NOT NULL,
    CONSTRAINT ck_itm_window_state CHECK (state IN ('OPEN', 'REVIEW', 'CONFIRMED', 'TEST', 'ANNULLED')),
    CONSTRAINT ck_itm_window_source CHECK (source IN ('MANUAL', 'XLSX', 'CSV', 'PASTE')),
    CONSTRAINT ck_itm_window_start_minute CHECK (date_part('second', start_at) = 0),
    CONSTRAINT ck_itm_window_end_minute CHECK (end_at IS NULL OR date_part('second', end_at) = 0),
    -- Abierta si y solo si no tiene fin; una ventana anulada puede quedar sin fin porque se anulo estando abierta.
    CONSTRAINT ck_itm_window_open CHECK (
        (state = 'OPEN' AND end_at IS NULL) OR (state = 'ANNULLED') OR (state <> 'OPEN' AND end_at IS NOT NULL)),
    CONSTRAINT ck_itm_window_duration CHECK (
        (end_at IS NULL AND duration_minutes IS NULL)
        OR (end_at IS NOT NULL AND duration_minutes >= 1
            AND duration_minutes::numeric * 60 = extract(epoch FROM (end_at - start_at)))),
    CONSTRAINT ck_itm_window_reasons CHECK (jsonb_typeof(review_reasons) = 'array'),
    CONSTRAINT ck_itm_window_revision CHECK (current_revision >= 1),
    CONSTRAINT ck_itm_window_provenance CHECK (
        (batch_id IS NULL AND row_number IS NULL AND pair_number IS NULL)
        OR (batch_id IS NOT NULL AND row_number IS NOT NULL AND pair_number IS NOT NULL
            AND row_number >= 1 AND pair_number >= 1)),
    -- Ninguna ventana no anulada de un punto se solapa con otra. Una abierta ocupa [inicio, infinito).
    CONSTRAINT ex_itm_window_overlap EXCLUDE USING gist (
        equipment_id WITH =,
        tstzrange(start_at, coalesce(end_at, 'infinity'::timestamptz), '[)') WITH &&
    ) WHERE (state <> 'ANNULLED')
);

CREATE UNIQUE INDEX uq_itm_window_identity ON itm_window (equipment_id, start_at, end_at) WHERE state <> 'ANNULLED';
CREATE INDEX ix_itm_window_equipment_start ON itm_window (equipment_id, start_at);
CREATE INDEX ix_itm_window_start ON itm_window (start_at);
CREATE INDEX ix_itm_window_end ON itm_window (end_at);
CREATE INDEX ix_itm_window_state ON itm_window (state);
CREATE INDEX ix_itm_window_batch ON itm_window (batch_id) WHERE batch_id IS NOT NULL;

-- Historial inmutable de cada ventana: una fila por cambio.
CREATE TABLE itm_window_revision (
    id               UUID         PRIMARY KEY,
    window_id        UUID         NOT NULL REFERENCES itm_window (id),
    revision         INTEGER      NOT NULL,
    start_at         TIMESTAMPTZ  NOT NULL,
    end_at           TIMESTAMPTZ,
    duration_minutes INTEGER,
    state            VARCHAR(20)  NOT NULL,
    comment          VARCHAR(500),
    review_reasons   JSONB        NOT NULL DEFAULT '[]',
    change           VARCHAR(20)  NOT NULL,
    justification    VARCHAR(500),
    actor_id         UUID         NOT NULL,
    at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_itm_window_revision UNIQUE (window_id, revision),
    CONSTRAINT ck_itm_revision_number CHECK (revision >= 1),
    CONSTRAINT ck_itm_revision_state CHECK (state IN ('OPEN', 'REVIEW', 'CONFIRMED', 'TEST', 'ANNULLED')),
    CONSTRAINT ck_itm_revision_change CHECK (change IN ('CREATED', 'CORRECTED', 'CLOSED', 'REVIEW_CONFIRMED',
        'MARKED_TEST', 'UNMARKED_TEST', 'ANNULLED', 'REPLACED_BY_IMPORT')),
    CONSTRAINT ck_itm_revision_reasons CHECK (jsonb_typeof(review_reasons) = 'array'),
    -- Todo cambio lleva su justificacion (FR-017), salvo la creacion y el cierre de una ventana abierta, que son
    -- el flujo normal de la captura y no corrigen nada.
    CONSTRAINT ck_itm_revision_justification CHECK (
        change IN ('CREATED', 'CLOSED') OR (justification IS NOT NULL AND length(btrim(justification)) > 0))
);

-- Fila de un lote con su vista previa editable.
CREATE TABLE itm_import_row (
    id             UUID        PRIMARY KEY,
    batch_id       UUID        NOT NULL REFERENCES itm_import_batch (id),
    row_number     INTEGER     NOT NULL,
    raw            JSONB       NOT NULL,
    edits          JSONB       NOT NULL DEFAULT '[]',
    interpretation JSONB       NOT NULL DEFAULT '{}',
    equipment_id   UUID,
    linked_by      UUID,
    linked_at      TIMESTAMPTZ,
    classification VARCHAR(20) NOT NULL,
    decision       VARCHAR(20),
    skip_reason    VARCHAR(500),
    resolved_by    UUID,
    resolved_at    TIMESTAMPTZ,
    touched_days   DATE[]      NOT NULL DEFAULT '{}',
    version        BIGINT      NOT NULL,
    CONSTRAINT uq_itm_import_row UNIQUE (batch_id, row_number),
    CONSTRAINT ck_itm_import_row_number CHECK (row_number >= 1),
    CONSTRAINT ck_itm_import_row_classification CHECK (
        classification IN ('NEW', 'IDENTICAL', 'CONFLICT', 'INVALID', 'PENDING_LINK', 'OUT_OF_SCOPE', 'REVIEW')),
    CONSTRAINT ck_itm_import_row_decision CHECK (decision IS NULL OR decision IN ('APPLY', 'SKIP', 'KEEP_PENDING')),
    CONSTRAINT ck_itm_import_row_skip_reason CHECK (
        decision IS DISTINCT FROM 'SKIP' OR (skip_reason IS NOT NULL AND length(btrim(skip_reason)) > 0)),
    CONSTRAINT ck_itm_import_row_raw CHECK (jsonb_typeof(raw) = 'object'),
    CONSTRAINT ck_itm_import_row_edits CHECK (jsonb_typeof(edits) = 'array'),
    CONSTRAINT ck_itm_import_row_link CHECK ((linked_by IS NULL) = (linked_at IS NULL)),
    CONSTRAINT ck_itm_import_row_resolved CHECK ((resolved_by IS NULL) = (resolved_at IS NULL))
);

CREATE INDEX ix_itm_import_row_classification ON itm_import_row (batch_id, classification);
CREATE INDEX ix_itm_import_row_touched_days ON itm_import_row USING gin (touched_days);
CREATE INDEX ix_itm_import_row_pending ON itm_import_row (batch_id)
    WHERE decision = 'KEEP_PENDING' AND resolved_at IS NULL;

-- Dias revisados: un registro por dia; la red es implicita (KIA).
CREATE TABLE itm_reviewed_day (
    day       DATE        PRIMARY KEY,
    active    BOOLEAN     NOT NULL,
    marked_by UUID        NOT NULL,
    marked_at TIMESTAMPTZ NOT NULL,
    version   BIGINT      NOT NULL
);

CREATE TABLE itm_reviewed_day_event (
    id                     UUID         PRIMARY KEY,
    day                    DATE         NOT NULL REFERENCES itm_reviewed_day (day),
    action                 VARCHAR(10)  NOT NULL,
    justification          VARCHAR(500),
    actor_id               UUID         NOT NULL,
    at                     TIMESTAMPTZ  NOT NULL,
    pending_batches_warned INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_itm_reviewed_day_event_action CHECK (action IN ('MARKED', 'WITHDRAWN')),
    CONSTRAINT ck_itm_reviewed_day_event_justification CHECK (
        action <> 'WITHDRAWN' OR (justification IS NOT NULL AND length(btrim(justification)) > 0)),
    CONSTRAINT ck_itm_reviewed_day_event_warned CHECK (pending_batches_warned >= 0)
);

CREATE INDEX ix_itm_reviewed_day_event_day ON itm_reviewed_day_event (day, at DESC);
