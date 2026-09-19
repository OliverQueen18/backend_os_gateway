-- Confirmation TX par solde gateway uniquement (plus de WAIT_SMS sur DEPOT/RETRAIT).
-- Process : SOLDE avant (hors bande mobile) → USSD → SOLDE après → delta.

DO $$
DECLARE
    depot_id BIGINT;
    retrait_id BIGINT;
BEGIN
    SELECT t.id INTO depot_id
    FROM ussd_templates t
    JOIN operators o ON o.id = t.operator_id
    WHERE UPPER(o.code) = 'ORANGE'
      AND UPPER(t.transaction_type) = 'DEPOT'
      AND t.active = TRUE
    ORDER BY t.id LIMIT 1;

    IF depot_id IS NOT NULL THEN
        DELETE FROM ussd_steps WHERE template_id = depot_id;
        INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, extract_var, wait_millis)
        VALUES
            (depot_id, 1, 'COMPOSE', '#145#1*{{amount}}*{{phone}}*{{pin}}#', NULL, NULL, NULL),
            (depot_id, 2, 'WAIT', NULL, 'OK|Annuler|confirm|succes|reussi|envoye|depot|transfert|insuffisant|solde', NULL, 25000),
            (depot_id, 3, 'CONTINUE', 'OK', 'OK|Fermer|Continuer|Annuler', NULL, 20000);
        RAISE NOTICE 'DEPOT template id=% → USSD only (balance confirmation hors bande)', depot_id;
    ELSE
        RAISE NOTICE 'DEPOT template ORANGE not found';
    END IF;

    SELECT t.id INTO retrait_id
    FROM ussd_templates t
    JOIN operators o ON o.id = t.operator_id
    WHERE UPPER(o.code) = 'ORANGE'
      AND UPPER(t.transaction_type) = 'RETRAIT'
      AND t.active = TRUE
    ORDER BY t.id ASC
    LIMIT 1;

    IF retrait_id IS NOT NULL THEN
        DELETE FROM ussd_steps WHERE template_id = retrait_id;
        INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, wait_millis)
        VALUES
            (retrait_id, 1, 'COMPOSE', '#145#2*1*{{amount}}*{{phone}}*{{pin}}#', NULL, NULL),
            (retrait_id, 2, 'WAIT', NULL,
             'client|confirmer|secret|retrait|telephone|téléphone|numero|numéro|OK|Annuler|insuffisant|solde', 25000),
            (retrait_id, 3, 'REPLY', '{{phone}}', NULL, 25000),
            (retrait_id, 4, 'CONTINUE', 'OK',
             'retrait|effectu|secret|sms|confirm|OK|Annuler|insuffisant|solde|client', 30000);
        RAISE NOTICE 'RETRAIT template id=% → USSD only (balance confirmation hors bande)', retrait_id;
    ELSE
        RAISE NOTICE 'RETRAIT template ORANGE not found';
    END IF;
END $$;
