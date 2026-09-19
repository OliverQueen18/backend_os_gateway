-- PIN d’autorisation des transactions distributeur (stocké en hash BCrypt).
ALTER TABLE distributor_accounts
    ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(255);
