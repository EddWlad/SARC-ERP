-- RF-003: importacion de sesiones de carga (Enerlink, Wallbox), vista previa y consumo diario.

-- Lote de importacion: archivo o pegado original, su huella y el resultado de cada etapa.
CREATE TABLE energy_import_batch (
    id                UUID         PRIMARY KEY,
    source            VARCHAR(20)  NOT NULL,
    source_scope      VARCHAR(100) NOT NULL,
    origin            VARCHAR(10)  NOT NULL,
    file_name         VARCHAR(255),
    checksum          VARCHAR(64)  NOT NULL,
    original_ref      VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    failure_code      VARCHAR(60),
    failure_message   VARCHAR(500),
    total_rows        INTEGER      NOT NULL DEFAULT 0,
    new_rows          INTEGER      NOT NULL DEFAULT 0,
    identical_rows    INTEGER      NOT NULL DEFAULT 0,
    modified_rows     INTEGER      NOT NULL DEFAULT 0,
    invalid_rows      INTEGER      NOT NULL DEFAULT 0,
    duplicate_rows    INTEGER      NOT NULL DEFAULT 0,
    pending_link_rows INTEGER      NOT NULL DEFAULT 0,
    applied_rows      INTEGER      NOT NULL DEFAULT 0,
    confirmed_at      TIMESTAMPTZ,
    confirmed_by      UUID,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT       NOT NULL,
    CONSTRAINT ck_energy_import_batch_source CHECK (source IN ('ENERLINK', 'WALLBOX')),
    CONSTRAINT ck_energy_import_batch_origin CHECK (origin IN ('FILE', 'PASTE')),
    CONSTRAINT ck_energy_import_batch_status CHECK (
        status IN ('PARSING', 'READY', 'CONFIRMING', 'COMPLETED', 'FAILED', 'DISCARDED'))
);

CREATE INDEX ix_energy_import_batch_created ON energy_import_batch (created_at DESC);

-- Fila de la vista previa: valor original interpretado, clasificacion y decision del analista.
CREATE TABLE energy_import_row (
    id                   UUID         PRIMARY KEY,
    batch_id             UUID         NOT NULL REFERENCES energy_import_batch (id),
    row_number           INTEGER      NOT NULL,
    external_session_id  VARCHAR(100),
    started_at           TIMESTAMPTZ,
    ended_at             TIMESTAMPTZ,
    energy_kwh           NUMERIC(12, 3),
    source_site          VARCHAR(150),
    source_charger       VARCHAR(150),
    source_serial        VARCHAR(80),
    source_connector     VARCHAR(150),
    equipment_id         UUID,
    network              VARCHAR(20),
    infrastructure_type  VARCHAR(20),
    classification       VARCHAR(20)  NOT NULL,
    issues               JSONB        NOT NULL,
    non_preferred_source BOOLEAN      NOT NULL,
    test                 BOOLEAN      NOT NULL,
    approved             BOOLEAN      NOT NULL,
    existing_session_id  UUID,
    outcome              VARCHAR(20),
    CONSTRAINT uq_energy_import_row UNIQUE (batch_id, row_number),
    CONSTRAINT ck_energy_import_row_issues CHECK (jsonb_typeof(issues) = 'array'),
    CONSTRAINT ck_energy_import_row_classification CHECK (
        classification IN ('NEW', 'IDENTICAL', 'MODIFIED', 'INVALID', 'DUPLICATE', 'PENDING_LINK')),
    CONSTRAINT ck_energy_import_row_outcome CHECK (
        outcome IS NULL OR outcome IN ('INSERTED', 'CORRECTED', 'UNCHANGED', 'SKIPPED'))
);

CREATE INDEX ix_energy_import_row_batch ON energy_import_row (batch_id, classification, row_number);

-- Sesion de carga: unidad de conteo de consumo. Se cuenta en el dia local de INICIO (PRD RF-003.10).
CREATE TABLE energy_session (
    id                  UUID           PRIMARY KEY,
    source              VARCHAR(20)    NOT NULL,
    source_scope        VARCHAR(100)   NOT NULL,
    external_session_id VARCHAR(100)   NOT NULL,
    equipment_id        UUID           REFERENCES asset_equipment (id),
    network             VARCHAR(20),
    infrastructure_type VARCHAR(20),
    source_site         VARCHAR(150),
    source_charger      VARCHAR(150),
    source_serial       VARCHAR(80),
    source_connector    VARCHAR(150),
    started_at          TIMESTAMPTZ    NOT NULL,
    ended_at            TIMESTAMPTZ    NOT NULL,
    start_date          DATE           NOT NULL,
    energy_kwh          NUMERIC(12, 3) NOT NULL,
    state               VARCHAR(20)    NOT NULL,
    revision            INTEGER        NOT NULL,
    import_batch_id     UUID           NOT NULL REFERENCES energy_import_batch (id),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT         NOT NULL,
    -- Reimportar no duplica (CA-003-01): la clave es la de la plataforma de origen.
    CONSTRAINT uq_energy_session_source UNIQUE (source, source_scope, external_session_id),
    CONSTRAINT ck_energy_session_source CHECK (source IN ('ENERLINK', 'WALLBOX')),
    CONSTRAINT ck_energy_session_state CHECK (state IN ('VALIDATED', 'PENDING_LINK', 'TEST')),
    CONSTRAINT ck_energy_session_span CHECK (ended_at >= started_at),
    CONSTRAINT ck_energy_session_energy CHECK (energy_kwh >= 0),
    -- La red y el tipo oficiales salen del equipo vinculado, nunca del archivo.
    CONSTRAINT ck_energy_session_link CHECK (
        (equipment_id IS NULL AND network IS NULL AND infrastructure_type IS NULL)
        OR (equipment_id IS NOT NULL AND network IS NOT NULL AND infrastructure_type IS NOT NULL)),
    CONSTRAINT ck_energy_session_validated_linked CHECK (state <> 'VALIDATED' OR equipment_id IS NOT NULL),
    CONSTRAINT ck_energy_session_revision CHECK (revision >= 1)
);

CREATE INDEX ix_energy_session_period ON energy_session (start_date, state);
CREATE INDEX ix_energy_session_equipment ON energy_session (equipment_id, start_date);
-- Conciliacion: recorre las pendientes en orden de id.
CREATE INDEX ix_energy_session_pending ON energy_session (id) WHERE state = 'PENDING_LINK';

-- Version anterior de una sesion corregida por una reimportacion aprobada (RF-003.6).
CREATE TABLE energy_session_revision (
    id                   UUID           PRIMARY KEY,
    session_id           UUID           NOT NULL REFERENCES energy_session (id),
    revision             INTEGER        NOT NULL,
    started_at           TIMESTAMPTZ    NOT NULL,
    ended_at             TIMESTAMPTZ    NOT NULL,
    energy_kwh           NUMERIC(12, 3) NOT NULL,
    source_site          VARCHAR(150),
    source_charger       VARCHAR(150),
    source_serial        VARCHAR(80),
    source_connector     VARCHAR(150),
    import_batch_id      UUID           NOT NULL REFERENCES energy_import_batch (id),
    superseded_at        TIMESTAMPTZ    NOT NULL,
    superseded_by        UUID,
    superseded_by_batch  UUID           NOT NULL REFERENCES energy_import_batch (id),
    CONSTRAINT uq_energy_session_revision UNIQUE (session_id, revision)
);
