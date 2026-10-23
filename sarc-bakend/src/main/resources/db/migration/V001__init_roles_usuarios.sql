CREATE TABLE auth_role (
    code        VARCHAR(40)  PRIMARY KEY,
    description VARCHAR(200) NOT NULL
);

INSERT INTO auth_role (code, description) VALUES
    ('ADMINISTRADOR', 'Gestiona identidades, roles y todos los modulos'),
    ('GERENTE', 'Gerente de Electromovilidad: consulta y reportes ejecutivos'),
    ('SUPERVISOR', 'Supervisor de Electromovilidad: consulta operacional'),
    ('ASISTENTE', 'Asistente de Electromovilidad: consulta operacional'),
    ('ANALISTA', 'Analista de Monitoreo: captura, importacion y tickets');

CREATE TABLE auth_user (
    id                    UUID         PRIMARY KEY,
    email                 VARCHAR(254) NOT NULL,
    full_name             VARCHAR(150) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    must_change_password  BOOLEAN      NOT NULL,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    created_by            UUID,
    updated_by            UUID,
    version               BIGINT       NOT NULL,
    CONSTRAINT uq_auth_user_email UNIQUE (email),
    CONSTRAINT ck_auth_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_auth_user_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_auth_user_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE TABLE auth_user_role (
    user_id   UUID        NOT NULL REFERENCES auth_user (id),
    role_code VARCHAR(40) NOT NULL REFERENCES auth_role (code),
    PRIMARY KEY (user_id, role_code)
);

CREATE INDEX ix_auth_user_role_role ON auth_user_role (role_code);
