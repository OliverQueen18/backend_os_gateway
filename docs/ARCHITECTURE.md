# Architecture — OS Gateway Backend

## Vue d'ensemble

OS Gateway orchestre des **gateways Android** pour exécuter des opérations Mobile Money (Orange, Moov, Malitel) via **USSD** et **SMS**. Le backend est découpé en microservices Spring Boot, derrière un API Gateway, avec PostgreSQL (état), Redis (rate-limit/cache) et RabbitMQ (jobs prioritaires).

## Diagramme de contexte

```mermaid
flowchart LR
  Client[Web / Mobile Admin]
  Android[Android Gateways]
  NG[Nginx]
  AG[API Gateway :8080]
  Auth[Auth]
  User[User]
  GW[Gateway]
  USSD[USSD]
  SMS[SMS]
  TX[Transaction]
  Notif[Notification]
  Audit[Audit]
  Report[Reporting]
  Sched[Scheduler]
  Mon[Monitoring]
  PG[(PostgreSQL)]
  Redis[(Redis)]
  MQ[[RabbitMQ]]

  Client --> NG --> AG
  Android --> AG
  AG --> Auth & User & GW & USSD & SMS & TX & Notif & Audit & Report & Sched & Mon
  Sched -->|Feign select| GW
  Sched -->|Feign status| TX
  TX -->|publish| MQ
  SMS -->|consume| MQ
  Sched -->|USSD/SMS jobs| MQ
  Auth & User & GW & USSD & SMS & TX & Notif & Audit & Report & Sched & Mon --> PG
  AG --> Redis
```

## Flux transactionnel

```mermaid
sequenceDiagram
  participant C as Client
  participant AG as API Gateway
  participant TX as Transaction Service
  participant MQ as RabbitMQ
  participant S as Scheduler
  participant G as Gateway Service
  participant U as USSD Service
  participant A as Android Gateway

  C->>AG: POST /api/v1/transactions
  AG->>TX: create (PENDING→QUEUED)
  TX->>MQ: TRANSACTION_QUEUE (priority)
  S->>TX: poll QUEUED
  S->>G: GET /gateways/select?operator=
  G-->>S: best ONLINE gateway
  S->>TX: status ASSIGNED
  S->>MQ: USSD_QUEUE + SMS_QUEUE
  A->>G: heartbeat (30s)
  A->>U: resolve template + run scenario
  A->>TX: PROCESSING → SUCCESS/FAILED
```

## Sélection de gateway

Critères (dans l'ordre) :

1. Opérateur demandé
2. Statut `ONLINE`
3. Batterie > 20%
4. Internet disponible
5. Charge (`loadScore`) la plus faible
6. Meilleure force réseau
7. Idle le plus long (`lastIdleAt`)

## Messaging

| Queue | Routing key | Priorités |
|-------|-------------|-----------|
| `osgateway.transaction.queue` | `transaction.job` | URGENT=10, NORMAL=5, BASSE=1 |
| `osgateway.sms.queue` | `sms.job` | idem |
| `osgateway.notification.queue` | `notification.job` | idem |
| `osgateway.ussd.queue` | `ussd.job` | idem |

## Architecture hexagonale (par service)

Chaque microservice suit :

- `api` — Controllers + DTOs OpenAPI
- `application` — Use cases / services
- `domain` — Entités JPA
- `infrastructure` — Repositories, Feign, RabbitMQ, WebSocket
- `config` — Beans Spring

## Sécurité

- JWT access (court) + refresh (persisté / révocable)
- Validation JWT au niveau API Gateway
- RBAC : `ADMIN`, `SUPERVISOR`, `DISTRIBUTEUR`, `OPERATOR`, `GATEWAY`
- HMAC / AES utilitaires dans `common` pour signatures device et secrets
- Rate limiting Redis (fenêtre fixe / IP)

## Observabilité

- Actuator + Prometheus scrape (`monitoring-service`, etc.)
- Grafana pour dashboards
- Audit logs pour actions critiques
