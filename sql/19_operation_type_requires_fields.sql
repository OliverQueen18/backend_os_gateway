-- Champs saisie transaction optionnels selon le type d'opération.
ALTER TABLE operation_types
    ADD COLUMN IF NOT EXISTS requires_phone BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS requires_amount BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN operation_types.requires_phone IS
    'When true, beneficiary phone is required to create a transaction of this type';
COMMENT ON COLUMN operation_types.requires_amount IS
    'When true, amount is required (> 0) to create a transaction of this type';

-- Consultation solde : ni téléphone client ni montant.
UPDATE operation_types
SET requires_phone = FALSE,
    requires_amount = FALSE
WHERE UPPER(code) = 'SOLDE';
