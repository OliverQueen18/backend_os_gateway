-- DEPOT Orange : après composition, Orange demande de ressaisir le numéro client.
-- Sans REPLY {{phone}}, le dialogue se ferme trop tôt → solde inchangé → FAILED.

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
        -- Écran : « Veuillez vérifier et entrer une dernière fois le numéro du client… »
        (tpl_id, 2, 'WAIT', NULL,
         'numero|numéro|telephone|téléphone|client|verifier|vérifier|confirmer|derniere|dernière|incorrect|insuffisant|solde',
         NULL, 25000),
        -- Ressaisie obligatoire du numéro client
        (tpl_id, 3, 'REPLY', '{{phone}}', NULL, NULL, 25000),
        -- Attendre confirmation / résultat
        (tpl_id, 4, 'WAIT', NULL,
         'OK|Annuler|confirm|succes|succès|reussi|réussi|envoye|envoyé|depot|dépôt|transfert|insuffisant|solde|effectu',
         NULL, 25000),
        (tpl_id, 5, 'CONTINUE', 'OK',
         'OK|Fermer|Continuer|Annuler|envoye|envoyé|effectu|succes|succès',
         NULL, 20000);

    RAISE NOTICE 'DEPOT template id=% → COMPOSE + REPLY phone + CONTINUE (pas de WAIT_SMS)', tpl_id;
END $$;
