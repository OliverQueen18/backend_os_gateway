#!/usr/bin/env bash
# Déploiement rapide OS Gateway sur Ubuntu (sans Docker)
# Usage (en root) : bash deploy/ubuntu/bootstrap.sh
set -euo pipefail

APP_USER=osgateway
APP_ROOT=/opt/osgateway
WWW_ROOT=/var/www/osgateway/frontend
ENV_FILE=/etc/osgateway/osgateway.env

echo "==> Packages"
apt-get update -y
apt-get install -y openjdk-21-jre-headless nginx postgresql postgresql-contrib redis-server rabbitmq-server certbot python3-certbot-nginx

echo "==> User & dirs"
id "$APP_USER" &>/dev/null || useradd --system --home "$APP_ROOT" --shell /usr/sbin/nologin "$APP_USER"
mkdir -p "$APP_ROOT/apps" /etc/osgateway "$WWW_ROOT" /var/www/certbot
chown -R "$APP_USER:$APP_USER" "$APP_ROOT"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$(dirname "$0")/osgateway.env.example" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "Éditez $ENV_FILE (mots de passe + JWT_SECRET) puis relancez."
fi

echo "==> Postgres DB (si absente)"
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='osgateway'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE USER osgateway WITH PASSWORD 'osgateway';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='os_gateway'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE DATABASE os_gateway OWNER osgateway;"

echo "==> Nginx site"
cp "$(dirname "$0")/nginx-osgateway.conf" /etc/nginx/sites-available/osgateway
ln -sf /etc/nginx/sites-available/osgateway /etc/nginx/sites-enabled/osgateway
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo "==> Systemd template"
cp "$(dirname "$0")/osg@.service" /etc/systemd/system/osg@.service
systemctl daemon-reload

echo ""
echo "Suite manuelle :"
echo "  1. Copier les JAR dans $APP_ROOT/apps/"
echo "  2. Appliquer sql/*.sql sur os_gateway"
echo "  3. Éditer $ENV_FILE"
echo "  4. Copier le build Angular dans $WWW_ROOT/"
echo "  5. systemctl enable --now osg@auth-service osg@user-service osg@gateway-service \\"
echo "       osg@ussd-service osg@sms-service osg@transaction-service \\"
echo "       osg@notification-service osg@api-gateway"
echo "  6. certbot --nginx -d osgateway.olive-services.net"
echo "  7. DNS A → IP de ce serveur"
