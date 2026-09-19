-- Motifs d'annulation de transactions + colonnes associées
CREATE TABLE IF NOT EXISTS cancellation_reasons (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(40) NOT NULL UNIQUE,
    label       VARCHAR(150) NOT NULL,
    description TEXT,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

COMMENT ON TABLE cancellation_reasons IS
    'Catalogue des motifs d''annulation de transactions (catégorisation).';

INSERT INTO cancellation_reasons (code, label, description, active)
VALUES
    ('CLIENT_REQUEST', 'Demande client', 'Annulation à la demande du client', TRUE),
    ('ERROR_SAISIE', 'Erreur de saisie', 'Montant, numéro ou type incorrect', TRUE),
    ('GATEWAY_ISSUE', 'Problème gateway', 'Gateway indisponible ou USSD échoué avant exécution', TRUE),
    ('INSUFFICIENT_UV', 'Solde UV insuffisant', 'Solde distributeur insuffisant après création', TRUE),
    ('OTHER', 'Autre', 'Motif libre (préciser en commentaire)', TRUE)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS cancellation_reason_id BIGINT REFERENCES cancellation_reasons(id),
    ADD COLUMN IF NOT EXISTS cancellation_note TEXT;

-- Autoriser le statut CANCELLED
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_status_check;
ALTER TABLE transactions
    ADD CONSTRAINT transactions_status_check
    CHECK (status IN ('PENDING','QUEUED','ASSIGNED','PROCESSING','WAITING_SMS_CONFIRMATION','SUCCESS','FAILED','CANCELLED','TIMEOUT'));
