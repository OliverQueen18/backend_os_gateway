-- Logo opérateur (URL externe ou fichier servi via /api/v1/ussd/operators/{id}/logo)
ALTER TABLE operators
    ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS logo_storage_key VARCHAR(500),
    ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR(120);

COMMENT ON COLUMN operators.logo_url IS 'URL publique du logo (externe ou endpoint API)';
COMMENT ON COLUMN operators.logo_storage_key IS 'Chemin fichier local si logo uploadé';
