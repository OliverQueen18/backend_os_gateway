-- Traduction FR des descriptions rôles / permissions (idempotent)

UPDATE roles SET description = 'Administrateur de la plateforme' WHERE name = 'ADMIN';
UPDATE roles SET description = 'Superviseur des opérations' WHERE name = 'SUPERVISOR';
UPDATE roles SET description = 'Compte distributeur' WHERE name = 'DISTRIBUTEUR';
UPDATE roles SET description = 'Agent opérateur / centre d’appels' WHERE name = 'OPERATOR';
UPDATE roles SET description = 'Rôle appareil gateway Android' WHERE name = 'GATEWAY';

UPDATE permissions SET description = 'Consulter les utilisateurs' WHERE code = 'USERS_READ';
UPDATE permissions SET description = 'Gérer les utilisateurs' WHERE code = 'USERS_WRITE';
UPDATE permissions SET description = 'Gérer les rôles et permissions' WHERE code = 'ROLES_WRITE';
UPDATE permissions SET description = 'Consulter les gateways' WHERE code = 'GATEWAYS_READ';
UPDATE permissions SET description = 'Gérer les gateways' WHERE code = 'GATEWAYS_WRITE';
UPDATE permissions SET description = 'Consulter les transactions' WHERE code = 'TX_READ';
UPDATE permissions SET description = 'Créer / modifier les transactions' WHERE code = 'TX_WRITE';
UPDATE permissions SET description = 'Envoyer des SMS' WHERE code = 'SMS_SEND';
UPDATE permissions SET description = 'Consulter les rapports' WHERE code = 'REPORTS_READ';
UPDATE permissions SET description = 'Consulter le journal d’audit' WHERE code = 'AUDIT_READ';
UPDATE permissions SET description = 'Gérer les scénarios USSD et opérateurs' WHERE code = 'USSD_WRITE';
UPDATE permissions SET description = 'Consulter les distributeurs' WHERE code = 'DISTRIBUTORS_READ';
UPDATE permissions SET description = 'Gérer les distributeurs' WHERE code = 'DISTRIBUTORS_WRITE';
UPDATE permissions SET description = 'Gérer les paramètres' WHERE code = 'SETTINGS_WRITE';
UPDATE permissions SET description = 'Gérer les alertes' WHERE code = 'ALERTS_WRITE';
