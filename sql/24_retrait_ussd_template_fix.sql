-- RETRAIT Orange Money : SMS = source de vérité (pas de VALIDATE après WAIT_SMS).
-- Ajoute WAIT + CONTINUE avant attente SMS ; supprime VALIDATE post-SMS.

DO $$
DECLARE
    tpl_id BIGINT;
BEGIN
    SELECT t.id INTO tpl_id
    FROM ussd_templates t
    JOIN operators o ON o.id = t.operator_id
    WHERE UPPER(o.code) = 'ORANGE'
      AND UPPER(t.transaction_type) = 'RETRAIT'
      AND t.active = TRUE
    ORDER BY t.id ASC
    LIMIT 1;

    IF tpl_id IS NULL THEN
        RAISE NOTICE 'No active ORANGE RETRAIT template — skip';
        RETURN;
    END IF;

    DELETE FROM ussd_steps WHERE template_id = tpl_id;

    INSERT INTO ussd_steps (template_id, step_order, action, expression, expected_pattern, wait_millis)
    VALUES
        (tpl_id, 1, 'COMPOSE', '#145#2*1*{{amount}}*{{phone}}*{{pin}}#', NULL, NULL),
        -- Attendre l'écran « client doit confirmer » (réseau Orange lent)
        (tpl_id, 2, 'WAIT', NULL,
         'client|confirmer|secret|retrait|telephone|téléphone|numero|numéro', 25000),
        -- Resaisie téléphone si Orange le demande (sinon ignorée si pas de champ)
        (tpl_id, 3, 'REPLY', '{{phone}}', NULL, 25000),
        -- Fermer le dialogue USSD avant d'attendre le SMS opérateur
        (tpl_id, 4, 'CONTINUE', 'OK',
         'retrait|effectu|secret|sms|confirm|OK|Annuler|insuffisant|solde|client', 30000),
        -- Source de vérité finale : SMS Orange Money corrélé montant + téléphone
        (tpl_id, 5, 'WAIT_SMS',
         'echec|échec|echoue|échoué|insuffisant|incorrect|refus|annul|impossible',
         'retrait.*(effectue|effectué|succes|succès|reussi|réussi)', 120000);

    RAISE NOTICE 'Updated RETRAIT template id=%', tpl_id;
END $$;
