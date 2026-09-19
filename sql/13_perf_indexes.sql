-- Accélère les agrégats commissions / listing par distributeur
CREATE INDEX IF NOT EXISTS idx_transactions_distributor_status
    ON transactions (distributor_id, status)
    WHERE distributor_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'distributor_attachments'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_distributor_attachments_distributor
            ON distributor_attachments (distributor_id);
    END IF;
END $$;
