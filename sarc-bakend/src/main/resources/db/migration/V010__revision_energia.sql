-- Feature 003: revision energetica; conserva filas y migraciones anteriores.
ALTER TABLE energy_session ADD COLUMN energy_review VARCHAR(20);
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_review
    CHECK (energy_review IS NULL OR energy_review IN ('PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE energy_session DROP CONSTRAINT ck_energy_session_state;
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_state CHECK (
    state IN ('VALIDATED', 'PENDING_LINK', 'AWAITING_APPROVAL', 'TEST', 'ENERGY_REVIEW', 'ENERGY_REJECTED'));
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_review_official
    CHECK (state <> 'VALIDATED' OR energy_review IS NULL OR energy_review = 'APPROVED');
CREATE INDEX ix_energy_session_review ON energy_session (energy_review, start_date) WHERE energy_review IS NOT NULL;
ALTER TABLE energy_session_revision ADD COLUMN energy_review VARCHAR(20);
ALTER TABLE energy_session_revision ADD CONSTRAINT ck_energy_session_revision_review
    CHECK (energy_review IS NULL OR energy_review IN ('PENDING', 'APPROVED', 'REJECTED'));
ALTER TABLE energy_import_batch ADD COLUMN energy_review_rows INTEGER NOT NULL DEFAULT 0;

CREATE TABLE energy_session_review (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES energy_session (id),
    session_revision INTEGER NOT NULL CHECK (session_revision >= 1),
    session_version BIGINT NOT NULL,
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    justification VARCHAR(500) NOT NULL CHECK (length(btrim(justification)) > 0),
    energy_kwh NUMERIC(12,3) NOT NULL CHECK (energy_kwh > 1000),
    decided_by UUID,
    decided_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_energy_session_review_version UNIQUE (session_id, session_version)
);
CREATE INDEX ix_energy_session_review_history ON energy_session_review (session_id, session_version DESC);
