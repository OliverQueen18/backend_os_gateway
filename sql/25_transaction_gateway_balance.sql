-- Soldes gateway (SIM Orange Money) avant/après transaction + confirmation par solde.

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS gateway_balance_before NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS gateway_balance_after NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS gateway_balance_delta NUMERIC(18, 2),
    ADD COLUMN IF NOT EXISTS balance_confirmed BOOLEAN,
    ADD COLUMN IF NOT EXISTS balance_confirmation_source VARCHAR(20);

COMMENT ON COLUMN transactions.gateway_balance_before IS 'Solde MM du gateway avant exécution USSD';
COMMENT ON COLUMN transactions.gateway_balance_after IS 'Solde MM du gateway après exécution USSD';
COMMENT ON COLUMN transactions.gateway_balance_delta IS 'after - before (FCFA)';
COMMENT ON COLUMN transactions.balance_confirmed IS 'Delta solde conforme au montant et au sens de la TX';
COMMENT ON COLUMN transactions.balance_confirmation_source IS 'BALANCE quand confirmé par delta solde sans SMS';
