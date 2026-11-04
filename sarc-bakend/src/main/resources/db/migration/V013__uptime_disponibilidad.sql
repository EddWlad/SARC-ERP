-- RF-004: mediciones de uptime por conector. Cabecera por identidad y revisiones por valor: una
-- correccion espera validacion mientras la version validada sigue vigente. La alineacion semanal y el
-- fin no futuro son reglas de dominio porque dependen de la zona operativa y del reloj.

CREATE TABLE upt_import_batch (
    id                   UUID         PRIMARY KEY,
    source               VARCHAR(20)  NOT NULL,
    origin               VARCHAR(10)  NOT NULL,
    file_name            VARCHAR(255),
    checksum             VARCHAR(64)  NOT NULL,
    original_ref         VARCHAR(255) NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    failure_code         VARCHAR(60),
    failure_message      VARCHAR(500),
    declared_period_type VARCHAR(20)  NOT NULL,
    total_rows           INTEGER      NOT NULL DEFAULT 0,
    new_rows             INTEGER      NOT NULL DEFAULT 0,
    identical_rows       INTEGER      NOT NULL DEFAULT 0,
    conflict_rows        INTEGER      NOT NULL DEFAULT 0,
    invalid_rows         INTEGER      NOT NULL DEFAULT 0,
    pending_link_rows    INTEGER      NOT NULL DEFAULT 0,
    observed_rows        INTEGER      NOT NULL DEFAULT 0,
    applied_rows         INTEGER      NOT NULL DEFAULT 0,
    exact_confirmed_by   UUID,
    exact_confirmed_at   TIMESTAMPTZ,
    confirmed_at         TIMESTAMPTZ,
    confirmed_by         UUID,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT       NOT NULL,
    CONSTRAINT ck_upt_import_batch_source CHECK (source IN ('ENERLINK')),
    CONSTRAINT ck_upt_import_batch_origin CHECK (origin IN ('FILE', 'PASTE')),
    CONSTRAINT ck_upt_import_batch_status CHECK (
        status IN ('PARSING', 'READY', 'CONFIRMING', 'COMPLETED', 'FAILED', 'DISCARDED')),
    CONSTRAINT ck_upt_import_batch_declared_type CHECK (
        declared_period_type IN ('ROLLING_24H', 'ROLLING_7D', 'ROLLING_30D'))
);

CREATE INDEX ix_upt_import_batch_created ON upt_import_batch (created_at DESC);

CREATE TABLE upt_measurement (
    id                 UUID        PRIMARY KEY,
    connector_id       UUID        NOT NULL REFERENCES asset_connector (id),
    equipment_id       UUID        NOT NULL REFERENCES asset_equipment (id),
    source             VARCHAR(20) NOT NULL,
    period_start       TIMESTAMPTZ NOT NULL,
    period_end         TIMESTAMPTZ NOT NULL,
    period_zone        VARCHAR(40) NOT NULL,
    latest_revision    INTEGER     NOT NULL,
    effective_revision INTEGER,
    excluded           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    created_by         UUID,
    updated_by         UUID,
    version            BIGINT      NOT NULL,
    CONSTRAINT uq_upt_measurement_identity UNIQUE (connector_id, source, period_start, period_end),
    CONSTRAINT ck_upt_measurement_source CHECK (source IN ('ENERLINK')),
    CONSTRAINT ck_upt_measurement_period CHECK (period_end > period_start),
    CONSTRAINT ck_upt_measurement_latest CHECK (latest_revision >= 1),
    CONSTRAINT ck_upt_measurement_effective CHECK (
        effective_revision IS NULL OR effective_revision <= latest_revision)
);

CREATE INDEX ix_upt_measurement_connector_end ON upt_measurement (connector_id, period_end DESC);
CREATE INDEX ix_upt_measurement_period_end ON upt_measurement (period_end DESC);

CREATE TABLE upt_measurement_revision (
    id                       UUID           PRIMARY KEY,
    measurement_id           UUID           NOT NULL REFERENCES upt_measurement (id),
    revision                 INTEGER        NOT NULL,
    reading_class            VARCHAR(20)    NOT NULL,
    period_type              VARCHAR(20)    NOT NULL,
    available_minutes        INTEGER,
    occupied_minutes         INTEGER,
    fault_minutes            INTEGER,
    offline_minutes          INTEGER,
    reported_percent         NUMERIC(12, 4),
    review_state             VARCHAR(20)    NOT NULL,
    observations             JSONB          NOT NULL,
    justification            VARCHAR(500),
    origin                   VARCHAR(10)    NOT NULL,
    import_batch_id          UUID           REFERENCES upt_import_batch (id),
    source_values            JSONB,
    equipment_label          VARCHAR(150)   NOT NULL,
    equipment_code           VARCHAR(80),
    connector_type_label     VARCHAR(100)   NOT NULL,
    exact_confirmed_by       UUID,
    exact_confirmed_at       TIMESTAMPTZ,
    entered_by               UUID,
    entered_at               TIMESTAMPTZ    NOT NULL,
    validated_by             UUID,
    validated_at             TIMESTAMPTZ,
    validation_justification VARCHAR(500),
    superseded_at            TIMESTAMPTZ,
    CONSTRAINT uq_upt_measurement_revision UNIQUE (measurement_id, revision),
    CONSTRAINT ck_upt_revision_number CHECK (revision >= 1),
    CONSTRAINT ck_upt_revision_class CHECK (
        reading_class IN ('MEASURED', 'NO_DATA', 'OUT_OF_SCOPE', 'RETIRED')),
    CONSTRAINT ck_upt_revision_period_type CHECK (
        period_type IN ('ROLLING_24H', 'ROLLING_7D', 'ROLLING_30D', 'EXACT_WEEK')),
    CONSTRAINT ck_upt_revision_state CHECK (review_state IN ('PENDING', 'VALIDATED', 'SUPERSEDED')),
    CONSTRAINT ck_upt_revision_origin CHECK (origin IN ('MANUAL', 'BLOCK', 'FILE', 'PASTE')),
    CONSTRAINT ck_upt_revision_minutes CHECK (
        (available_minutes IS NULL OR available_minutes >= 0)
        AND (occupied_minutes IS NULL OR occupied_minutes >= 0)
        AND (fault_minutes IS NULL OR fault_minutes >= 0)
        AND (offline_minutes IS NULL OR offline_minutes >= 0)),
    CONSTRAINT ck_upt_revision_special CHECK (
        reading_class = 'MEASURED'
        OR (available_minutes IS NULL AND occupied_minutes IS NULL AND fault_minutes IS NULL
            AND offline_minutes IS NULL AND reported_percent IS NULL)),
    CONSTRAINT ck_upt_revision_exact CHECK (
        period_type <> 'EXACT_WEEK' OR exact_confirmed_at IS NOT NULL),
    CONSTRAINT ck_upt_revision_validated CHECK (review_state <> 'VALIDATED' OR validated_at IS NOT NULL),
    CONSTRAINT ck_upt_revision_observations CHECK (jsonb_typeof(observations) = 'array')
);

CREATE TABLE upt_measurement_exclusion (
    id                  UUID         PRIMARY KEY,
    measurement_id      UUID         NOT NULL REFERENCES upt_measurement (id),
    action              VARCHAR(20)  NOT NULL,
    justification       VARCHAR(500) NOT NULL,
    measurement_version BIGINT       NOT NULL,
    decided_by          UUID,
    decided_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_upt_measurement_exclusion UNIQUE (measurement_id, measurement_version),
    CONSTRAINT ck_upt_exclusion_action CHECK (action IN ('EXCLUDED', 'RESTORED')),
    CONSTRAINT ck_upt_exclusion_justification CHECK (length(btrim(justification)) > 0)
);

CREATE TABLE upt_import_row (
    id                      UUID           PRIMARY KEY,
    batch_id                UUID           NOT NULL REFERENCES upt_import_batch (id),
    row_number              INTEGER        NOT NULL,
    source_site             VARCHAR(150),
    source_charger          VARCHAR(150),
    source_connector        VARCHAR(150),
    equipment_id            UUID,
    connector_id            UUID,
    period_start            TIMESTAMPTZ,
    period_end              TIMESTAMPTZ,
    period_type             VARCHAR(20),
    available_minutes       INTEGER,
    occupied_minutes        INTEGER,
    fault_minutes           INTEGER,
    offline_minutes         INTEGER,
    reported_percent        NUMERIC(12, 4),
    source_values           JSONB,
    classification          VARCHAR(20)    NOT NULL,
    issues                  JSONB          NOT NULL,
    observations            JSONB          NOT NULL,
    approved                BOOLEAN        NOT NULL,
    justification           VARCHAR(500),
    existing_measurement_id UUID,
    existing_revision       INTEGER,
    outcome                 VARCHAR(20),
    applied_measurement_id  UUID,
    version                 BIGINT         NOT NULL,
    CONSTRAINT uq_upt_import_row UNIQUE (batch_id, row_number),
    CONSTRAINT ck_upt_import_row_issues CHECK (jsonb_typeof(issues) = 'array'),
    CONSTRAINT ck_upt_import_row_observations CHECK (jsonb_typeof(observations) = 'array'),
    CONSTRAINT ck_upt_import_row_classification CHECK (
        classification IN ('NEW', 'IDENTICAL', 'CONFLICT', 'INVALID', 'PENDING_LINK')),
    CONSTRAINT ck_upt_import_row_outcome CHECK (
        outcome IS NULL OR outcome IN ('APPLIED', 'SKIPPED', 'FAILED', 'PENDING_LINK', 'LINKED'))
);

CREATE INDEX ix_upt_import_row_batch ON upt_import_row (batch_id, classification, row_number);
CREATE INDEX ix_upt_import_row_pending ON upt_import_row (batch_id) WHERE outcome = 'PENDING_LINK';

CREATE TABLE upt_connector_alias (
    id             UUID         PRIMARY KEY,
    source         VARCHAR(20)  NOT NULL,
    equipment_id   UUID         NOT NULL REFERENCES asset_equipment (id),
    connector_id   UUID         NOT NULL REFERENCES asset_connector (id),
    label_original VARCHAR(150) NOT NULL,
    label_key      VARCHAR(310) NOT NULL,
    active         BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    created_by     UUID,
    updated_by     UUID,
    version        BIGINT       NOT NULL,
    CONSTRAINT uq_upt_connector_alias UNIQUE (source, equipment_id, label_key),
    CONSTRAINT ck_upt_connector_alias_source CHECK (source IN ('ENERLINK'))
);
