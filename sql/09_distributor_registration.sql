-- Distributor registration KYC, fee, attachments, approval workflow

ALTER TABLE distributor_accounts
    ADD COLUMN IF NOT EXISTS rccm VARCHAR(50),
    ADD COLUMN IF NOT EXISTS nif VARCHAR(50),
    ADD COLUMN IF NOT EXISTS nina VARCHAR(50),
    ADD COLUMN IF NOT EXISTS registration_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS registration_fee_amount NUMERIC(18,2),
    ADD COLUMN IF NOT EXISTS registration_fee_paid BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS registration_fee_paid_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS registration_fee_payment_ref VARCHAR(64),
    ADD COLUMN IF NOT EXISTS registration_fee_payment_method VARCHAR(30),
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS reviewed_by BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;

UPDATE distributor_accounts
SET registration_status = 'APPROVED',
    registration_fee_paid = TRUE
WHERE registration_status IS NULL
   OR registration_status = 'APPROVED';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_distributor_registration_status'
    ) THEN
        ALTER TABLE distributor_accounts
            ADD CONSTRAINT chk_distributor_registration_status
            CHECK (registration_status IN (
                'FEE_PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED'
            ));
    END IF;
END $$;

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

CREATE INDEX IF NOT EXISTS idx_distributor_attachments_distributor
    ON distributor_attachments(distributor_id, created_at DESC);

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

CREATE INDEX IF NOT EXISTS idx_distributor_reg_payments_distributor
    ON distributor_registration_payments(distributor_id, created_at DESC);

INSERT INTO settings (key, value, description) VALUES
    ('distributor.registration.fee', '25000', 'Frais d''inscription distributeur (XOF)'),
    ('distributor.registration.require_fee', 'true', 'Exiger le paiement des frais avant validation'),
    ('distributor.registration.terms',
     E'Conditions d''utilisation — Distributeur OS Gateway\n\n1. Le distributeur s''engage à fournir des informations exactes (identité, RCCM, NIF, NINA).\n2. L''inscription n''est active qu''après paiement des frais et validation par l''administrateur.\n3. Le distributeur est responsable de la confidentialité de son PIN et de ses accès.\n4. Toute activité frauduleuse peut entraîner la suspension ou la résiliation du compte.\n5. Les commissions et opérations sont régies par les règles définies par l''opérateur OS Gateway.',
     'Conditions d''utilisation affichées à l''inscription distributeur')
ON CONFLICT (key) DO NOTHING;
