-- Extra RBAC permissions + org settings (idempotent)
INSERT INTO permissions (code, description) VALUES
    ('ROLES_WRITE', 'Gérer les rôles et permissions'),
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
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'GATEWAYS_READ','TX_READ','TX_WRITE','SMS_SEND','REPORTS_READ','ALERTS_WRITE'
)
WHERE r.name = 'OPERATOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('DISTRIBUTORS_READ')
WHERE r.name = 'DISTRIBUTEUR'
ON CONFLICT DO NOTHING;

INSERT INTO settings (key, value, description) VALUES
    ('org.name', 'OS Gateway', 'Organisation name'),
    ('org.timezone', 'Africa/Bamako', 'Default timezone'),
    ('org.currency', 'XOF', 'Default currency'),
    ('alerts.email.enabled', 'true', 'Enable email alerts'),
    ('realtime.enabled', 'true', 'Enable realtime polling')
ON CONFLICT (key) DO NOTHING;
