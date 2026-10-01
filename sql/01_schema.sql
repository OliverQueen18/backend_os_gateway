-- OS Gateway PostgreSQL schema
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS roles (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS permissions (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200),
    phone           VARCHAR(30),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id         BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           VARCHAR(512) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS operators (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    logo_url        VARCHAR(500),
    logo_storage_key VARCHAR(500),
    logo_content_type VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS distributor_accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    code            VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(200) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    email           VARCHAR(255),
    phone           VARCHAR(30),
    address         VARCHAR(500),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    rccm            VARCHAR(50),
    nif             VARCHAR(50),
    nina            VARCHAR(50),
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0,
    commission_rate NUMERIC(5,2) NOT NULL DEFAULT 1.50,
    -- PIN transaction (BCrypt) — saisi à chaque opération
    pin_hash        VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    registration_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED'
                    CHECK (registration_status IN ('FEE_PENDING','UNDER_REVIEW','APPROVED','REJECTED')),
    registration_fee_amount NUMERIC(18,2),
    registration_fee_paid BOOLEAN NOT NULL DEFAULT FALSE,
    registration_fee_paid_at TIMESTAMPTZ,
    registration_fee_payment_ref VARCHAR(64),
    registration_fee_payment_method VARCHAR(30),
    rejection_reason TEXT,
    reviewed_by     BIGINT REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    submitted_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS distributor_attachments (
    id              BIGSERIAL PRIMARY KEY,
    distributor_id  BIGINT NOT NULL REFERENCES distributor_accounts(id) ON DELETE CASCADE,
    doc_type        VARCHAR(40) NOT NULL CHECK (doc_type IN ('RCCM','NIF','NINA','ID_CARD','OTHER')),
    file_name       VARCHAR(255) NOT NULL,
    content_type    VARCHAR(120),
    size_bytes      BIGINT NOT NULL DEFAULT 0,
    storage_key     VARCHAR(500) NOT NULL,
    uploaded_by     BIGINT REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS distributor_registration_payments (
    id              BIGSERIAL PRIMARY KEY,
    distributor_id  BIGINT NOT NULL REFERENCES distributor_accounts(id) ON DELETE CASCADE,
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    payment_method  VARCHAR(30) NOT NULL CHECK (
        payment_method IN ('CASH','BANK_TRANSFER','MOBILE_MONEY','OTHER')
    ),
    reference       VARCHAR(64),
    note            TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
                    CHECK (status IN ('PENDING','CONFIRMED','REJECTED')),
    confirmed_by    VARCHAR(100),
    confirmed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS operation_types (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) NOT NULL UNIQUE,
    label           VARCHAR(100) NOT NULL,
    description     TEXT,
    -- Classe PrimeIcons, ex. pi pi-arrow-down
    icon            VARCHAR(80) NOT NULL DEFAULT 'pi pi-bolt',
    -- DEBIT = consomme le solde UV, CREDIT = alimente le solde, NONE = informatif
    balance_effect  VARCHAR(20) NOT NULL DEFAULT 'DEBIT'
                    CHECK (balance_effect IN ('DEBIT','CREDIT','NONE')),
    -- Commission totale: PERCENT (% du montant) ou FIXED (montant XOF)
    commission_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT'
                    CHECK (commission_mode IN ('PERCENT','FIXED')),
    commission_value NUMERIC(18,4) NOT NULL DEFAULT 1.50,
    admin_share_percent NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    distributor_share_percent NUMERIC(5,2) NOT NULL DEFAULT 60.00,
    CHECK (
        admin_share_percent >= 0
        AND distributor_share_percent >= 0
        AND admin_share_percent + distributor_share_percent = 100
    ),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    cancellable     BOOLEAN NOT NULL DEFAULT TRUE,
    -- Saisie transaction : téléphone client / montant exigés selon le type
    requires_phone  BOOLEAN NOT NULL DEFAULT TRUE,
    requires_amount BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

-- Commission propre à un couple type d'opération + opérateur.
-- Absent = le type d'opération fournit le mode, la valeur et les parts.
CREATE TABLE IF NOT EXISTS operation_operator_commissions (
    id              BIGSERIAL PRIMARY KEY,
    operation_type_id BIGINT NOT NULL REFERENCES operation_types(id) ON DELETE CASCADE,
    operator_id     BIGINT NOT NULL REFERENCES operators(id),
    commission_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT'
                    CHECK (commission_mode IN ('PERCENT','FIXED')),
    commission_value NUMERIC(18,4) NOT NULL DEFAULT 1.50,
    admin_share_percent NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    distributor_share_percent NUMERIC(5,2) NOT NULL DEFAULT 60.00,
    CHECK (
        admin_share_percent >= 0
        AND distributor_share_percent >= 0
        AND admin_share_percent + distributor_share_percent = 100
    ),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    UNIQUE (operation_type_id, operator_id)
);

-- Barème par palier de montant, opérateur et type d'opération.
-- BASE_THEN_SPLIT : base = MIN(MAX(montant × rate_percent, commission_min), commission_max).
-- DIRECT_ON_AMOUNT : chaque taux s'applique au montant de la transaction.
CREATE TABLE IF NOT EXISTS commission_rules (
    id                  BIGSERIAL PRIMARY KEY,
    operation_type_id   BIGINT NOT NULL REFERENCES operation_types(id) ON DELETE CASCADE,
    operator_id         BIGINT NOT NULL REFERENCES operators(id),
    amount_min          NUMERIC(18,2) NOT NULL DEFAULT 0,
    amount_max          NUMERIC(18,2),
    calculation_mode    VARCHAR(30) NOT NULL
                        CHECK (calculation_mode IN ('BASE_THEN_SPLIT', 'DIRECT_ON_AMOUNT')),
    rate_percent        NUMERIC(18,4),
    commission_min      NUMERIC(18,2),
    commission_max      NUMERIC(18,2),
    distributor_rate    NUMERIC(18,4) NOT NULL,
    admin_rate          NUMERIC(18,4) NOT NULL,
    operator_rate       NUMERIC(18,4),
    valid_from          DATE,
    valid_to            DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    priority            INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CHECK (amount_min >= 0),
    CHECK (amount_max IS NULL OR amount_max >= amount_min),
    CHECK (commission_min IS NULL OR commission_min >= 0),
    CHECK (commission_max IS NULL OR commission_max >= 0),
    CHECK (distributor_rate >= 0 AND admin_rate >= 0),
    CHECK (operator_rate IS NULL OR operator_rate >= 0),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup
    ON commission_rules (operation_type_id, operator_id, active);

CREATE TABLE IF NOT EXISTS cancellation_reasons (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40) NOT NULL UNIQUE,
    label       VARCHAR(150) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS gateways (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(120) NOT NULL,
    operator            VARCHAR(30) NOT NULL,
    phone_number        VARCHAR(30),
    status              VARCHAR(30) NOT NULL CHECK (status IN ('ONLINE','OFFLINE','BUSY','MAINTENANCE','DISABLED')),
    load_score          INT NOT NULL DEFAULT 0,
    battery_level       INT NOT NULL DEFAULT 0,
    network_strength    INT NOT NULL DEFAULT 0,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    memory_free_mb      BIGINT,
    storage_free_mb     BIGINT,
    temperature         DOUBLE PRECISION,
    internet_available  BOOLEAN NOT NULL DEFAULT FALSE,
    last_heartbeat_at   TIMESTAMPTZ,
    last_idle_at        TIMESTAMPTZ,
    api_key_hash        VARCHAR(255),
    ussd_pin            VARCHAR(32),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS gateway_status (
    id                  BIGSERIAL PRIMARY KEY,
    gateway_id          BIGINT NOT NULL REFERENCES gateways(id) ON DELETE CASCADE,
    battery_level       INT,
    network_strength    INT,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    memory_free_mb      BIGINT,
    storage_free_mb     BIGINT,
    temperature         DOUBLE PRECISION,
    internet_available  BOOLEAN,
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS gateway_logs (
    id              BIGSERIAL PRIMARY KEY,
    gateway_id      BIGINT NOT NULL REFERENCES gateways(id) ON DELETE CASCADE,
    event_type      VARCHAR(50) NOT NULL,
    message         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS distributor_uv_purchases (
    id              BIGSERIAL PRIMARY KEY,
    distributor_id  BIGINT NOT NULL REFERENCES distributor_accounts(id),
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    payment_method  VARCHAR(30) NOT NULL CHECK (payment_method IN ('CASH','GATEWAY_DEPOSIT')),
    gateway_id      BIGINT REFERENCES gateways(id),
    reference       VARCHAR(64),
    note            TEXT,
    balance_after   NUMERIC(18,2) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS commission_payouts (
    id              BIGSERIAL PRIMARY KEY,
    distributor_id  BIGINT NOT NULL REFERENCES distributor_accounts(id),
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    payment_method  VARCHAR(30) NOT NULL CHECK (
        payment_method IN ('CASH','BANK_TRANSFER','MOBILE_MONEY','OTHER')
    ),
    reference       VARCHAR(64),
    note            TEXT,
    unpaid_after    NUMERIC(18,2) NOT NULL,
    paid_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_commission_payouts_distributor
    ON commission_payouts(distributor_id, paid_at DESC);

CREATE TABLE IF NOT EXISTS transactions (
    id                  BIGSERIAL PRIMARY KEY,
    reference           VARCHAR(64) NOT NULL UNIQUE,
    user_id             BIGINT REFERENCES users(id),
    distributor_id      BIGINT REFERENCES distributor_accounts(id),
    gateway_id          BIGINT REFERENCES gateways(id),
    operator            VARCHAR(30) NOT NULL,
    type                VARCHAR(30) NOT NULL,
    beneficiary_phone   VARCHAR(30),
    amount              NUMERIC(18,2) NOT NULL,
    commission          NUMERIC(18,2),
    admin_commission    NUMERIC(18,2),
    distributor_commission NUMERIC(18,2),
    operator_commission NUMERIC(18,2),
    status              VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','QUEUED','ASSIGNED','PROCESSING','WAITING_SMS_CONFIRMATION','SUCCESS','FAILED','CANCELLED','TIMEOUT')),
    priority            VARCHAR(20) NOT NULL CHECK (priority IN ('URGENT','NORMAL','BASSE')),
    ussd_response       TEXT,
    confirmation_sms    TEXT,
    confirmation_received_at TIMESTAMPTZ,
    orange_transaction_id VARCHAR(80),
    confirmation_source VARCHAR(20),
    parsed_amount       NUMERIC(18,2),
    parsed_phone        VARCHAR(30),
    screenshot_url      VARCHAR(500),
    duration_ms         BIGINT,
    cancellation_reason_id BIGINT REFERENCES cancellation_reasons(id),
    cancellation_note   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS transaction_history (
    id              BIGSERIAL PRIMARY KEY,
    transaction_id  BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    from_status     VARCHAR(30) NOT NULL,
    to_status       VARCHAR(30) NOT NULL,
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS sms (
    id              BIGSERIAL PRIMARY KEY,
    recipient       VARCHAR(30) NOT NULL,
    content         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','QUEUED','SENDING','SENT','DELIVERED','FAILED','SCHEDULED','RECEIVED')),
    priority        VARCHAR(20) NOT NULL CHECK (priority IN ('URGENT','NORMAL','BASSE')),
    gateway_id      BIGINT REFERENCES gateways(id),
    scheduled_at    TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS sms_history (
    id              BIGSERIAL PRIMARY KEY,
    sms_id          BIGINT NOT NULL REFERENCES sms(id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL,
    details         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ussd_templates (
    id                  BIGSERIAL PRIMARY KEY,
    operator_id         BIGINT NOT NULL REFERENCES operators(id),
    transaction_type    VARCHAR(50) NOT NULL,
    name                VARCHAR(150) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(100),
    UNIQUE (operator_id, transaction_type, name)
);

CREATE TABLE IF NOT EXISTS ussd_steps (
    id                  BIGSERIAL PRIMARY KEY,
    template_id         BIGINT NOT NULL REFERENCES ussd_templates(id) ON DELETE CASCADE,
    step_order          INT NOT NULL,
    action              VARCHAR(30) NOT NULL CHECK (action IN ('COMPOSE','READ','REPLY','WAIT','CONTINUE','VALIDATE','EXTRACT','WAIT_SMS','RUN_TEMPLATE','VERIFY_BALANCE')),
    expression          TEXT,
    expected_pattern    VARCHAR(255),
    extract_var         VARCHAR(100),
    wait_millis         INT,
    UNIQUE (template_id, step_order)
);

CREATE TABLE IF NOT EXISTS notifications (
    id              BIGSERIAL PRIMARY KEY,
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message         TEXT NOT NULL,
    user_id         BIGINT REFERENCES users(id),
    gateway_id      BIGINT REFERENCES gateways(id),
    read_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    severity        VARCHAR(30),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS device_push_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           TEXT NOT NULL,
    platform        VARCHAR(20) NOT NULL DEFAULT 'ANDROID',
    app             VARCHAR(30) NOT NULL,
    gateway_id      BIGINT REFERENCES gateways(id) ON DELETE SET NULL,
    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_device_push_platform CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT chk_device_push_app CHECK (app IN ('DISTRIBUTOR', 'GATEWAY')),
    CONSTRAINT uq_device_push_token UNIQUE (token)
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,
    actor_name      VARCHAR(100),
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100),
    resource_id     VARCHAR(100),
    details         TEXT,
    ip_address      VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS settings (
    id              BIGSERIAL PRIMARY KEY,
    key             VARCHAR(100) NOT NULL UNIQUE,
    value           TEXT NOT NULL,
    description     VARCHAR(255),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scheduler_jobs (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    status          VARCHAR(30) NOT NULL,
    details         TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS auto_reply_rules (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    match_pattern   VARCHAR(255) NOT NULL,
    reply_content   TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_gateways_operator_status ON gateways(operator, status);
CREATE INDEX IF NOT EXISTS idx_gateways_heartbeat ON gateways(last_heartbeat_at);
CREATE INDEX IF NOT EXISTS idx_gateway_status_gateway ON gateway_status(gateway_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_operator ON transactions(operator);
CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sms_status ON sms(status);
CREATE INDEX IF NOT EXISTS idx_sms_recipient ON sms(recipient);
CREATE INDEX IF NOT EXISTS idx_ussd_templates_op_type ON ussd_templates(operator_id, transaction_type);
CREATE INDEX IF NOT EXISTS idx_ussd_steps_template ON ussd_steps(template_id, step_order);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_device_push_tokens_user ON device_push_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs(action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_distributor_user ON distributor_accounts(user_id);
