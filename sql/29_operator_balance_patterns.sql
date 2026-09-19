-- Motifs regex extraction solde gateway, paramétrables par opérateur.

CREATE TABLE IF NOT EXISTS operator_balance_patterns (
    id              BIGSERIAL PRIMARY KEY,
    operator_id     BIGINT NOT NULL REFERENCES operators(id) ON DELETE CASCADE,
    field_type      VARCHAR(30) NOT NULL,
    regex_pattern   VARCHAR(500) NOT NULL,
    priority        INT NOT NULL DEFAULT 10,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_operator_balance_patterns_op
    ON operator_balance_patterns (operator_id, field_type, priority);

COMMENT ON TABLE operator_balance_patterns IS 'Regex extraction solde MM gateway (Principal, Bonus UV) par opérateur';
COMMENT ON COLUMN operator_balance_patterns.field_type IS 'PRINCIPAL | BONUS_UV';
COMMENT ON COLUMN operator_balance_patterns.regex_pattern IS 'Regex Java/Kotlin — groupe 1 = montant';

-- Orange Mali (dialogue USSD + SMS identiques)
DELETE FROM operator_balance_patterns
WHERE operator_id IN (SELECT id FROM operators WHERE UPPER(code) = 'ORANGE');

INSERT INTO operator_balance_patterns (operator_id, field_type, regex_pattern, priority, description, created_by)
SELECT o.id, 'PRINCIPAL',
       '(?i)(?:(?:le\s+)?solde\s+de\s+votre\s+)?principal\s*:?\s*([0-9][0-9\s.,\u00A0]*)',
       10, 'Ligne Principal (dialogue ou SMS)', 'migration'
FROM operators o WHERE UPPER(o.code) = 'ORANGE';

INSERT INTO operator_balance_patterns (operator_id, field_type, regex_pattern, priority, description, created_by)
SELECT o.id, 'BONUS_UV',
       '(?i)bonus\s*uv\s*:?\s*([0-9][0-9\s.,\u00A0]*)',
       10, 'Ligne Bonus UV', 'migration'
FROM operators o WHERE UPPER(o.code) = 'ORANGE';
