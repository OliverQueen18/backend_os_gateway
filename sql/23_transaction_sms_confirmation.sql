-- Confirmation SMS Orange Money (RETRAIT) + statut d'attente SMS.
ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS confirmation_sms TEXT,
    ADD COLUMN IF NOT EXISTS confirmation_received_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS orange_transaction_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS confirmation_source VARCHAR(20),
    ADD COLUMN IF NOT EXISTS parsed_amount NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS parsed_phone VARCHAR(30);

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_status_check
    CHECK (status IN (
        'PENDING','QUEUED','ASSIGNED','PROCESSING','WAITING_SMS_CONFIRMATION',
        'SUCCESS','FAILED','CANCELLED','TIMEOUT'
    ));
