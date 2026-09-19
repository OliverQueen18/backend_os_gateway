-- FCM / push device tokens (Android distributor + gateway apps)
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

CREATE INDEX IF NOT EXISTS idx_device_push_tokens_user ON device_push_tokens(user_id);

COMMENT ON TABLE device_push_tokens IS
    'Firebase Cloud Messaging registration tokens linked to authenticated users';
