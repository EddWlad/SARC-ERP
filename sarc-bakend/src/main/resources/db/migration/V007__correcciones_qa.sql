-- Correcciones de la revision QA del 26-oct-2026 (QA-01, QA-02, QA-03, Lectura-01).

-- QA-03: una sesion de fuente no preferente para su equipo no suma hasta que un analista la aprueba,
-- sea cual sea el momento en que se identifico el equipo. Las sesiones ya vinculadas conservan su
-- estado: si eran de fuente no preferente, se importaron con aprobacion (o se vincularon antes de esta
-- regla) y se marcan como aprobadas para no alterar los historicos.
ALTER TABLE energy_session ADD COLUMN source_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE energy_session SET source_approved = TRUE
WHERE equipment_id IS NOT NULL
  AND NOT ((network = 'KIA' AND infrastructure_type = 'CHARGING_POINT' AND source = 'WALLBOX')
        OR (NOT (network = 'KIA' AND infrastructure_type = 'CHARGING_POINT') AND source = 'ENERLINK'));
ALTER TABLE energy_session ALTER COLUMN source_approved DROP DEFAULT;

ALTER TABLE energy_session DROP CONSTRAINT ck_energy_session_state;
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_state
    CHECK (state IN ('VALIDATED', 'PENDING_LINK', 'AWAITING_APPROVAL', 'TEST'));
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_awaiting_linked
    CHECK (state <> 'AWAITING_APPROVAL' OR equipment_id IS NOT NULL);

-- QA-01: la version anterior conserva tambien el equipo, que cambia al reasignar una sesion.
ALTER TABLE energy_session_revision ADD COLUMN equipment_id UUID;

-- QA-01 y QA-02: la fila guarda que revision de la sesion se reviso y si se reasigno de cargador.
ALTER TABLE energy_import_row ADD COLUMN existing_session_revision INTEGER;
ALTER TABLE energy_import_row ADD COLUMN reassigned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE energy_import_row ALTER COLUMN reassigned DROP DEFAULT;
ALTER TABLE energy_import_row ADD COLUMN outcome_detail VARCHAR(500);
ALTER TABLE energy_import_row DROP CONSTRAINT ck_energy_import_row_classification;
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_classification CHECK (
    classification IN ('NEW', 'IDENTICAL', 'MODIFIED', 'EQUIPMENT_CHANGED', 'INVALID', 'DUPLICATE', 'PENDING_LINK'));
ALTER TABLE energy_import_row DROP CONSTRAINT ck_energy_import_row_outcome;
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_outcome CHECK (
    outcome IS NULL OR outcome IN ('INSERTED', 'CORRECTED', 'REASSIGNED', 'UNCHANGED', 'SKIPPED'));

ALTER TABLE energy_import_batch ADD COLUMN equipment_changed_rows INTEGER NOT NULL DEFAULT 0;

-- Lectura-01 (RF-RPT-08): cada reporte emitido, con su alcance, huella y copia privada del archivo.
CREATE TABLE report_issued (
    id          UUID         PRIMARY KEY,
    kind        VARCHAR(30)  NOT NULL,
    format      VARCHAR(10)  NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    scope       VARCHAR(500) NOT NULL,
    checksum    VARCHAR(64)  NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    file_ref    VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL,
    CONSTRAINT ck_report_issued_format CHECK (format IN ('PDF', 'XLSX')),
    CONSTRAINT ck_report_issued_size CHECK (size_bytes >= 0)
);

CREATE INDEX ix_report_issued_kind ON report_issued (kind, created_at DESC);
CREATE INDEX ix_report_issued_created ON report_issued (created_at DESC);
