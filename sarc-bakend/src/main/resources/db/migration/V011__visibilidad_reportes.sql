-- Feature004: metadatos de emisiones nuevas, sin reescribir originales.
ALTER TABLE report_issued ADD COLUMN content_profile VARCHAR(24);
ALTER TABLE report_issued ADD COLUMN cut_off TIMESTAMPTZ;
ALTER TABLE report_issued ADD COLUMN criteria JSONB;
ALTER TABLE report_issued ADD CONSTRAINT ck_report_content_profile CHECK(content_profile IS NULL OR content_profile IN ('OFFICIAL_ONLY','WITH_OPERATIONS'));
ALTER TABLE report_issued ADD CONSTRAINT ck_report_criteria_object CHECK(criteria IS NULL OR jsonb_typeof(criteria)='object');
ALTER TABLE report_issued ADD CONSTRAINT ck_report_publication_complete CHECK(content_profile IS NULL OR (cut_off IS NOT NULL AND criteria IS NOT NULL));
