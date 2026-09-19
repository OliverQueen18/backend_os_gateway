-- SOLDE Orange : retirer WAIT_SMS (bloque l'enchaînement vers DEPOT/RETRAIT).
-- Lecture solde via USSD uniquement (COMPOSE → WAIT → EXTRACT → CONTINUE).

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
        (tpl_id, 2, 'WAIT', NULL, 'principal|bonus\s*uv|solde', NULL, 15000),
        (tpl_id, 3, 'EXTRACT', NULL, 'principal', 'mm_balance', 8000),
        (tpl_id, 4, 'CONTINUE', 'OK', 'OK|Fermer|Continuer|Envoyer|Envoye', NULL, 10000);

    RAISE NOTICE 'SOLDE template id=% → USSD only (no WAIT_SMS)', tpl_id;
END $$;
