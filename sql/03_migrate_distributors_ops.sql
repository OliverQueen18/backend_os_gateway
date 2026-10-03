-- Migration idempotente pour environnements déjà initialisés
ALTER TABLE distributor_accounts ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
ALTER TABLE distributor_accounts ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);
ALTER TABLE distributor_accounts ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE distributor_accounts ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE distributor_accounts ADD COLUMN IF NOT EXISTS address VARCHAR(500);

CREATE TABLE IF NOT EXISTS operation_types (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30) NOT NULL UNIQUE,
    label           VARCHAR(100) NOT NULL,
    description     TEXT,
    balance_effect  VARCHAR(20) NOT NULL DEFAULT 'DEBIT'
                    CHECK (balance_effect IN ('DEBIT','CREDIT','NONE')),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
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

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS distributor_id BIGINT REFERENCES distributor_accounts(id);

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_type_check;
ALTER TABLE ussd_templates DROP CONSTRAINT IF EXISTS ussd_templates_transaction_type_check;

INSERT INTO operation_types (code, label, description, balance_effect, active, created_by) VALUES
    ('DEPOT', 'Dépôt', 'Dépôt Mobile Money client', 'DEBIT', TRUE, 'migrate'),
    ('RETRAIT', 'Retrait', 'Retrait Mobile Money client', 'CREDIT', TRUE, 'migrate'),
    ('TRANSFERT', 'Transfert', 'Transfert Mobile Money', 'DEBIT', TRUE, 'migrate'),
    ('SOLDE', 'Consultation solde', 'Consultation de solde', 'NONE', TRUE, 'migrate'),
    ('ACHAT_CREDIT', 'Achat crédit', 'Achat de crédit téléphonique', 'DEBIT', TRUE, 'migrate'),
    ('PAIEMENT', 'Paiement marchand', 'Paiement marchand', 'DEBIT', TRUE, 'migrate'),
    ('ACHAT_UV', 'Achat UV', 'Recharge UV distributeur (caisse)', 'CREDIT', TRUE, 'migrate')
ON CONFLICT (code) DO NOTHING;

UPDATE distributor_accounts d
SET first_name = COALESCE(d.first_name, split_part(u.full_name, ' ', 1)),
    last_name = COALESCE(d.last_name, NULLIF(substr(u.full_name, strpos(u.full_name, ' ') + 1), u.full_name)),
    email = COALESCE(d.email, u.email),
    phone = COALESCE(d.phone, u.phone)
FROM users u
WHERE d.user_id = u.id
  AND (d.first_name IS NULL OR d.email IS NULL OR d.phone IS NULL);
