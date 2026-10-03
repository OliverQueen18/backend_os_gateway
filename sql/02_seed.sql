-- Seed data for OS Gateway

-- Password for admin: Admin@123 (bcrypt)

-- Pas de données métier de démo (opérateurs, gateways, distributeurs, modèles USSD).

-- Seuls les types d’opération actifs et le socle (rôles, admin, réglages) sont fournis.



INSERT INTO roles (name, description) VALUES

    ('ADMIN', 'Administrateur de la plateforme'),

    ('SUPERVISOR', 'Superviseur des opérations'),

    ('DISTRIBUTEUR', 'Compte distributeur'),

    ('OPERATOR', 'Agent opérateur / centre d’appels'),

    ('GATEWAY', 'Rôle appareil gateway Android')

ON CONFLICT (name) DO NOTHING;



INSERT INTO permissions (code, description) VALUES

    ('USERS_READ', 'Consulter les utilisateurs'),

    ('USERS_WRITE', 'Gérer les utilisateurs'),

    ('ROLES_WRITE', 'Gérer les rôles et permissions'),

    ('GATEWAYS_READ', 'Consulter les gateways'),

    ('GATEWAYS_WRITE', 'Gérer les gateways'),

    ('TX_READ', 'Consulter les transactions'),

    ('TX_WRITE', 'Créer / modifier les transactions'),

    ('SMS_SEND', 'Envoyer des SMS'),

    ('REPORTS_READ', 'Consulter les rapports'),

    ('AUDIT_READ', 'Consulter le journal d’audit'),

    ('USSD_WRITE', 'Gérer les scénarios USSD et opérateurs'),

    ('DISTRIBUTORS_READ', 'Consulter les distributeurs'),

    ('DISTRIBUTORS_WRITE', 'Gérer les distributeurs'),

    ('SETTINGS_WRITE', 'Gérer les paramètres'),

    ('ALERTS_WRITE', 'Gérer les alertes')

ON CONFLICT (code) DO NOTHING;



INSERT INTO role_permissions (role_id, permission_id)

SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN'

ON CONFLICT DO NOTHING;



INSERT INTO role_permissions (role_id, permission_id)

SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (

    'GATEWAYS_READ','GATEWAYS_WRITE','TX_READ','TX_WRITE','SMS_SEND','REPORTS_READ',

    'DISTRIBUTORS_READ','USSD_WRITE','ALERTS_WRITE','AUDIT_READ'

)

WHERE r.name = 'SUPERVISOR'

ON CONFLICT DO NOTHING;



INSERT INTO role_permissions (role_id, permission_id)

SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('TX_READ','TX_WRITE','SMS_SEND','DISTRIBUTORS_READ')

WHERE r.name = 'DISTRIBUTEUR'

ON CONFLICT DO NOTHING;



INSERT INTO role_permissions (role_id, permission_id)

SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (

    'GATEWAYS_READ','TX_READ','TX_WRITE','SMS_SEND','REPORTS_READ','ALERTS_WRITE'

)

WHERE r.name = 'OPERATOR'

ON CONFLICT DO NOTHING;



INSERT INTO settings (key, value, description) VALUES

    ('org.name', 'OS Gateway', 'Organisation name'),

    ('org.timezone', 'Africa/Bamako', 'Default timezone'),

    ('org.currency', 'XOF', 'Default currency'),

    ('alerts.email.enabled', 'true', 'Enable email alerts'),

    ('realtime.enabled', 'true', 'Enable realtime polling'),

    ('distributor.registration.fee', '25000', 'Frais d''inscription distributeur (XOF)'),

    ('distributor.registration.require_fee', 'true', 'Exiger le paiement des frais avant validation'),

    ('distributor.registration.terms',

     E'Conditions d''utilisation — Distributeur OS Gateway\n\n1. Le distributeur s''engage à fournir des informations exactes (identité, RCCM, NIF, NINA).\n2. L''inscription n''est active qu''après paiement des frais et validation par l''administrateur.\n3. Le distributeur est responsable de la confidentialité de son PIN et de ses accès.\n4. Toute activité frauduleuse peut entraîner la suspension ou la résiliation du compte.\n5. Les commissions et opérations sont régies par les règles définies par l''opérateur OS Gateway.',

     'Conditions d''utilisation affichées à l''inscription distributeur'),

    ('heartbeat.interval.seconds', '30', 'Expected gateway heartbeat interval'),

    ('gateway.min.battery', '20', 'Minimum battery for selection'),

    ('tx.default.commission.rate', '0.015', 'Default commission rate')

ON CONFLICT (key) DO NOTHING;



-- bcrypt hash for Admin@123

INSERT INTO users (username, email, password_hash, full_name, phone, enabled, created_by)

VALUES (

    'admin',

    'admin@osgateway.com',

    '$2a$10$meakAOIVZOSAjv4fXL0P1e9Ffy32rEM0WxbiNb9s4OVKm0aOGNOme',

    'System Administrator',

    '70000001',

    TRUE,

    'seed'

)

ON CONFLICT (username) DO NOTHING;



INSERT INTO user_roles (user_id, role_id)

SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.name = 'ADMIN'

ON CONFLICT DO NOTHING;



INSERT INTO operation_types (

    code, label, description, icon, balance_effect,

    commission_mode, commission_value, admin_share_percent, distributor_share_percent,

    active, requires_phone, requires_amount, created_by

) VALUES

    ('DEPOT', 'Dépôt', 'Dépôt Mobile Money client', 'pi pi-arrow-down', 'DEBIT',

     'PERCENT', 1.50, 40.00, 60.00, TRUE, TRUE, TRUE, 'seed'),

    ('RETRAIT', 'Retrait', 'Retrait Mobile Money client', 'pi pi-arrow-up', 'CREDIT',

     'PERCENT', 1.50, 40.00, 60.00, TRUE, TRUE, TRUE, 'seed'),

    ('TRANSFERT', 'Transfert', 'Transfert Mobile Money', 'pi pi-arrows-h', 'DEBIT',

     'PERCENT', 1.50, 40.00, 60.00, TRUE, TRUE, TRUE, 'seed'),

    ('SOLDE', 'Consultation solde', 'Consultation de solde', 'pi pi-wallet', 'NONE',

     'FIXED', 0, 40.00, 60.00, TRUE, FALSE, FALSE, 'seed'),

    ('ACHAT_CREDIT', 'Achat crédit', 'Achat de crédit téléphonique', 'pi pi-mobile', 'DEBIT',

     'PERCENT', 1.50, 40.00, 60.00, TRUE, TRUE, TRUE, 'seed'),

    ('PAIEMENT', 'Paiement marchand', 'Paiement marchand', 'pi pi-shop', 'DEBIT',

     'PERCENT', 1.50, 40.00, 60.00, TRUE, TRUE, TRUE, 'seed'),

    ('ACHAT_UV', 'Achat UV', 'Recharge UV distributeur (caisse)', 'pi pi-plus-circle', 'CREDIT',

     'FIXED', 0, 40.00, 60.00, TRUE, FALSE, TRUE, 'seed')

ON CONFLICT (code) DO NOTHING;

