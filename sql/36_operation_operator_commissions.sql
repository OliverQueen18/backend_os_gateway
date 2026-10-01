-- Commission paramétrable par type d'opération ET par opérateur.
-- Sans ligne, la transaction utilise le mode / la valeur / les parts du type.

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
