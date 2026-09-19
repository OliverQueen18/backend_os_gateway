-- Add / refresh distributor registration terms setting
INSERT INTO settings (key, value, description) VALUES
    ('distributor.registration.terms',
     E'Conditions d''utilisation — Distributeur OS Gateway\n\n1. Le distributeur s''engage à fournir des informations exactes (identité, RCCM, NIF, NINA).\n2. L''inscription n''est active qu''après paiement des frais et validation par l''administrateur.\n3. Le distributeur est responsable de la confidentialité de son PIN et de ses accès.\n4. Toute activité frauduleuse peut entraîner la suspension ou la résiliation du compte.\n5. Les commissions et opérations sont régies par les règles définies par l''opérateur OS Gateway.',
     'Conditions d''utilisation affichées à l''inscription distributeur')
ON CONFLICT (key) DO NOTHING;
