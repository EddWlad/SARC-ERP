-- RF-002 / RF-005.1: carga masiva de equipos desde la plantilla de SARC (archivo o pegado), con vista
-- previa, aprobacion de cambios y confirmacion. Las columnas comunes del lote son las de energy_import_batch.

CREATE TABLE asset_import_batch (
    id                  UUID         PRIMARY KEY,
    origin              VARCHAR(10)  NOT NULL,
    file_name           VARCHAR(255),
    checksum            VARCHAR(64)  NOT NULL,
    original_ref        VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    failure_code        VARCHAR(60),
    failure_message     VARCHAR(500),
    total_rows          INTEGER      NOT NULL DEFAULT 0,
    new_rows            INTEGER      NOT NULL DEFAULT 0,
    identical_rows      INTEGER      NOT NULL DEFAULT 0,
    modified_rows       INTEGER      NOT NULL DEFAULT 0,
    invalid_rows        INTEGER      NOT NULL DEFAULT 0,
    duplicate_rows      INTEGER      NOT NULL DEFAULT 0,
    deleted_exists_rows INTEGER      NOT NULL DEFAULT 0,
    conflict_rows       INTEGER      NOT NULL DEFAULT 0,
    applied_rows        INTEGER      NOT NULL DEFAULT 0,
    confirmed_at        TIMESTAMPTZ,
    confirmed_by        UUID,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT       NOT NULL,
    CONSTRAINT ck_asset_import_batch_origin CHECK (origin IN ('FILE', 'PASTE')),
    CONSTRAINT ck_asset_import_batch_status CHECK (
        status IN ('PARSING', 'READY', 'CONFIRMING', 'COMPLETED', 'FAILED', 'DISCARDED'))
);

CREATE INDEX ix_asset_import_batch_created ON asset_import_batch (created_at DESC);

-- Fila de la vista previa: las celdas tal como llegaron (texto), para mostrarlas aunque no sean
-- validas y volver a interpretarlas al confirmar; su clasificacion, observaciones y decision.
CREATE TABLE asset_import_row (
    id                UUID         PRIMARY KEY,
    batch_id          UUID         NOT NULL REFERENCES asset_import_batch (id),
    row_number        INTEGER      NOT NULL,
    network           VARCHAR(40),
    infrastructure    VARCHAR(40),
    brand             VARCHAR(40),
    name              VARCHAR(150),
    serial            VARCHAR(80),
    assigned_code     VARCHAR(80),
    uid_puk           VARCHAR(80),
    location          VARCHAR(300),
    maps_url          VARCHAR(500),
    ocpp_url          VARCHAR(500),
    usage_status      VARCHAR(40),
    connector_1       VARCHAR(40),
    connector_2       VARCHAR(40),
    connector_3       VARCHAR(40),
    enerlink_scope    VARCHAR(100),
    enerlink_charger  VARCHAR(150),
    wallbox_id        VARCHAR(150),
    ocpp_id           VARCHAR(150),
    classification    VARCHAR(20)  NOT NULL,
    issues            JSONB        NOT NULL,
    equipment_id      UUID         REFERENCES asset_equipment (id),
    equipment_version BIGINT,
    approved          BOOLEAN      NOT NULL,
    outcome           VARCHAR(20),
    outcome_detail    VARCHAR(500),
    CONSTRAINT uq_asset_import_row UNIQUE (batch_id, row_number),
    CONSTRAINT ck_asset_import_row_issues CHECK (jsonb_typeof(issues) = 'array'),
    CONSTRAINT ck_asset_import_row_classification CHECK (
        classification IN ('NEW', 'IDENTICAL', 'MODIFIED', 'INVALID', 'DUPLICATE', 'DELETED_EXISTS', 'CONFLICT')),
    CONSTRAINT ck_asset_import_row_outcome CHECK (
        outcome IS NULL OR outcome IN ('CREATED', 'UPDATED', 'UNCHANGED', 'SKIPPED')),
    -- Una fila identica o modificada apunta al equipo existente con la version que se reviso.
    CONSTRAINT ck_asset_import_row_existing CHECK (
        classification NOT IN ('IDENTICAL', 'MODIFIED') OR (equipment_id IS NOT NULL AND equipment_version IS NOT NULL))
);

CREATE INDEX ix_asset_import_row_batch ON asset_import_row (batch_id, classification, row_number);
