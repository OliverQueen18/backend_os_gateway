-- Purge des données de démonstration (seed) sans toucher aux types d'opération.
-- Idempotent. Conserver : roles, permissions, admin, settings, operation_types.

DELETE FROM ussd_steps
WHERE template_id IN (
    SELECT id FROM ussd_templates
    WHERE created_by = 'seed'
       OR name IN (
            'Orange Depot Standard',
            'Orange Retrait Standard',
            'Orange Depot Menu + confirmation tél.'
       )
);

DELETE FROM ussd_templates
WHERE created_by = 'seed'
   OR name IN (
        'Orange Depot Standard',
        'Orange Retrait Standard',
        'Orange Depot Menu + confirmation tél.'
   );

UPDATE transactions SET gateway_id = NULL
WHERE gateway_id IN (
    SELECT id FROM gateways
    WHERE created_by = 'seed'
       OR device_id IN ('GW-ORANGE-01','GW-ORANGE-02','GW-MOOV-01','GW-MALITEL-01')
);
UPDATE sms SET gateway_id = NULL
WHERE gateway_id IN (
    SELECT id FROM gateways
    WHERE created_by = 'seed'
       OR device_id IN ('GW-ORANGE-01','GW-ORANGE-02','GW-MOOV-01','GW-MALITEL-01')
);
UPDATE notifications SET gateway_id = NULL
WHERE gateway_id IN (
    SELECT id FROM gateways
    WHERE created_by = 'seed'
       OR device_id IN ('GW-ORANGE-01','GW-ORANGE-02','GW-MOOV-01','GW-MALITEL-01')
);

DELETE FROM gateway_logs WHERE gateway_id IN (
    SELECT id FROM gateways
    WHERE created_by = 'seed'
       OR device_id IN ('GW-ORANGE-01','GW-ORANGE-02','GW-MOOV-01','GW-MALITEL-01')
);
DELETE FROM gateway_status WHERE gateway_id IN (
    SELECT id FROM gateways
    WHERE created_by = 'seed'
       OR device_id IN ('GW-ORANGE-01','GW-ORANGE-02','GW-MOOV-01','GW-MALITEL-01')
);

DELETE FROM gateways
WHERE created_by = 'seed'
   OR device_id IN ('GW-ORANGE-01','GW-ORANGE-02','GW-MOOV-01','GW-MALITEL-01');

UPDATE transactions SET distributor_id = NULL
WHERE distributor_id IN (SELECT id FROM distributor_accounts WHERE code IN ('DIST-001','DIST-002'));
UPDATE transactions SET user_id = NULL
WHERE user_id IN (SELECT id FROM users WHERE username IN ('dist1','dist2'));

DELETE FROM distributor_uv_purchases
WHERE distributor_id IN (SELECT id FROM distributor_accounts WHERE code IN ('DIST-001','DIST-002'));
DELETE FROM distributor_attachments
WHERE distributor_id IN (SELECT id FROM distributor_accounts WHERE code IN ('DIST-001','DIST-002'));
DELETE FROM distributor_registration_payments
WHERE distributor_id IN (SELECT id FROM distributor_accounts WHERE code IN ('DIST-001','DIST-002'));
DELETE FROM commission_payouts
WHERE distributor_id IN (SELECT id FROM distributor_accounts WHERE code IN ('DIST-001','DIST-002'));

DELETE FROM distributor_accounts WHERE code IN ('DIST-001','DIST-002');

DELETE FROM user_roles
WHERE user_id IN (SELECT id FROM users WHERE username IN ('dist1','dist2'));
DELETE FROM refresh_tokens
WHERE user_id IN (SELECT id FROM users WHERE username IN ('dist1','dist2'));
DELETE FROM device_push_tokens
WHERE user_id IN (SELECT id FROM users WHERE username IN ('dist1','dist2'));
DELETE FROM notifications
WHERE user_id IN (SELECT id FROM users WHERE username IN ('dist1','dist2'));
DELETE FROM users WHERE username IN ('dist1','dist2');

DELETE FROM auto_reply_rules
WHERE name IN ('Solde request', 'Help');

DELETE FROM operators o
WHERE o.created_by = 'seed'
  AND NOT EXISTS (SELECT 1 FROM ussd_templates t WHERE t.operator_id = o.id)
  AND NOT EXISTS (SELECT 1 FROM gateways g WHERE UPPER(g.operator) = UPPER(o.code));
