-- Solo los puntos KIA libres admiten ausencia de codigo; no se reescriben datos existentes.
ALTER TABLE asset_equipment ALTER COLUMN assigned_code DROP NOT NULL;
ALTER TABLE asset_equipment DROP CONSTRAINT ck_asset_equipment_assigned_code;
ALTER TABLE asset_equipment ADD CONSTRAINT ck_asset_equipment_assigned_code CHECK ((
    (network = 'KIA' AND infrastructure_type = 'CHARGING_POINT' AND (
        (usage_status = 'FREE' AND (assigned_code IS NULL OR assigned_code ~ '^[1-9][0-9]{5}$'))
        OR (usage_status IN ('BLOCKED', 'DISCONNECTED') AND assigned_code IS NOT NULL
            AND assigned_code ~ '^[1-9][0-9]{5}$')
    ))
    OR (NOT (network = 'KIA' AND infrastructure_type = 'CHARGING_POINT')
        AND assigned_code IS NOT NULL AND assigned_code = serial)
) IS TRUE);
