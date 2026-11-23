-- Feature 010: incidencias, tickets y tiempos. Solo tablas nuevas (prefijo inc_) y su siembra; no altera ni carga
-- datos de las existentes. Las referencias a equipos, conectores y usuarios son UUID sin clave foranea entre modulos,
-- igual que Intermitencias: se validan con IncidentAssetDirectory e IncidentStaffDirectory.
--
-- NO siembra ningun numero de ticket (FR-013): el numero inicial de produccion lo configura un Administrador
-- despues de publicar (P-01).
--
-- Hasta que T009 la compruebe contra una copia de produccion, solo se aplica en bases desechables. Si ya se aplico en
-- una base persistente, cualquier cambio va en V022: una migracion aplicada no se reescribe.

-- Contador de una sola fila. start_number y next_number son nulos hasta que el Administrador lo configura.
CREATE TABLE inc_ticket_numbering (
    id            SMALLINT    PRIMARY KEY,
    start_number  INTEGER,
    next_number   INTEGER,
    environment   VARCHAR(12),
    configured_at TIMESTAMPTZ,
    configured_by UUID,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_inc_ticket_numbering_single CHECK (id = 1),
    CONSTRAINT ck_inc_ticket_numbering_start CHECK (start_number IS NULL OR start_number >= 1),
    CONSTRAINT ck_inc_ticket_numbering_environment CHECK (environment IS NULL OR environment IN ('PRODUCTION', 'TEST')),
    CONSTRAINT ck_inc_ticket_numbering_configured CHECK (
        (start_number IS NULL AND next_number IS NULL AND environment IS NULL)
        OR (start_number IS NOT NULL AND next_number IS NOT NULL AND environment IS NOT NULL
            AND next_number >= start_number))
);

INSERT INTO inc_ticket_numbering (id) VALUES (1);

-- Referencias de tiempo por version, inmutables (P-03). No son SLA contractual.
CREATE TABLE inc_reference_version (
    id             UUID         PRIMARY KEY,
    code           VARCHAR(40)  NOT NULL,
    label          VARCHAR(160) NOT NULL,
    effective_from TIMESTAMPTZ  NOT NULL,
    source_note    VARCHAR(500) NOT NULL,
    CONSTRAINT uq_inc_reference_version_code UNIQUE (code)
);

CREATE TABLE inc_reference_rule (
    version_id           UUID        NOT NULL REFERENCES inc_reference_version (id),
    incident_type        VARCHAR(40) NOT NULL,
    validation_max_min   INTEGER,
    recovery_max_min     INTEGER,
    recovery_extendable  BOOLEAN     NOT NULL DEFAULT FALSE,
    escalation_max_min   INTEGER,
    persistent_after_min INTEGER,
    proposed_priority    VARCHAR(10) NOT NULL,
    PRIMARY KEY (version_id, incident_type),
    CONSTRAINT ck_inc_reference_rule_priority CHECK (proposed_priority IN ('MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_inc_reference_rule_type CHECK (incident_type IN (
        'WAIT_FOR_CONNECTION', 'DISCONNECTION', 'CONNECTOR_FAILURE', 'EMERGENCY_STOP', 'CONNECTOR_STUCK',
        'CANNOT_STOP_CHARGING', 'KIA_PERSISTENT_OUTAGE', 'INTERMITTENCY', 'INSTALLATION_OR_CONNECTIVITY',
        'CHATBOT_SERVICE_FAILURE', 'SITE_ELEMENT_FAILURE', 'UNCATALOGED')),
    CONSTRAINT ck_inc_reference_rule_minutes CHECK (
        COALESCE(validation_max_min, 1) >= 0 AND COALESCE(recovery_max_min, 1) >= 0
        AND COALESCE(escalation_max_min, 1) >= 0 AND COALESCE(persistent_after_min, 1) >= 0)
);

-- Version inicial: reproduce FR-034 (prioridades) y FR-051 (referencias) con el sustento del protocolo. Los tipos sin
-- sustento (paro de emergencia, novedad de instalacion o conectividad, chatbot, elemento del sitio y no catalogada) no
-- llevan fila: no tienen referencia y su prioridad propuesta por defecto es MEDIUM.
INSERT INTO inc_reference_version (id, code, label, effective_from, source_note)
VALUES ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'PROTOCOLO-FIRMADO-1',
        'Protocolo de monitoreo firmado (versión por confirmar)', TIMESTAMPTZ '2000-01-01 00:00:00+00',
        'PRD §10.3, Protocolo §5');

INSERT INTO inc_reference_rule (version_id, incident_type, validation_max_min, recovery_max_min, recovery_extendable,
                                escalation_max_min, persistent_after_min, proposed_priority)
VALUES ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'WAIT_FOR_CONNECTION', 5, NULL, FALSE, 60, NULL, 'MEDIUM'),
       ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'DISCONNECTION', 5, 300, TRUE, NULL, NULL, 'HIGH'),
       ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'CONNECTOR_FAILURE', 5, 180, FALSE, NULL, NULL, 'HIGH'),
       ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'CONNECTOR_STUCK', 5, 120, FALSE, NULL, NULL, 'CRITICAL'),
       ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'CANNOT_STOP_CHARGING', 5, NULL, FALSE, NULL, NULL, 'CRITICAL'),
       ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'KIA_PERSISTENT_OUTAGE', NULL, NULL, FALSE, NULL, 30, 'MEDIUM'),
       ('5b3f1c0e-0a10-4a01-9a10-000000000001', 'INTERMITTENCY', NULL, NULL, FALSE, NULL, NULL, 'MEDIUM');

-- Ticket: raiz del agregado. Columnas de BaseAuditableEntity al final.
CREATE TABLE inc_ticket (
    id                       UUID         PRIMARY KEY,
    ticket_number            INTEGER      NOT NULL,
    ticket_code              VARCHAR(20)  GENERATED ALWAYS AS (
        'TICKET-' || lpad(ticket_number::text, greatest(3, length(ticket_number::text)), '0')) STORED,
    client_request_id        UUID         NOT NULL,
    state                    VARCHAR(8)   NOT NULL,
    element_class            VARCHAR(20)  NOT NULL,
    equipment_id             UUID,
    equipment_label_at_t0    VARCHAR(150),
    network_at_t0            VARCHAR(20),
    infrastructure_type_at_t0 VARCHAR(20),
    chatbot_scope            VARCHAR(10),
    site_element_kind        VARCHAR(20),
    element_name             VARCHAR(120),
    reference_equipment_id   UUID,
    manual_site_name         VARCHAR(150),
    manual_location          VARCHAR(300),
    manual_maps_url          VARCHAR(500),
    site_label_at_t0         VARCHAR(150),
    location_at_t0           VARCHAR(300),
    maps_url_at_t0           VARCHAR(500),
    incident_type            VARCHAR(40)  NOT NULL,
    report_origin            VARCHAR(20)  NOT NULL,
    report_origin_detail     VARCHAR(200),
    description              VARCHAR(500) NOT NULL,
    priority                 VARCHAR(10)  NOT NULL,
    current_responsible_id   UUID         NOT NULL,
    current_responsible_name VARCHAR(150) NOT NULL,
    duplicate_key            VARCHAR(200) NOT NULL,
    duplicate_override_reason VARCHAR(300),
    last_activity_at         TIMESTAMPTZ  NOT NULL,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT       NOT NULL,
    CONSTRAINT uq_inc_ticket_number UNIQUE (ticket_number),
    CONSTRAINT uq_inc_ticket_code UNIQUE (ticket_code),
    CONSTRAINT uq_inc_ticket_client_request UNIQUE (client_request_id),
    CONSTRAINT ck_inc_ticket_number CHECK (ticket_number >= 1),
    CONSTRAINT ck_inc_ticket_state CHECK (state IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_inc_ticket_class CHECK (element_class IN ('EQUIPMENT', 'CHATBOT_SERVICE', 'SITE_ELEMENT')),
    CONSTRAINT ck_inc_ticket_network CHECK (network_at_t0 IS NULL OR network_at_t0 IN ('KIA', 'JAC', 'CONECTZ')),
    CONSTRAINT ck_inc_ticket_infrastructure CHECK (
        infrastructure_type_at_t0 IS NULL OR infrastructure_type_at_t0 IN ('CHARGING_POINT', 'CHARGING_STATION')),
    CONSTRAINT ck_inc_ticket_chatbot_scope CHECK (chatbot_scope IS NULL OR chatbot_scope IN ('TOTAL', 'PARTIAL')),
    CONSTRAINT ck_inc_ticket_site_kind CHECK (
        site_element_kind IS NULL OR site_element_kind IN ('VEHICLE_BARRIER', 'OTHER')),
    CONSTRAINT ck_inc_ticket_origin CHECK (
        report_origin IN ('PLATFORM', 'CHATBOT', 'SITE_STAFF', 'CUSTOMER', 'PROVIDER', 'OTHER')),
    CONSTRAINT ck_inc_ticket_origin_detail CHECK (
        report_origin <> 'OTHER' OR (report_origin_detail IS NOT NULL AND length(btrim(report_origin_detail)) > 0)),
    CONSTRAINT ck_inc_ticket_priority CHECK (priority IN ('MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_inc_ticket_type CHECK (incident_type IN (
        'WAIT_FOR_CONNECTION', 'DISCONNECTION', 'CONNECTOR_FAILURE', 'EMERGENCY_STOP', 'CONNECTOR_STUCK',
        'CANNOT_STOP_CHARGING', 'KIA_PERSISTENT_OUTAGE', 'INTERMITTENCY', 'INSTALLATION_OR_CONNECTIVITY',
        'CHATBOT_SERVICE_FAILURE', 'SITE_ELEMENT_FAILURE', 'UNCATALOGED')),
    -- Un tipo solo sirve para las clases que lo admiten (FR-022). IncidentType.allowedFor lo repite y una prueba
    -- comprueba que coincidan.
    CONSTRAINT ck_inc_ticket_type_class CHECK (
        (element_class = 'EQUIPMENT' AND incident_type NOT IN ('CHATBOT_SERVICE_FAILURE', 'SITE_ELEMENT_FAILURE'))
        OR (element_class = 'CHATBOT_SERVICE' AND incident_type IN ('CHATBOT_SERVICE_FAILURE', 'UNCATALOGED'))
        OR (element_class = 'SITE_ELEMENT' AND incident_type IN ('SITE_ELEMENT_FAILURE', 'UNCATALOGED'))),
    CONSTRAINT ck_inc_ticket_equipment CHECK (
        element_class <> 'EQUIPMENT'
        OR (equipment_id IS NOT NULL AND equipment_label_at_t0 IS NOT NULL AND network_at_t0 IS NOT NULL
            AND infrastructure_type_at_t0 IS NOT NULL
            AND chatbot_scope IS NULL AND site_element_kind IS NULL AND element_name IS NULL
            AND reference_equipment_id IS NULL AND manual_site_name IS NULL AND manual_location IS NULL
            AND manual_maps_url IS NULL AND site_label_at_t0 IS NULL AND location_at_t0 IS NULL
            AND maps_url_at_t0 IS NULL)),
    CONSTRAINT ck_inc_ticket_chatbot CHECK (
        element_class <> 'CHATBOT_SERVICE'
        OR (equipment_id IS NULL AND equipment_label_at_t0 IS NULL AND network_at_t0 IS NULL
            AND infrastructure_type_at_t0 IS NULL
            AND site_element_kind IS NULL AND element_name IS NULL
            AND reference_equipment_id IS NULL AND manual_site_name IS NULL AND manual_location IS NULL
            AND manual_maps_url IS NULL AND site_label_at_t0 IS NULL AND location_at_t0 IS NULL
            AND maps_url_at_t0 IS NULL)),
    -- Elemento del sitio: exactamente una de dos opciones de ubicacion, sin equipo propio, sin red y sin conectores.
    CONSTRAINT ck_inc_ticket_site CHECK (
        element_class <> 'SITE_ELEMENT'
        OR (site_element_kind IS NOT NULL
            AND equipment_id IS NULL AND equipment_label_at_t0 IS NULL AND network_at_t0 IS NULL
            AND infrastructure_type_at_t0 IS NULL AND chatbot_scope IS NULL
            AND ((reference_equipment_id IS NOT NULL AND manual_site_name IS NULL AND manual_location IS NULL
                  AND manual_maps_url IS NULL)
                 OR (reference_equipment_id IS NULL AND manual_site_name IS NOT NULL
                     AND manual_location IS NOT NULL)))),
    CONSTRAINT ck_inc_ticket_site_other_name CHECK (
        element_class <> 'SITE_ELEMENT' OR site_element_kind <> 'OTHER'
        OR (element_name IS NOT NULL AND length(btrim(element_name)) > 0))
);

CREATE INDEX ix_inc_ticket_state_activity ON inc_ticket (state, last_activity_at DESC);
CREATE INDEX ix_inc_ticket_duplicate_open ON inc_ticket (duplicate_key) WHERE state = 'OPEN';
CREATE INDEX ix_inc_ticket_equipment ON inc_ticket (equipment_id);
CREATE INDEX ix_inc_ticket_reference_equipment ON inc_ticket (reference_equipment_id);
CREATE INDEX ix_inc_ticket_site ON inc_ticket (element_class, site_element_kind, reference_equipment_id);
CREATE INDEX ix_inc_ticket_network ON inc_ticket (network_at_t0);
CREATE INDEX ix_inc_ticket_type ON inc_ticket (incident_type);

-- Conectores afectados por identidad (D06). Sin filas, la afectacion es del equipo completo (FR-002).
CREATE TABLE inc_ticket_connector (
    ticket_id       UUID     NOT NULL REFERENCES inc_ticket (id),
    connector_id    UUID     NOT NULL,
    position_at_t0  SMALLINT NOT NULL,
    PRIMARY KEY (ticket_id, connector_id),
    CONSTRAINT ck_inc_ticket_connector_position CHECK (position_at_t0 BETWEEN 1 AND 3)
);

-- Relacion entre tickets: una sola fila por par, visible en ambos sentidos.
CREATE TABLE inc_ticket_relation (
    ticket_id         UUID         NOT NULL REFERENCES inc_ticket (id),
    related_ticket_id UUID         NOT NULL REFERENCES inc_ticket (id),
    kind              VARCHAR(20)  NOT NULL,
    reason            VARCHAR(300),
    created_at        TIMESTAMPTZ  NOT NULL,
    created_by        UUID,
    PRIMARY KEY (ticket_id, related_ticket_id),
    CONSTRAINT ck_inc_ticket_relation_order CHECK (ticket_id < related_ticket_id),
    CONSTRAINT ck_inc_ticket_relation_kind CHECK (kind IN ('INDEPENDENT_FAILURE', 'COMMON_FAILURE'))
);

CREATE INDEX ix_inc_ticket_relation_related ON inc_ticket_relation (related_ticket_id);

-- Episodio: ciclo de atencion. El primero se abre con el ticket y cada reapertura crea otro.
CREATE TABLE inc_episode (
    id                           UUID         PRIMARY KEY,
    ticket_id                    UUID         NOT NULL REFERENCES inc_ticket (id),
    ordinal                      SMALLINT     NOT NULL,
    state                        VARCHAR(8)   NOT NULL,
    reference_version_id         UUID         NOT NULL REFERENCES inc_reference_version (id),
    reopen_reason                VARCHAR(500),
    closure_result               VARCHAR(20),
    duplicate_of_ticket_id       UUID         REFERENCES inc_ticket (id),
    cause_kind                   VARCHAR(20),
    cause_text                   VARCHAR(500),
    recommendations              VARCHAR(1000),
    final_evidence_waiver        VARCHAR(300),
    closure_communication_waiver VARCHAR(300),
    closure_checks               JSONB,
    deviation_justification      VARCHAR(500),
    created_at                   TIMESTAMPTZ  NOT NULL,
    updated_at                   TIMESTAMPTZ  NOT NULL,
    created_by                   UUID,
    updated_by                   UUID,
    version                      BIGINT       NOT NULL,
    CONSTRAINT uq_inc_episode_ordinal UNIQUE (ticket_id, ordinal),
    CONSTRAINT ck_inc_episode_ordinal CHECK (ordinal >= 1),
    CONSTRAINT ck_inc_episode_state CHECK (state IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_inc_episode_reopen CHECK (
        ordinal = 1 OR (reopen_reason IS NOT NULL AND length(btrim(reopen_reason)) > 0)),
    CONSTRAINT ck_inc_episode_result CHECK (
        closure_result IS NULL
        OR closure_result IN ('RECOVERED', 'NO_FAULT_CONFIRMED', 'DUPLICATE', 'REGISTERED_IN_ERROR')),
    CONSTRAINT ck_inc_episode_closed_result CHECK (state <> 'CLOSED' OR closure_result IS NOT NULL),
    CONSTRAINT ck_inc_episode_duplicate CHECK (
        closure_result IS DISTINCT FROM 'DUPLICATE' OR duplicate_of_ticket_id IS NOT NULL),
    CONSTRAINT ck_inc_episode_cause_kind CHECK (
        cause_kind IS NULL OR cause_kind IN ('CONFIRMED', 'PROBABLE', 'UNDETERMINED')),
    CONSTRAINT ck_inc_episode_closed_cause CHECK (state <> 'CLOSED' OR cause_kind IS NOT NULL),
    -- La descripcion de la causa es obligatoria salvo si no esta determinada (FR-047).
    CONSTRAINT ck_inc_episode_cause_text CHECK (
        cause_kind IS NULL OR cause_kind = 'UNDETERMINED'
        OR (cause_text IS NOT NULL AND length(btrim(cause_text)) > 0))
);

CREATE INDEX ix_inc_episode_ticket ON inc_episode (ticket_id, ordinal);

-- Hito: cinco filas por episodio, creadas con el (T0 RECORDED y T1 a T4 PENDING).
CREATE TABLE inc_milestone (
    episode_id        UUID         NOT NULL REFERENCES inc_episode (id),
    kind              VARCHAR(2)   NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    occurred_at       TIMESTAMPTZ,
    reason            VARCHAR(300),
    retroactive       BOOLEAN      NOT NULL DEFAULT FALSE,
    order_warning_ack BOOLEAN      NOT NULL DEFAULT FALSE,
    recorded_by       UUID,
    recorded_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (episode_id, kind),
    CONSTRAINT ck_inc_milestone_kind CHECK (kind IN ('T0', 'T1', 'T2', 'T3', 'T4')),
    CONSTRAINT ck_inc_milestone_status CHECK (status IN ('PENDING', 'RECORDED', 'NOT_APPLICABLE', 'NO_DATA')),
    CONSTRAINT ck_inc_milestone_time CHECK ((status = 'RECORDED') = (occurred_at IS NOT NULL)),
    CONSTRAINT ck_inc_milestone_reason CHECK (
        status NOT IN ('NOT_APPLICABLE', 'NO_DATA') OR (reason IS NOT NULL AND length(btrim(reason)) > 0)),
    CONSTRAINT ck_inc_milestone_t0 CHECK (kind <> 'T0' OR status = 'RECORDED'),
    CONSTRAINT ck_inc_milestone_t4 CHECK (kind <> 'T4' OR status NOT IN ('NOT_APPLICABLE', 'NO_DATA'))
);

-- Revisiones de hitos: solo se anexan filas, nunca se actualizan ni se borran (research R3).
CREATE TABLE inc_milestone_revision (
    id                   UUID         PRIMARY KEY,
    episode_id           UUID         NOT NULL REFERENCES inc_episode (id),
    kind                 VARCHAR(2)   NOT NULL,
    previous_status      VARCHAR(16),
    previous_occurred_at TIMESTAMPTZ,
    previous_reason      VARCHAR(300),
    new_status           VARCHAR(16)  NOT NULL,
    new_occurred_at      TIMESTAMPTZ,
    new_reason           VARCHAR(300),
    justification        VARCHAR(500),
    recorded_by          UUID,
    recorded_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_inc_milestone_revision_kind CHECK (kind IN ('T0', 'T1', 'T2', 'T3', 'T4')),
    CONSTRAINT ck_inc_milestone_revision_status CHECK (
        new_status IN ('PENDING', 'RECORDED', 'NOT_APPLICABLE', 'NO_DATA')
        AND (previous_status IS NULL OR previous_status IN ('PENDING', 'RECORDED', 'NOT_APPLICABLE', 'NO_DATA')))
);

CREATE INDEX ix_inc_milestone_revision_episode ON inc_milestone_revision (episode_id, kind, recorded_at);

-- Cronologia. La anulacion es la unica modificacion permitida: ningun evento se borra.
CREATE TABLE inc_event (
    id                      UUID         PRIMARY KEY,
    ticket_id               UUID         NOT NULL REFERENCES inc_ticket (id),
    episode_id              UUID         REFERENCES inc_episode (id),
    type                    VARCHAR(30)  NOT NULL,
    occurred_at             TIMESTAMPTZ  NOT NULL,
    recorded_at             TIMESTAMPTZ  NOT NULL,
    recorded_by             UUID,
    retroactive             BOOLEAN      NOT NULL DEFAULT FALSE,
    text                    VARCHAR(1000),
    target_kind             VARCHAR(20),
    target_role_or_provider VARCHAR(150),
    details                 JSONB,
    notifiable              BOOLEAN      NOT NULL DEFAULT FALSE,
    annulled_at             TIMESTAMPTZ,
    annulled_by             UUID,
    annul_reason            VARCHAR(300),
    CONSTRAINT ck_inc_event_type CHECK (type IN (
        'TICKET_CREATED', 'VALIDATION', 'CORRECTIVE_ACTION', 'ESCALATED', 'OBSERVATION', 'DATA_CORRECTED',
        'PRIORITY_CHANGED', 'RESPONSIBLE_ASSIGNED', 'SHIFT_HANDOVER', 'RECLASSIFIED', 'VISIT_STAGE',
        'COMMUNICATION_RECORDED', 'EVIDENCE_ADDED', 'MILESTONE_RECORDED', 'MILESTONE_CORRECTED', 'TICKET_CLOSED',
        'TICKET_REOPENED', 'RELATION_ADDED')),
    CONSTRAINT ck_inc_event_target_kind CHECK (
        target_kind IS NULL OR target_kind IN ('TECHNICAL', 'SUPERVISION', 'PLATFORM_SUPPORT', 'OTHER')),
    -- Solo el destino TECHNICAL cuenta para la referencia de escalamiento (FR-052).
    CONSTRAINT ck_inc_event_escalated CHECK (type <> 'ESCALATED' OR target_kind IS NOT NULL),
    CONSTRAINT ck_inc_event_annulment CHECK (
        (annulled_at IS NULL AND annulled_by IS NULL AND annul_reason IS NULL)
        OR (annulled_at IS NOT NULL AND annul_reason IS NOT NULL))
);

CREATE INDEX ix_inc_event_ticket ON inc_event (ticket_id, recorded_at);
CREATE INDEX ix_inc_event_notifiable ON inc_event (recorded_at) WHERE notifiable;

-- Visita tecnica registrada a mano (P-08). Cada cambio de etapa deja ademas un evento VISIT_STAGE.
CREATE TABLE inc_visit (
    id                      UUID         PRIMARY KEY,
    ticket_id               UUID         NOT NULL REFERENCES inc_ticket (id),
    episode_id              UUID         NOT NULL REFERENCES inc_episode (id),
    provider_name           VARCHAR(120),
    stage                   VARCHAR(20)  NOT NULL,
    cancel_decided_by       VARCHAR(150),
    cancel_evidence_note    VARCHAR(500),
    reescalation_condition  VARCHAR(300),
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    created_by              UUID,
    updated_by              UUID,
    version                 BIGINT       NOT NULL,
    CONSTRAINT ck_inc_visit_stage CHECK (stage IN (
        'REQUESTED', 'ASSIGNED', 'CONFIRMED', 'CANCELLED', 'POSTPONED', 'EXECUTED', 'REPORT_RECEIVED')),
    -- Cancelar o aplazar exige quien decidio, la evidencia y la condicion de nueva escalada (Protocolo §9.4).
    CONSTRAINT ck_inc_visit_cancel CHECK (
        stage NOT IN ('CANCELLED', 'POSTPONED')
        OR (cancel_decided_by IS NOT NULL AND cancel_evidence_note IS NOT NULL AND reescalation_condition IS NOT NULL))
);

CREATE INDEX ix_inc_visit_ticket ON inc_visit (ticket_id);
CREATE INDEX ix_inc_visit_provider ON inc_visit (provider_name) WHERE provider_name IS NOT NULL;

-- Comunicacion publicada a mano. Copiar el texto no crea ninguna fila (FR-059).
CREATE TABLE inc_communication (
    id              UUID         PRIMARY KEY,
    ticket_id       UUID         NOT NULL REFERENCES inc_ticket (id),
    episode_id      UUID         NOT NULL REFERENCES inc_episode (id),
    channel         VARCHAR(30)  NOT NULL,
    channel_detail  VARCHAR(150),
    kind            VARCHAR(10)  NOT NULL,
    sent_at         TIMESTAMPTZ  NOT NULL,
    retroactive     BOOLEAN      NOT NULL DEFAULT FALSE,
    justification   VARCHAR(300),
    recorded_by     UUID,
    recorded_at     TIMESTAMPTZ  NOT NULL,
    annulled_at     TIMESTAMPTZ,
    annulled_by     UUID,
    annul_reason    VARCHAR(300),
    CONSTRAINT ck_inc_communication_channel CHECK (channel IN (
        'REDAE_INPSERCOM', 'KIA_ELECTROLINERAS', 'CARGADORES_ELECTRICOS', 'AUTOEASTERN_ENERLINK', 'DIRECTIVOS',
        'EMAIL', 'OTHER')),
    CONSTRAINT ck_inc_communication_detail CHECK (
        channel <> 'OTHER' OR (channel_detail IS NOT NULL AND length(btrim(channel_detail)) > 0)),
    CONSTRAINT ck_inc_communication_kind CHECK (kind IN ('INITIAL', 'UPDATE', 'CLOSURE')),
    CONSTRAINT ck_inc_communication_retroactive CHECK (
        NOT retroactive OR (justification IS NOT NULL AND length(btrim(justification)) > 0)),
    CONSTRAINT ck_inc_communication_annulment CHECK (
        (annulled_at IS NULL AND annulled_by IS NULL AND annul_reason IS NULL)
        OR (annulled_at IS NOT NULL AND annul_reason IS NOT NULL))
);

CREATE INDEX ix_inc_communication_ticket ON inc_communication (ticket_id, sent_at);

-- Evidencia: imagen o PDF. No se borra: si se cargo por error, se anula su evento.
CREATE TABLE inc_evidence (
    id               UUID         PRIMARY KEY,
    ticket_id        UUID         NOT NULL REFERENCES inc_ticket (id),
    event_id         UUID         REFERENCES inc_event (id),
    communication_id UUID         REFERENCES inc_communication (id),
    purpose          VARCHAR(12)  NOT NULL,
    storage_ref      VARCHAR(255) NOT NULL,
    format           VARCHAR(8)   NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    sha256           CHAR(64)     NOT NULL,
    original_name    VARCHAR(200) NOT NULL,
    uploaded_by      UUID,
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_inc_evidence_purpose CHECK (purpose IN ('GENERAL', 'INITIAL', 'FINAL')),
    CONSTRAINT ck_inc_evidence_format CHECK (format IN ('JPEG', 'PNG', 'WEBP', 'PDF')),
    CONSTRAINT ck_inc_evidence_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_inc_evidence_ticket ON inc_evidence (ticket_id);
CREATE INDEX ix_inc_evidence_event ON inc_evidence (event_id);
