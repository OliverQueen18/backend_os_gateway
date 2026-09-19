-- Code PIN USSD du SIM / Mobile Money du gateway (utilisé dans {{pin}} des templates)
ALTER TABLE gateways
    ADD COLUMN IF NOT EXISTS ussd_pin VARCHAR(32);

COMMENT ON COLUMN gateways.ussd_pin IS
    'PIN Mobile Money / USSD du terminal (clair, substitué dans les templates {{pin}})';
