-- Template USSD consultation solde Orange Money (lecture solde gateway).

DO $$
DECLARE
    op_id BIGINT;
    tpl_id BIGINT;
BEGIN
    SELECT id INTO op_id FROM operators WHERE UPPER(code) = 'ORANGE' LIMIT 1;
    IF op_id IS NULL THEN
        RAISE NOTICE 'Operator ORANGE not found — skip SOLDE template';
        RETURN;
    END IF;

    SELECT id INTO tpl_id FROM ussd_templates
    WHERE operator_id = op_id AND UPPER(transaction_type) = 'SOLDE' AND active = TRUE
    ORDER BY id LIMIT 1;

    IF tpl_id IS NULL THEN
        INSERT INTO ussd_templates (operator_id, name, transaction_type, description, active, created_by)
        VALUES (op_id, 'Solde Orange Money', 'SOLDE', 'Consultation solde SIM gateway', TRUE, 'migration')
        RETURNING id INTO tpl_id;
    END IF;

    DELETE FROM ussd_steps WHERE template_id = tpl_id;

    INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, extract_var, wait_millis)
    VALUES
        (tpl_id, 1, 'COMPOSE', '#145#4*{{pin}}#', NULL, NULL, NULL),
        (tpl_id, 2, 'WAIT', NULL, 'principal|bonus\s*uv|solde', NULL, 25000),
        (tpl_id, 3, 'EXTRACT', NULL, 'principal', 'mm_balance', 15000),
        (tpl_id, 4, 'CONTINUE', 'OK', 'OK|Fermer|Continuer', NULL, 15000),
        (tpl_id, 5, 'WAIT_SMS', NULL, 'principal|bonus\s*uv|solde', 'mm_balance', 60000);

    RAISE NOTICE 'SOLDE template id=% for ORANGE', tpl_id;
END $$;
