-- Retrait client : le float revient sur la SIM, le solde UV du distributeur est crédité.
-- Les bases déjà seedées ont RETRAIT en DEBIT (même valeur que le dépôt).

UPDATE operation_types
SET balance_effect = 'CREDIT',
    updated_at = NOW()
WHERE UPPER(code) = 'RETRAIT'
  AND balance_effect IS DISTINCT FROM 'CREDIT';
