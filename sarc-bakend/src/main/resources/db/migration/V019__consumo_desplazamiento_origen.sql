-- Feature 009 (hotfix): cada sesion cuenta en la fecha de inicio que declara su fuente (FR-002, FR-006).
-- Antes, start_date se calculaba siempre en America/Guayaquil y una sesion Enerlink escrita en UTC+00:00
-- entre las 00:00 y las 05:00 caia en el dia anterior. Se guarda el desplazamiento de origen en minutos y se
-- recalcula la fecha de atribucion. Los instantes (started_at, ended_at) no cambian.
--
-- Evidencia del relleno (H006, specs/009-integridad-temporal-consumo/evidence/hotfix.md): se leyo el
-- encabezado "INICIO (UTC...)" del archivo original de cada lote Enerlink de produccion (copia del
-- 2026-11-13). La mayoria declara UTC-05:00, que coincide con la fecha que ya tenian. Solo los lotes listados
-- abajo declaran UTC+00:00. Wallbox siempre se interpreta en America/Guayaquil (-05:00, sin horario de
-- verano). En otras bases (desarrollo, pruebas) esos identificadores no existen y Enerlink queda en -05:00,
-- es decir, con la misma fecha que tenia.

ALTER TABLE energy_session ADD COLUMN source_utc_offset_minutes INTEGER;
ALTER TABLE energy_import_row ADD COLUMN source_utc_offset_minutes INTEGER;

ALTER TABLE energy_session ADD CONSTRAINT ck_energy_session_source_offset
    CHECK (source_utc_offset_minutes BETWEEN -1080 AND 1080);
ALTER TABLE energy_import_row ADD CONSTRAINT ck_energy_import_row_source_offset
    CHECK (source_utc_offset_minutes BETWEEN -1080 AND 1080);

CREATE TEMPORARY TABLE v019_utc_batch (id UUID PRIMARY KEY) ON COMMIT DROP;
INSERT INTO v019_utc_batch (id) VALUES
    ('0e3fdd42-2e56-4ae5-a323-441573c7497e'), -- Transacciones_01-10-2026_07-11-2026.xlsx
    ('d973837b-fed0-4382-884a-004f0213e609'), -- KIA Transacciones_07-11-2026_08-11-2026.xlsx
    ('2f5f0384-82e5-43c3-812f-1839e969eb1c'), -- Transacciones_10-11-2026_11-11-2026_3YTWm2W.xlsx
    ('16985066-b3c2-460f-b805-cd4efdfd16be'), -- Transacciones_10-11-2026_11-11-2026_NB6Lt5r.xlsx (descartado)
    ('cdc72562-1c7d-4ee5-9efc-2332a7562d31'), -- Transacciones_10-11-2026_12-11-2026.xlsx
    ('f8e2e56c-4a9b-41fe-b93e-747fffe35839'); -- Transacciones_11-11-2026_13-11-2026.xlsx

UPDATE energy_session s
SET source_utc_offset_minutes = CASE
        WHEN s.source = 'ENERLINK' AND s.import_batch_id IN (SELECT id FROM v019_utc_batch) THEN 0
        ELSE -300
    END;

UPDATE energy_import_row r
SET source_utc_offset_minutes = CASE
        WHEN b.source = 'ENERLINK' AND b.id IN (SELECT id FROM v019_utc_batch) THEN 0
        ELSE -300
    END
FROM energy_import_batch b
WHERE b.id = r.batch_id AND r.started_at IS NOT NULL;

-- Solo cambia la fecha de las sesiones cuyo dia de origen difiere del que tenian.
UPDATE energy_session
SET start_date = (started_at AT TIME ZONE 'UTC')::date
WHERE source_utc_offset_minutes = 0 AND start_date <> (started_at AT TIME ZONE 'UTC')::date;
