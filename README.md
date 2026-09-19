# OS Gateway Backend

Production-ready multi-module Spring Boot backend for **OS Gateway** — a Mobile Money platform orchestrating Android gateways over USSD/SMS.

## Stack

- Java 21, Spring Boot 3.3.5, Maven multi-module
- PostgreSQL, Redis, RabbitMQ
- JWT (access + refresh), OpenAPI/Swagger
- Spring Cloud Gateway, OpenFeign
- Docker Compose, Nginx, Prometheus, Grafana

## Modules

| Module | Port | Role |
|--------|------|------|
| api-gateway | 8080 | Edge routing, CORS, JWT, rate-limit |
| auth-service | 8081 | Login / refresh / logout / register |
| user-service | 8082 | Users, roles, distributors |
| gateway-service | 8083 | Android gateway registry + heartbeat + selection |
| ussd-service | 8084 | Operators, USSD templates & scenario engine |
| sms-service | 8085 | SMS send/bulk/schedule + RabbitMQ consumer |
| transaction-service | 8086 | Mobile money transactions workflow |
| notification-service | 8087 | Alerts, FCM push, WebSocket/SSE |
| audit-service | 8088 | Audit append + search |
| reporting-service | 8089 | Reports + PDF/Excel export stubs |
| scheduler-service | 8090 | Poll queued TX, assign gateway, dispatch jobs |
| monitoring-service | 8091 | Dashboard stats + Prometheus metrics |
| common | — | Shared DTOs, JWT, crypto, enums, messaging |

## Quick start

```bash
# Infrastructure
docker compose up -d postgres redis rabbitmq

# Build
mvn -DskipTests package

# Run a service (example)
java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
```

Full stack:

```bash
mvn -DskipTests package
docker compose up -d --build
```

API entry: `http://localhost/api/` (Nginx) or `http://localhost:8080/api/`

Default admin: `admin` / `Admin@123`

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [API overview](docs/API.md)
- [UML](docs/UML.md)
- [Installation](docs/INSTALLATION.md)

## License

Proprietary — OS Gateway.
