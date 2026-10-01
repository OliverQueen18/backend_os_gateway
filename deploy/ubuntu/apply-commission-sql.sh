#!/bin/bash
# Applique les migrations de commission sur la base OS Gateway déjà en service.
# À lancer sur le VPS, depuis /home/adminubuntu/OliveApps :
#   bash OSGATEWAY/apply-commission-sql.sh
# ou, si les .sql sont déjà dans OSGATEWAY/sql :
#   bash apply-commission-sql.sh /home/adminubuntu/OliveApps/OSGATEWAY/sql
set -euo pipefail

APP_DIR="${APP_DIR:-/home/adminubuntu/OliveApps}"
ENV_FILE="${ENV_FILE:-$APP_DIR/prod.env}"
SQL_DIR="${1:-$APP_DIR/OSGATEWAY/sql}"
CONTAINER="${POSTGRES_CONTAINER:-postgres-osgateway}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Fichier d'environnement introuvable : $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [[ -z "${OSGATEWAY_DB:-}" || -z "${OSGATEWAY_DB_USER:-}" ]]; then
  echo "OSGATEWAY_DB et OSGATEWAY_DB_USER doivent être définis dans $ENV_FILE" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "Conteneur Postgres introuvable ou arrêté : $CONTAINER" >&2
  exit 1
fi

apply() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "Fichier SQL introuvable : $file" >&2
    exit 1
  fi
  echo "Application de $(basename "$file")"
  docker exec -i "$CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$OSGATEWAY_DB_USER" -d "$OSGATEWAY_DB" \
    < "$file"
}

apply "$SQL_DIR/36_operation_operator_commissions.sql"
apply "$SQL_DIR/37_commission_rules.sql"

echo "Vérification"
docker exec -i "$CONTAINER" \
  psql -U "$OSGATEWAY_DB_USER" -d "$OSGATEWAY_DB" -c \
  "SELECT ot.code AS type, o.code AS operateur, r.calculation_mode, r.rate_percent,
          r.commission_min, r.commission_max, r.distributor_rate, r.admin_rate, r.active
   FROM commission_rules r
   JOIN operation_types ot ON ot.id = r.operation_type_id
   JOIN operators o ON o.id = r.operator_id
   ORDER BY ot.code, o.code, r.amount_min;"
