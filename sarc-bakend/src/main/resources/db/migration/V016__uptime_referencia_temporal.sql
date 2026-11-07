-- Feature006: referencia del informe no equivale a un intervalo observado.
-- No lectura de archivos ni reclasificacion definitiva en Flyway: candidatos a conciliacion.
ALTER TABLE upt_measurement
    ADD COLUMN period_precision VARCHAR(32) NOT NULL DEFAULT 'KNOWN_RANGE',
    ADD COLUMN reference_week DATE,
    ADD COLUMN reference_period_type VARCHAR(20),
    ALTER COLUMN period_start DROP NOT NULL,
    ALTER COLUMN period_end DROP NOT NULL;
ALTER TABLE upt_measurement DROP CONSTRAINT uq_upt_measurement_identity;
ALTER TABLE upt_measurement ADD CONSTRAINT ck_upt_measurement_precision CHECK
    (period_precision IN ('KNOWN_RANGE','REFERENCE_WEEK','REQUIRES_RECONCILIATION'));
ALTER TABLE upt_measurement ADD CONSTRAINT ck_upt_measurement_temporal CHECK (
    ((period_start IS NULL AND period_end IS NULL)
        OR (period_start IS NOT NULL AND period_end IS NOT NULL AND period_end > period_start))
    AND (reference_week IS NULL OR EXTRACT(ISODOW FROM reference_week) = 1)
    AND (reference_period_type IS NULL OR reference_period_type = 'ROLLING_7D')
    AND ((period_precision = 'KNOWN_RANGE' AND period_start IS NOT NULL AND period_end IS NOT NULL)
        OR (period_precision = 'REFERENCE_WEEK' AND period_start IS NULL AND period_end IS NULL
            AND reference_week IS NOT NULL AND reference_period_type IS NOT NULL
            AND reference_period_type = 'ROLLING_7D')
        OR period_precision = 'REQUIRES_RECONCILIATION')
);
-- Toda cadena con semana impuesta es candidata; la prueba requiere original y cadena completa.
UPDATE upt_measurement m SET period_precision = 'REQUIRES_RECONCILIATION'
WHERE EXISTS (SELECT 1 FROM upt_measurement_revision r
    JOIN upt_import_batch b ON b.id = r.import_batch_id
    WHERE r.measurement_id = m.id AND b.declared_period_start IS NOT NULL);
CREATE UNIQUE INDEX uq_upt_measurement_identity
    ON upt_measurement(connector_id, source, period_start, period_end)
    WHERE period_precision = 'KNOWN_RANGE';
CREATE UNIQUE INDEX uq_upt_measurement_reference
    ON upt_measurement(connector_id, source, reference_period_type, reference_week)
    WHERE period_precision = 'REFERENCE_WEEK';
CREATE INDEX ix_upt_measurement_reference
    ON upt_measurement(reference_week DESC, period_precision, connector_id);

ALTER TABLE upt_import_batch
    ADD COLUMN period_precision VARCHAR(32) NOT NULL DEFAULT 'KNOWN_RANGE',
    ADD COLUMN reference_week DATE,
    ADD CONSTRAINT ck_upt_batch_precision CHECK
        (period_precision IN ('KNOWN_RANGE','REFERENCE_WEEK','REQUIRES_RECONCILIATION')),
    ADD CONSTRAINT ck_upt_batch_reference CHECK
        ((reference_week IS NULL OR EXTRACT(ISODOW FROM reference_week) = 1)
        AND (period_precision <> 'REFERENCE_WEEK' OR
            (reference_week IS NOT NULL AND declared_period_type = 'ROLLING_7D')));
UPDATE upt_import_batch SET period_precision = 'REQUIRES_RECONCILIATION'
    WHERE declared_period_start IS NOT NULL;
ALTER TABLE upt_import_row
    ADD COLUMN period_precision VARCHAR(32) NOT NULL DEFAULT 'KNOWN_RANGE',
    ADD COLUMN reference_week DATE,
    ADD COLUMN reference_period_type VARCHAR(20),
    ADD CONSTRAINT ck_upt_row_precision CHECK
        (period_precision IN ('KNOWN_RANGE','REFERENCE_WEEK','REQUIRES_RECONCILIATION')),
    ADD CONSTRAINT ck_upt_row_reference CHECK (
        (reference_week IS NULL OR EXTRACT(ISODOW FROM reference_week) = 1)
        AND (reference_period_type IS NULL OR reference_period_type = 'ROLLING_7D')
        AND (period_precision <> 'REFERENCE_WEEK' OR (reference_week IS NOT NULL
            AND reference_period_type IS NOT NULL AND reference_period_type = 'ROLLING_7D'
            AND period_start IS NULL AND period_end IS NULL)));
UPDATE upt_import_row r SET period_precision = 'REQUIRES_RECONCILIATION'
    FROM upt_import_batch b WHERE b.id = r.batch_id AND b.declared_period_start IS NOT NULL;

CREATE TABLE upt_measurement_temporal_change (
    id UUID PRIMARY KEY,
    measurement_id UUID NOT NULL REFERENCES upt_measurement(id),
    policy_version VARCHAR(20) NOT NULL,
    expected_measurement_version BIGINT NOT NULL CHECK (expected_measurement_version >= 0),
    outcome VARCHAR(32) NOT NULL CHECK
        (outcome IN ('APPLIED','REQUIRES_RECONCILIATION','VERIFIED_KNOWN_RANGE')),
    previous_temporal JSONB NOT NULL CHECK (jsonb_typeof(previous_temporal) = 'object'),
    result_temporal JSONB NOT NULL CHECK (jsonb_typeof(result_temporal) = 'object'),
    evidence JSONB NOT NULL CHECK (jsonb_typeof(evidence) = 'object'),
    actor_id UUID,
    actor_kind VARCHAR(10) NOT NULL CHECK (actor_kind IN ('SYSTEM','USER')),
    occurred_at TIMESTAMPTZ NOT NULL,
    reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_upt_temporal_actor CHECK (actor_kind <> 'USER' OR actor_id IS NOT NULL),
    CONSTRAINT uq_upt_temporal_change UNIQUE (measurement_id, expected_measurement_version, policy_version)
);
CREATE INDEX ix_upt_temporal_change_history
    ON upt_measurement_temporal_change(measurement_id, occurred_at DESC);

CREATE TABLE upt_temporal_transition_work (
    id UUID PRIMARY KEY,
    requested_by UUID NOT NULL,
    request_key UUID NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    items JSONB NOT NULL,
    results JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(results) = 'object'),
    status VARCHAR(20) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    last_attempt_by UUID,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    updated_by UUID,
    version BIGINT NOT NULL,
    CONSTRAINT uq_upt_temporal_work_request UNIQUE (requested_by, request_key),
    CONSTRAINT ck_upt_temporal_work_items CHECK
        (jsonb_typeof(items) = 'array' AND jsonb_array_length(items) > 0),
    CONSTRAINT ck_upt_temporal_work_status CHECK
        (status IN ('QUEUED','RUNNING','COMPLETED','FAILED','INTERRUPTED'))
);
CREATE INDEX ix_upt_temporal_work_active ON upt_temporal_transition_work(status)
    WHERE status IN ('QUEUED','RUNNING');
