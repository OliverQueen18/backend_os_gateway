-- UV solde : passer du débit à la création (montant+commission)
-- au débit uniquement à SUCCESS (montant seul).
-- Annule les débits provisoires encore ouverts pour éviter un double débit après déploiement.

UPDATE distributor_accounts da
SET balance = da.balance + sub.refund,
    updated_at = NOW()
FROM (
    SELECT t.distributor_id,
           SUM(t.amount + COALESCE(t.commission, 0)) AS refund
    FROM transactions t
    JOIN operation_types ot ON UPPER(ot.code) = UPPER(t.type)
    WHERE t.distributor_id IS NOT NULL
      AND t.status NOT IN ('SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT')
      AND UPPER(ot.balance_effect) = 'DEBIT'
    GROUP BY t.distributor_id
) sub
WHERE da.id = sub.distributor_id;

DO $$
DECLARE
    n bigint;
BEGIN
    SELECT COUNT(*) INTO n
    FROM transactions t
    JOIN operation_types ot ON UPPER(ot.code) = UPPER(t.type)
    WHERE t.distributor_id IS NOT NULL
      AND t.status NOT IN ('SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT')
      AND UPPER(ot.balance_effect) = 'DEBIT';
    RAISE NOTICE 'TX ouvertes DEBIT remboursées (provisoire) : %', n;
END $$;
