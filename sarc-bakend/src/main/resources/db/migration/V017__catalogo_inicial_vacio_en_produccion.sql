-- El primer arranque de producción deja que el administrador defina su catálogo.
-- V004 permanece intacta. En bases en uso se conserva el catálogo existente.
DO $$
BEGIN
    IF current_setting('sarc.empty_initial_connector_catalog', true) IS DISTINCT FROM 'on'
        OR EXISTS (SELECT 1 FROM auth_user)
        OR EXISTS (SELECT 1 FROM audit_event)
        OR EXISTS (SELECT 1 FROM asset_equipment) THEN
        RETURN;
    END IF;

    DELETE FROM asset_connector_type
    WHERE (id, code) IN (
        ('00000000-0000-0000-0000-000000000c01'::uuid, 'TYPE2'),
        ('00000000-0000-0000-0000-000000000c02'::uuid, 'CCS2'),
        ('00000000-0000-0000-0000-000000000c03'::uuid, 'CHADEMO'),
        ('00000000-0000-0000-0000-000000000c04'::uuid, 'GBT_AC'),
        ('00000000-0000-0000-0000-000000000c05'::uuid, 'GBT_DC'),
        ('00000000-0000-0000-0000-000000000c06'::uuid, 'TYPE1')
    ) AND version = 0 AND created_by IS NULL AND updated_by IS NULL;
END;
$$;
