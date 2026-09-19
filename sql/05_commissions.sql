-- Migration: commissions par type d'opération + parts admin/distributeur sur transactions
-- Idempotent pour environnements déjà initialisés.

ALTER TABLE operation_types
    ADD COLUMN IF NOT EXISTS commission_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS commission_value NUMERIC(18,4) NOT NULL DEFAULT 1.50,
    ADD COLUMN IF NOT EXISTS admin_share_percent NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    ADD COLUMN IF NOT EXISTS distributor_share_percent NUMERIC(5,2) NOT NULL DEFAULT 60.00;

ALTER TABLE operation_types DROP CONSTRAINT IF EXISTS operation_types_commission_mode_check;
ALTER TABLE operation_types
    ADD CONSTRAINT operation_types_commission_mode_check
    CHECK (commission_mode IN ('PERCENT', 'FIXED'));

ALTER TABLE operation_types DROP CONSTRAINT IF EXISTS operation_types_commission_shares_check;
ALTER TABLE operation_types
    ADD CONSTRAINT operation_types_commission_shares_check
    CHECK (
        admin_share_percent >= 0
        AND distributor_share_percent >= 0
        AND admin_share_percent + distributor_share_percent = 100
    );

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS admin_commission NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS distributor_commission NUMERIC(18,2);

-- Backfill parts for existing SUCCESS rows (approx 40/60) when missing
UPDATE transactions
SET
    admin_commission = ROUND(COALESCE(commission, 0) * 0.40, 2),
    distributor_commission = ROUND(COALESCE(commission, 0) * 0.60, 2)
WHERE admin_commission IS NULL OR distributor_commission IS NULL;
