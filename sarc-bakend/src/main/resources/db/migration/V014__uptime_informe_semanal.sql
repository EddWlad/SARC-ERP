-- RF-004, informe semanal de uptime (2026-11-02). El modelo que se envia por correo no trae Ocupado:
-- su porcentaje es el valor de la medicion (base REPORTED_PERCENT). La semana se declara al cargar y
-- cada fila conserva su hoja y su fila de origen (D09, excepcion del informe semanal de uptime).

ALTER TABLE upt_measurement_revision
    ADD COLUMN uptime_basis VARCHAR(20) NOT NULL DEFAULT 'STATES',
    ADD CONSTRAINT ck_upt_revision_basis CHECK (
        uptime_basis = 'STATES'
        OR (uptime_basis = 'REPORTED_PERCENT' AND reading_class = 'MEASURED'
            AND occupied_minutes IS NULL AND reported_percent IS NOT NULL));

ALTER TABLE upt_import_row
    ADD COLUMN uptime_basis VARCHAR(20) NOT NULL DEFAULT 'STATES',
    ADD COLUMN source_sheet VARCHAR(100),
    ADD COLUMN source_row   INTEGER,
    ADD CONSTRAINT ck_upt_import_row_basis CHECK (uptime_basis IN ('STATES', 'REPORTED_PERCENT'));

ALTER TABLE upt_import_batch
    ADD COLUMN declared_period_start TIMESTAMPTZ,
    ADD COLUMN declared_period_end   TIMESTAMPTZ,
    ADD CONSTRAINT ck_upt_import_batch_declared_period CHECK (
        (declared_period_start IS NULL AND declared_period_end IS NULL)
        OR declared_period_end > declared_period_start);
