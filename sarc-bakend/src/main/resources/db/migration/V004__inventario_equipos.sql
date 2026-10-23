-- RF-002: inventario maestro de puntos de carga y electrolineras.
-- Las reglas por red/tipo se validan en el dominio y, como red de seguridad, con CHECK en la base.

CREATE TABLE asset_connector_type (
    id           UUID         PRIMARY KEY,
    code         VARCHAR(30)  NOT NULL,
    name         VARCHAR(100) NOT NULL,
    admin_status SMALLINT     NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT       NOT NULL,
    CONSTRAINT uq_asset_connector_type_code UNIQUE (code),
    CONSTRAINT ck_asset_connector_type_code CHECK (code ~ '^[A-Z0-9_]+$'),
    CONSTRAINT ck_asset_connector_type_status CHECK (admin_status IN (1, 2))
);

INSERT INTO asset_connector_type (id, code, name, admin_status, created_at, updated_at, version) VALUES
    ('00000000-0000-0000-0000-000000000c01', 'TYPE2', 'Tipo 2 (Mennekes) AC', 1, now(), now(), 0),
    ('00000000-0000-0000-0000-000000000c02', 'CCS2', 'CCS Combo 2 DC', 1, now(), now(), 0),
    ('00000000-0000-0000-0000-000000000c03', 'CHADEMO', 'CHAdeMO DC', 1, now(), now(), 0),
    ('00000000-0000-0000-0000-000000000c04', 'GBT_AC', 'GB/T AC', 1, now(), now(), 0),
    ('00000000-0000-0000-0000-000000000c05', 'GBT_DC', 'GB/T DC', 1, now(), now(), 0),
    ('00000000-0000-0000-0000-000000000c06', 'TYPE1', 'Tipo 1 (J1772) AC', 1, now(), now(), 0);

CREATE TABLE asset_equipment (
    id                    UUID         PRIMARY KEY,
    network               VARCHAR(20)  NOT NULL,
    infrastructure_type   VARCHAR(20)  NOT NULL,
    brand                 VARCHAR(20)  NOT NULL,
    name                  VARCHAR(150) NOT NULL,
    serial                VARCHAR(80)  NOT NULL,
    assigned_code         VARCHAR(80)  NOT NULL,
    uid_puk               VARCHAR(80),
    location              VARCHAR(300),
    maps_url              VARCHAR(500),
    ocpp_url              VARCHAR(500),
    image_ref             VARCHAR(255),
    admin_status          SMALLINT     NOT NULL,
    usage_status          VARCHAR(20),
    schedule_mode         VARCHAR(20)  NOT NULL,
    replaces_equipment_id UUID         REFERENCES asset_equipment (id),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT       NOT NULL,
    -- Unicidad global, tambien frente a equipos inactivos o eliminados (RF-002.2): sin indices parciales.
    CONSTRAINT uq_asset_equipment_serial UNIQUE (serial),
    CONSTRAINT uq_asset_equipment_assigned_code UNIQUE (assigned_code),
    CONSTRAINT uq_asset_equipment_uid_puk UNIQUE (uid_puk),
    CONSTRAINT ck_asset_equipment_network CHECK (network IN ('KIA', 'JAC', 'CONECTZ')),
    CONSTRAINT ck_asset_equipment_type CHECK (infrastructure_type IN ('CHARGING_POINT', 'CHARGING_STATION')),
    CONSTRAINT ck_asset_equipment_brand CHECK (brand IN ('BENNY', 'WALLBOX')),
    CONSTRAINT ck_asset_equipment_admin_status CHECK (admin_status IN (0, 1, 2)),
    CONSTRAINT ck_asset_equipment_schedule_mode CHECK (schedule_mode IN ('NOT_DEFINED', 'ALWAYS_OPEN', 'WEEKLY')),
    CONSTRAINT ck_asset_equipment_usage_status CHECK (
        (infrastructure_type = 'CHARGING_POINT' AND usage_status IN ('FREE', 'BLOCKED', 'DISCONNECTED'))
        OR (infrastructure_type = 'CHARGING_STATION' AND usage_status IS NULL)),
    CONSTRAINT ck_asset_equipment_uid_puk CHECK (infrastructure_type <> 'CHARGING_POINT' OR uid_puk IS NOT NULL),
    CONSTRAINT ck_asset_equipment_assigned_code CHECK (
        (network = 'KIA' AND infrastructure_type = 'CHARGING_POINT' AND assigned_code ~ '^[1-9][0-9]{5}$')
        OR (NOT (network = 'KIA' AND infrastructure_type = 'CHARGING_POINT') AND assigned_code = serial)),
    CONSTRAINT ck_asset_equipment_not_self_replacement CHECK (replaces_equipment_id <> id)
);

CREATE INDEX ix_asset_equipment_filters ON asset_equipment (admin_status, network, infrastructure_type, brand);
-- El listado pagina ordenado por nombre.
CREATE INDEX ix_asset_equipment_name ON asset_equipment (name);

CREATE TABLE asset_connector (
    id                UUID        PRIMARY KEY,
    equipment_id      UUID        NOT NULL REFERENCES asset_equipment (id),
    ordinal           SMALLINT    NOT NULL,
    connector_type_id UUID        NOT NULL REFERENCES asset_connector_type (id),
    active            BOOLEAN     NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    created_by        UUID,
    updated_by        UUID,
    version           BIGINT      NOT NULL,
    CONSTRAINT uq_asset_connector_ordinal UNIQUE (equipment_id, ordinal),
    CONSTRAINT ck_asset_connector_ordinal CHECK (ordinal BETWEEN 1 AND 3)
);

CREATE INDEX ix_asset_connector_type ON asset_connector (connector_type_id);

-- Horario semanal: cada fila es un intervalo; si cierra antes de abrir, cruza la medianoche.
CREATE TABLE asset_schedule_interval (
    equipment_id UUID     NOT NULL REFERENCES asset_equipment (id),
    day_of_week  SMALLINT NOT NULL,
    opens_at     TIME     NOT NULL,
    closes_at    TIME     NOT NULL,
    CONSTRAINT ck_asset_schedule_interval_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_asset_schedule_interval_span CHECK (opens_at <> closes_at)
);

CREATE INDEX ix_asset_schedule_interval_equipment ON asset_schedule_interval (equipment_id);

-- Excepciones por fecha: prevalecen sobre el horario habitual (cerrado o un horario especial).
CREATE TABLE asset_schedule_exception (
    equipment_id   UUID         NOT NULL REFERENCES asset_equipment (id),
    exception_date DATE         NOT NULL,
    closed         BOOLEAN      NOT NULL,
    opens_at       TIME,
    closes_at      TIME,
    note           VARCHAR(200),
    CONSTRAINT uq_asset_schedule_exception UNIQUE (equipment_id, exception_date),
    CONSTRAINT ck_asset_schedule_exception_hours CHECK (
        (closed AND opens_at IS NULL AND closes_at IS NULL)
        OR (NOT closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at <> closes_at))
);

-- Identidades del equipo en plataformas externas, para conciliar importaciones (RF-002.10).
CREATE TABLE asset_external_identity (
    id           UUID         PRIMARY KEY,
    equipment_id UUID         NOT NULL REFERENCES asset_equipment (id),
    platform     VARCHAR(20)  NOT NULL,
    scope        VARCHAR(100) NOT NULL,
    external_id  VARCHAR(150) NOT NULL,
    alias        VARCHAR(150),
    verified     BOOLEAN      NOT NULL,
    verified_by  UUID,
    verified_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    created_by   UUID,
    updated_by   UUID,
    version      BIGINT       NOT NULL,
    CONSTRAINT uq_asset_external_identity UNIQUE (platform, scope, external_id),
    CONSTRAINT ck_asset_external_identity_platform CHECK (platform IN ('WALLBOX', 'ENERLINK', 'OCPP')),
    CONSTRAINT ck_asset_external_identity_verified CHECK (verified = (verified_at IS NOT NULL))
);

CREATE INDEX ix_asset_external_identity_equipment ON asset_external_identity (equipment_id);
