-- Icônes PrimeIcons pour les types d'opérations
ALTER TABLE operation_types
    ADD COLUMN IF NOT EXISTS icon VARCHAR(80) NOT NULL DEFAULT 'pi pi-bolt';

UPDATE operation_types SET icon = 'pi pi-arrow-down' WHERE code = 'DEPOT' AND (icon IS NULL OR icon = 'pi pi-bolt');
UPDATE operation_types SET icon = 'pi pi-arrow-up' WHERE code = 'RETRAIT' AND (icon IS NULL OR icon = 'pi pi-bolt');
UPDATE operation_types SET icon = 'pi pi-arrows-h' WHERE code = 'TRANSFERT' AND (icon IS NULL OR icon = 'pi pi-bolt');
UPDATE operation_types SET icon = 'pi pi-wallet' WHERE code = 'SOLDE' AND (icon IS NULL OR icon = 'pi pi-bolt');
UPDATE operation_types SET icon = 'pi pi-mobile' WHERE code = 'ACHAT_CREDIT' AND (icon IS NULL OR icon = 'pi pi-bolt');
UPDATE operation_types SET icon = 'pi pi-shop' WHERE code = 'PAIEMENT' AND (icon IS NULL OR icon = 'pi pi-bolt');
UPDATE operation_types SET icon = 'pi pi-plus-circle' WHERE code = 'ACHAT_UV' AND (icon IS NULL OR icon = 'pi pi-bolt');
