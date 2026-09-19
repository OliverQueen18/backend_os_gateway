# Installation — OS Gateway Backend

## Prérequis

- JDK 21+
- Maven 3.9+
- Docker & Docker Compose
- (Optionnel) Make / Git

## 1. Cloner et construire

```bash
cd backend_os_gateway
mvn -DskipTests package
```

## 2. Infrastructure seule

```bash
docker compose up -d postgres redis rabbitmq
```

- PostgreSQL: `localhost:5432` / `osgateway` / `osgateway` / DB `os_gateway`
- Redis: `localhost:6379`
- RabbitMQ UI: `http://localhost:15672` (guest/guest)

Les scripts `sql/01_schema.sql` et `sql/02_seed.sql` sont montés au premier démarrage Postgres.

## 3. Variables d'environnement

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/os_gateway` | JDBC |
| `DB_USER` / `DB_PASSWORD` | `osgateway` | Credentials |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `RABBITMQ_HOST` | `localhost` | AMQP |
| `JWT_SECRET` | (dev key ≥32 chars) | HMAC JWT secret |
| `SERVER_PORT` | per-service | Override port |
| `FIREBASE_CREDENTIALS_JSON` | (empty) | Firebase service-account JSON (notification-service FCM) |
| `FIREBASE_CREDENTIALS_PATH` | (empty) | Alternative path to service-account JSON file |

## 4. Lancer les services

Exemple local :

```bash
java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
# ... other services
```

Ou stack complète :

```bash
mvn -DskipTests package
docker compose up -d --build
```

## 5. Vérifications

```bash
curl http://localhost:8080/actuator/health
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"Admin@123\"}"
```

- Grafana: `http://localhost:3000` (admin/admin)
- Prometheus: `http://localhost:9090`
- Nginx: `http://localhost/api/`

## 6. CI

GitHub Actions (`.github/workflows/ci.yml`) exécute `mvn package` puis `mvn test` sur push/PR.

## Notes

- `ddl-auto: validate` — le schéma SQL est la source de vérité.
- Changer `JWT_SECRET` en production.
- Pour les push FCM : appliquer `sql/15_device_push_tokens.sql`, puis définir `FIREBASE_CREDENTIALS_JSON` (ou `_PATH`) sur `notification-service`.
- Activer le bloc SSL commenté dans `docker/nginx/nginx.conf` pour le TLS.
