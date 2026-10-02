#!/usr/bin/env bash
# Ubuntu : applique les migrations de commission dans postgres-osgateway.
# Le SQL est inclus dans ce fichier. Aucun autre .sql n'est necessaire.
#
#   bash /home/adminubuntu/OliveApps/OSGATEWAY/apply-commission-sql.sh
set -eu

APP_DIR="${APP_DIR:-/home/adminubuntu/OliveApps}"
ENV_FILE="${ENV_FILE:-$APP_DIR/prod.env}"
CONTAINER="${POSTGRES_CONTAINER:-postgres-osgateway}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Fichier d'environnement introuvable : $ENV_FILE" >&2
  exit 1
fi

read_env() {
  key="$1"
  line=$(grep -E "^(export[[:space:]]+)?${key}=" "$ENV_FILE" | tail -n 1 | tr -d '\r' || true)
  value="${line#*=}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  printf '%s' "$value"
}

OSGATEWAY_DB=$(read_env OSGATEWAY_DB)
OSGATEWAY_DB_USER=$(read_env OSGATEWAY_DB_USER)

if [ -z "$OSGATEWAY_DB" ] || [ -z "$OSGATEWAY_DB_USER" ]; then
  echo "OSGATEWAY_DB et OSGATEWAY_DB_USER doivent etre definis dans $ENV_FILE" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "Conteneur Postgres introuvable ou arrete : $CONTAINER" >&2
  exit 1
fi

echo "Application du bareme sur $OSGATEWAY_DB (conteneur $CONTAINER)"

docker exec -i "$CONTAINER" \
  psql -v ON_ERROR_STOP=1 -U "$OSGATEWAY_DB_USER" -d "$OSGATEWAY_DB" <<'SQL'
CREATE TABLE IF NOT EXISTS operation_operator_commissions (
    id              BIGSERIAL PRIMARY KEY,
    operation_type_id BIGINT NOT NULL REFERENCES operation_types(id) ON DELETE CASCADE,
    operator_id     BIGINT NOT NULL REFERENCES operators(id),
    commission_mode VARCHAR(20) NOT NULL DEFAULT 'PERCENT'
                    CHECK (commission_mode IN ('PERCENT','FIXED')),
    commission_value NUMERIC(18,4) NOT NULL DEFAULT 1.50,
    admin_share_percent NUMERIC(5,2) NOT NULL DEFAULT 40.00,
    distributor_share_percent NUMERIC(5,2) NOT NULL DEFAULT 60.00,
    CHECK (
        admin_share_percent >= 0
        AND distributor_share_percent >= 0
        AND admin_share_percent + distributor_share_percent = 100
    ),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ,
    UNIQUE (operation_type_id, operator_id)
);

CREATE TABLE IF NOT EXISTS commission_rules (
    id                  BIGSERIAL PRIMARY KEY,
    operation_type_id   BIGINT NOT NULL REFERENCES operation_types(id) ON DELETE CASCADE,
    operator_id         BIGINT NOT NULL REFERENCES operators(id),
    amount_min          NUMERIC(18,2) NOT NULL DEFAULT 0,
    amount_max          NUMERIC(18,2),
    calculation_mode    VARCHAR(30) NOT NULL
                        CHECK (calculation_mode IN ('BASE_THEN_SPLIT', 'DIRECT_ON_AMOUNT')),
    rate_percent        NUMERIC(18,4),
    commission_min      NUMERIC(18,2),
    commission_max      NUMERIC(18,2),
    distributor_rate    NUMERIC(18,4) NOT NULL,
    admin_rate          NUMERIC(18,4) NOT NULL,
    operator_rate       NUMERIC(18,4),
    valid_from          DATE,
    valid_to            DATE,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    priority            INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ,
    CHECK (amount_min >= 0),
    CHECK (amount_max IS NULL OR amount_max >= amount_min),
    CHECK (commission_min IS NULL OR commission_min >= 0),
    CHECK (commission_max IS NULL OR commission_max >= 0),
    CHECK (distributor_rate >= 0 AND admin_rate >= 0),
    CHECK (operator_rate IS NULL OR operator_rate >= 0),
    CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX IF NOT EXISTS idx_commission_rules_lookup
    ON commission_rules (operation_type_id, operator_id, active);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS operator_commission NUMERIC(18,2);

INSERT INTO commission_rules (
    operation_type_id, operator_id, amount_min, amount_max, calculation_mode,
    rate_percent, commission_min, commission_max,
    distributor_rate, admin_rate, active, priority
)
SELECT ot.id, o.id, 0, NULL, 'BASE_THEN_SPLIT',
       1.0000, 50, 10000,
       70.0000, 30.0000, TRUE, 0
FROM operation_types ot
CROSS JOIN operators o
WHERE UPPER(ot.code) = 'RETRAIT'
  AND NOT EXISTS (
      SELECT 1 FROM commission_rules r
      WHERE r.operation_type_id = ot.id AND r.operator_id = o.id
        AND r.calculation_mode = 'BASE_THEN_SPLIT'
  );

INSERT INTO commission_rules (
    operation_type_id, operator_id, amount_min, amount_max, calculation_mode,
    distributor_rate, admin_rate, active, priority
)
SELECT ot.id, o.id, 0, NULL, 'DIRECT_ON_AMOUNT',
       0.5000, 0.1000, TRUE, 0
FROM operation_types ot
CROSS JOIN operators o
WHERE UPPER(ot.code) = 'DEPOT'
  AND NOT EXISTS (
      SELECT 1 FROM commission_rules r
      WHERE r.operation_type_id = ot.id AND r.operator_id = o.id
        AND r.calculation_mode = 'DIRECT_ON_AMOUNT'
  );

SELECT ot.code AS type, o.code AS operateur, r.calculation_mode, r.rate_percent,
       r.commission_min, r.commission_max, r.distributor_rate, r.admin_rate, r.active
FROM commission_rules r
JOIN operation_types ot ON ot.id = r.operation_type_id
JOIN operators o ON o.id = r.operator_id
ORDER BY ot.code, o.code, r.amount_min;
SQL

echo "Bareme applique."
