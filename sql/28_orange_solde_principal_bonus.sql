-- Solde Orange : dialogue USSD « Principal » + « Bonus UV », même texte en SMS.

DO $$
DECLARE
    tpl_id BIGINT;
BEGIN
    SELECT t.id INTO tpl_id
    FROM ussd_templates t
    JOIN operators o ON o.id = t.operator_id
    WHERE UPPER(o.code) = 'ORANGE'
      AND UPPER(t.transaction_type) = 'SOLDE'
      AND t.active = TRUE
    ORDER BY t.id LIMIT 1;

    IF tpl_id IS NULL THEN
        RAISE NOTICE 'SOLDE template ORANGE not found';
        RETURN;
    END IF;

    DELETE FROM ussd_steps WHERE template_id = tpl_id;

    INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, extract_var, wait_millis)
    VALUES
        (tpl_id, 1, 'COMPOSE', '#145#4*{{pin}}#', NULL, NULL, NULL),
        (tpl_id, 2, 'WAIT', NULL, 'principal|bonus\s*uv|solde', NULL, 25000),
        (tpl_id, 3, 'EXTRACT', NULL, 'principal', 'mm_balance', 15000),
        (tpl_id, 4, 'CONTINUE', 'OK', 'OK|Fermer|Continuer', NULL, 15000),
        (tpl_id, 5, 'WAIT_SMS', NULL, 'principal|bonus\s*uv|solde', 'mm_balance', 60000);

    RAISE NOTICE 'SOLDE template id=% updated (Principal + Bonus UV)', tpl_id;
END $$;
