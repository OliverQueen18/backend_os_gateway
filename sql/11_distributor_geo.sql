-- Coordonnées GPS du point de vente distributeur
ALTER TABLE distributor_accounts
    ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

COMMENT ON COLUMN distributor_accounts.latitude IS 'Latitude WGS84 du point de vente';
COMMENT ON COLUMN distributor_accounts.longitude IS 'Longitude WGS84 du point de vente';
