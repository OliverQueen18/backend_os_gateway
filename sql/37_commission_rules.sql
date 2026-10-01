-- Barème de commission par opérateur et type d'opération.
-- BASE_THEN_SPLIT : base = MIN(MAX(montant × taux, commission_min), commission_max),
--   puis répartition (taux distributeur / admin / opérateur appliqués à la base).
-- DIRECT_ON_AMOUNT : chaque taux est appliqué au montant de la transaction (dépôt).
-- Sans ligne active pour le couple opérateur + type + palier, le type d'opération
-- (et éventuellement operation_operator_commissions) reste le repli.

CREATE TABLE IF NOT EXISTS commission_rules (
    id                  BIGSERIAL PRIMARY KEY,
    operation_type_id   BIGINT NOT NULL REFERENCES operation_types(id) ON DELETE CASCADE,
    operator_id         BIGINT NOT NULL REFERENCES operators(id),
    amount_min          NUMERIC(18,2) NOT NULL DEFAULT 0,
    amount_max          NUMERIC(18,2),
    calculation_mode    VARCHAR(30) NOT NULL
                        CHECK (calculation_mode IN ('BASE_THEN_SPLIT', 'DIRECT_ON_AMOUNT')),
    rate_percent        NUMERIC(18,4),
    commission_min      NUMERIC(18,2),
    commission_max      NUMERIC(18,2),
    distributor_rate    NUMERIC(18,4) NOT NULL,
    admin_rate          NUMERIC(18,4) NOT NULL,
    operator_rate       NUMERIC(18,4),
    valid_from          DATE,
    valid_to            DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    priority            INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CHECK (amount_min >= 0),
    CHECK (amount_max IS NULL OR amount_max >= amount_min),
    CHECK (commission_min IS NULL OR commission_min >= 0),
    CHECK (commission_max IS NULL OR commission_max >= 0),
    CHECK (distributor_rate >= 0 AND admin_rate >= 0),
    CHECK (operator_rate IS NULL OR operator_rate >= 0),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup
    ON commission_rules (operation_type_id, operator_id, active);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS operator_commission NUMERIC(18,2);

-- Barème initial éditable (retrait : 1 % plancher 50 plafond 10 000, parts 70/30).
INSERT INTO commission_rules (
    operation_type_id, operator_id, amount_min, amount_max, calculation_mode,
    rate_percent, commission_min, commission_max,
    distributor_rate, admin_rate, active, priority
)
SELECT ot.id, o.id, 0, NULL, 'BASE_THEN_SPLIT',
       1.0000, 50, 10000,
       70.0000, 30.0000, TRUE, 0
FROM operation_types ot
CROSS JOIN operators o
WHERE UPPER(ot.code) = 'RETRAIT'
  AND NOT EXISTS (
      SELECT 1 FROM commission_rules r
      WHERE r.operation_type_id = ot.id AND r.operator_id = o.id
        AND r.calculation_mode = 'BASE_THEN_SPLIT'
  );

-- Dépôt : taux sur le montant (exemple 0,50 % distributeur, 0,10 % admin).
INSERT INTO commission_rules (
    operation_type_id, operator_id, amount_min, amount_max, calculation_mode,
    distributor_rate, admin_rate, active, priority
)
SELECT ot.id, o.id, 0, NULL, 'DIRECT_ON_AMOUNT',
       0.5000, 0.1000, TRUE, 0
FROM operation_types ot
CROSS JOIN operators o
WHERE UPPER(ot.code) = 'DEPOT'
  AND NOT EXISTS (
      SELECT 1 FROM commission_rules r
      WHERE r.operation_type_id = ot.id AND r.operator_id = o.id
        AND r.calculation_mode = 'DIRECT_ON_AMOUNT'
  );
