-- Commission payouts: admin can pay all or part of earned distributor commissions.
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
