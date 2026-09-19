-- Autorise un modèle USSD à appeler un autre (ex. DÉPÔT → SOLDE).

ALTER TABLE ussd_steps DROP CONSTRAINT IF EXISTS ussd_steps_action_check;

ALTER TABLE ussd_steps ADD CONSTRAINT ussd_steps_action_check
    CHECK (action IN (
        'COMPOSE','READ','REPLY','WAIT','CONTINUE','VALIDATE','EXTRACT','WAIT_SMS',
        'RUN_TEMPLATE','VERIFY_BALANCE'
    ));

COMMENT ON COLUMN ussd_steps.action IS
    'COMPOSE|READ|REPLY|WAIT|CONTINUE|VALIDATE|EXTRACT|WAIT_SMS|RUN_TEMPLATE|VERIFY_BALANCE. RUN_TEMPLATE.expression = type du modèle cible (ex. SOLDE). extract_var = gateway_balance_before|gateway_balance_after.';

-- DÉPÔT Orange : solde avant → process → solde après → vérification
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
        (tpl_id, 1, 'RUN_TEMPLATE', 'SOLDE', NULL, 'gateway_balance_before', NULL),
        (tpl_id, 2, 'COMPOSE', '#145#1*{{amount}}*{{phone}}*{{pin}}#', NULL, NULL, NULL),
        (tpl_id, 3, 'WAIT', NULL, 'OK|Annuler|confirm|succes|reussi|envoye|depot|transfert|insuffisant|solde', NULL, 25000),
        (tpl_id, 4, 'CONTINUE', 'OK', 'OK|Fermer|Continuer|Annuler', NULL, 20000),
        (tpl_id, 5, 'WAIT_SMS',
         'echec|échec|echoue|échoué|insuffisant|incorrect|refus|annul|impossible|delais',
         'envoye|envoyé|effectué|effectue|succès|succes|réussi|reussi|dépôt|depot|transfert|confirm',
         NULL, 120000),
        (tpl_id, 6, 'RUN_TEMPLATE', 'SOLDE', NULL, 'gateway_balance_after', NULL),
        (tpl_id, 7, 'VERIFY_BALANCE', NULL, NULL, NULL, NULL);

    RAISE NOTICE 'DEPOT template id=% now calls SOLDE before/after + VERIFY_BALANCE', tpl_id;
END $$;
