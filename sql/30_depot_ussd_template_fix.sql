-- Fix template DEPOT Orange : timeouts réalistes, SMS source de vérité, sans VALIDATE après WAIT_SMS.

DO $$
DECLARE
    tpl_id BIGINT;
BEGIN
    SELECT t.id INTO tpl_id
    FROM ussd_templates t
    JOIN operators o ON o.id = t.operator_id
    WHERE UPPER(o.code) = 'ORANGE'
      AND UPPER(t.transaction_type) = 'DEPOT'
      AND t.active = TRUE
    ORDER BY t.id LIMIT 1;

    IF tpl_id IS NULL THEN
        RAISE NOTICE 'DEPOT template ORANGE not found';
        RETURN;
    END IF;

    DELETE FROM ussd_steps WHERE template_id = tpl_id;

    INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, extract_var, wait_millis)
    VALUES
        (tpl_id, 1, 'COMPOSE', '#145#1*{{amount}}*{{phone}}*{{pin}}#', NULL, NULL, NULL),
        (tpl_id, 2, 'WAIT', NULL, 'OK|Annuler|confirm|succes|reussi|envoye|depot|transfert|insuffisant|solde', NULL, 25000),
        (tpl_id, 3, 'CONTINUE', 'OK', 'OK|Fermer|Continuer|Annuler', NULL, 20000),
        (tpl_id, 4, 'WAIT_SMS',
         'echec|échec|echoue|échoué|insuffisant|incorrect|refus|annul|impossible|delais',
         'envoye|envoyé|effectué|effectue|succès|succes|réussi|reussi|dépôt|depot|transfert|confirm',
         NULL, 120000);

    RAISE NOTICE 'DEPOT template id=% fixed (no VALIDATE after WAIT_SMS)', tpl_id;
END $$;
