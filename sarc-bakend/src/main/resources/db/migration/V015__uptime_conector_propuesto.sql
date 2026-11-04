-- RF-004, vinculacion asistida (2026-11-02). Cuando el numero de la etiqueta contradice el inventario
-- pero su tipo coincide con un solo conector activo del equipo, la fila queda pendiente con ese
-- conector propuesto. Es solo una sugerencia: nada se atribuye hasta que una persona vincula (FR-028).

ALTER TABLE upt_import_row
    ADD COLUMN suggested_connector_id UUID;
