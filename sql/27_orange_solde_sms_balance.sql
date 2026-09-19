-- Solde Orange Money : le montant arrive par SMS après le code USSD (pas à l'écran).

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
        RAISE NOTICE 'SOLDE template ORANGE not found — run 26_orange_solde_template.sql first';
        RETURN;
    END IF;

    DELETE FROM ussd_steps WHERE template_id = tpl_id;

    INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, extract_var, wait_millis)
    VALUES
        (tpl_id, 1, 'COMPOSE', '#145#4*{{pin}}#', NULL, NULL, NULL),
        (tpl_id, 2, 'WAIT_SMS', NULL, 'solde|balance|disponible|compte|fcfa|xof', 'mm_balance', 60000);

    RAISE NOTICE 'SOLDE template id=% updated for SMS balance', tpl_id;
END $$;
