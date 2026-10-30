-- Feature 002: vinculacion Enerlink y sesiones incompletas. No modifica filas existentes.

-- FR-008 a FR-010: una sesion iniciada puede no informar energia o termino; nulo no equivale a cero.
ALTER TABLE energy_session ALTER COLUMN ended_at DROP NOT NULL;
ALTER TABLE energy_session ALTER COLUMN energy_kwh DROP NOT NULL;
ALTER TABLE energy_session_revision ALTER COLUMN ended_at DROP NOT NULL;
ALTER TABLE energy_session_revision ALTER COLUMN energy_kwh DROP NOT NULL;

-- FR-006: motivo especifico de una sesion Enerlink pendiente de vincular.
ALTER TABLE energy_session ADD COLUMN link_issue VARCHAR(30);
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_link_issue CHECK (
    link_issue IS NULL OR link_issue IN (
        'IDENTITY_INCOMPLETE', 'IDENTITY_NOT_REGISTERED', 'IDENTITY_NOT_VERIFIED', 'IDENTITY_AMBIGUOUS'));
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_link_issue_unlinked CHECK (
    link_issue IS NULL OR equipment_id IS NULL);

-- Aclaracion 5: una sesion pendiente reimportada se vincula al confirmar si su par ya resuelve.
ALTER TABLE energy_import_row DROP CONSTRAINT ck_energy_import_row_classification;
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_classification CHECK (
    classification IN ('NEW', 'IDENTICAL', 'MODIFIED', 'EQUIPMENT_CHANGED', 'LINKABLE', 'INVALID', 'DUPLICATE',
                       'PENDING_LINK'));
ALTER TABLE energy_import_row DROP CONSTRAINT ck_energy_import_row_outcome;
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_outcome CHECK (
    outcome IS NULL OR outcome IN ('INSERTED', 'CORRECTED', 'REASSIGNED', 'LINKED', 'UNCHANGED', 'SKIPPED'));
ALTER TABLE energy_import_batch ADD COLUMN linkable_rows INTEGER NOT NULL DEFAULT 0;

-- FR-001: una fila que solo completa identidad Enerlink se aplica sin aprobacion por fila.
ALTER TABLE asset_import_row DROP CONSTRAINT ck_asset_import_row_classification;
ALTER TABLE asset_import_row ADD CONSTRAINT ck_asset_import_row_classification CHECK (
    classification IN ('NEW', 'IDENTICAL', 'MODIFIED', 'IDENTITY_COMPLETED', 'INVALID', 'DUPLICATE',
                       'DELETED_EXISTS', 'CONFLICT'));
ALTER TABLE asset_import_row DROP CONSTRAINT ck_asset_import_row_existing;
ALTER TABLE asset_import_row ADD CONSTRAINT ck_asset_import_row_existing CHECK (
    classification NOT IN ('IDENTICAL', 'MODIFIED', 'IDENTITY_COMPLETED')
    OR (equipment_id IS NOT NULL AND equipment_version IS NOT NULL));
ALTER TABLE asset_import_row DROP CONSTRAINT ck_asset_import_row_outcome;
ALTER TABLE asset_import_row ADD CONSTRAINT ck_asset_import_row_outcome CHECK (
    outcome IS NULL OR outcome IN ('CREATED', 'UPDATED', 'IDENTITY_ADDED', 'UNCHANGED', 'SKIPPED'));
ALTER TABLE asset_import_batch ADD COLUMN identity_completed_rows INTEGER NOT NULL DEFAULT 0;
