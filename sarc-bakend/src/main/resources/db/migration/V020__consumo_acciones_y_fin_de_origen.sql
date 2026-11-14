-- Feature 009 (US1 y US3): desplazamiento de origen del fin, baja logica de sesiones y revisiones de edicion y
-- baja. Solo agrega columnas opcionales o con valor por defecto: los registros anteriores siguen legibles y
-- no cambia ninguna fecha, instante ni estado existente.

-- US1: el fin puede declarar otro desplazamiento que el inicio. Nulo = el mismo que el inicio.
ALTER TABLE energy_session ADD COLUMN source_end_utc_offset_minutes INTEGER;
ALTER TABLE energy_import_row ADD COLUMN source_end_utc_offset_minutes INTEGER;
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_source_end_offset
    CHECK (source_end_utc_offset_minutes BETWEEN -1080 AND 1080);
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_source_end_offset
    CHECK (source_end_utc_offset_minutes BETWEEN -1080 AND 1080);

-- US3: baja logica. Conserva identidad, datos y estado; la excluye de los indicadores oficiales.
ALTER TABLE energy_session ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE energy_session ADD COLUMN deleted_by UUID;
ALTER TABLE energy_session ADD COLUMN deletion_reason VARCHAR(500);
ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_deletion
    CHECK ((deleted_at IS NULL AND deletion_reason IS NULL)
        OR (deleted_at IS NOT NULL AND deletion_reason IS NOT NULL));

-- US3: la revision distingue importacion, edicion y baja. Edicion y baja no tienen lote sucesor.
ALTER TABLE energy_session_revision ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'IMPORT';
ALTER TABLE energy_session_revision ADD CONSTRAINT ck_energy_session_revision_kind
    CHECK (kind IN ('IMPORT', 'EDIT', 'DELETION'));
ALTER TABLE energy_session_revision ADD COLUMN reason VARCHAR(500);
ALTER TABLE energy_session_revision ADD COLUMN state VARCHAR(20);
ALTER TABLE energy_session_revision ADD COLUMN source_utc_offset_minutes INTEGER;
ALTER TABLE energy_session_revision ADD COLUMN source_end_utc_offset_minutes INTEGER;
ALTER TABLE energy_session_revision ALTER COLUMN superseded_by_batch DROP NOT NULL;
ALTER TABLE energy_session_revision ADD CONSTRAINT ck_energy_session_revision_origin
    CHECK ((kind = 'IMPORT' AND superseded_by_batch IS NOT NULL)
        OR (kind <> 'IMPORT' AND superseded_by_batch IS NULL AND reason IS NOT NULL));

CREATE INDEX ix_energy_session_deleted ON energy_session (deleted_at) WHERE deleted_at IS NOT NULL;

-- US3: una fila que coincide con una sesion dada de baja se informa y no se aplica (la reimportacion no la
-- reactiva).
ALTER TABLE energy_import_row DROP CONSTRAINT ck_energy_import_row_classification;
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_classification CHECK (
    classification IN ('NEW', 'IDENTICAL', 'MODIFIED', 'EQUIPMENT_CHANGED', 'LINKABLE', 'INVALID', 'DUPLICATE',
                       'PENDING_LINK', 'DELETED'));
ALTER TABLE energy_import_batch ADD COLUMN deleted_rows INTEGER NOT NULL DEFAULT 0;
