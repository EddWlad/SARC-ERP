CREATE TABLE audit_event (
    id          UUID         PRIMARY KEY,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor_id    UUID,
    action      VARCHAR(60)  NOT NULL,
    entity_type VARCHAR(60)  NOT NULL,
    entity_id   UUID,
    trace_id    VARCHAR(64),
    details     JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id, occurred_at);
