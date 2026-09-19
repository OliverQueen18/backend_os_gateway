# API Overview — OS Gateway

Base URL (via Nginx): `http://localhost/api/v1`  
Base URL (direct): `http://localhost:8080/api/v1`

Auth: `Authorization: Bearer <access_token>` except `/auth/**`.

Swagger UI per service: `http://localhost:<port>/swagger-ui.html`

## Auth (`:8081`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/login` | Username/password → access + refresh |
| POST | `/auth/refresh` | Rotate tokens |
| POST | `/auth/logout` | Revoke refresh token |
| POST | `/auth/register` | Register distributor user |

## Users (`:8082`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/users` | Search + pagination (`q`, `enabled`, `page`, `size`) |
| GET | `/users/{id}` | Get user |
| POST | `/users` | Create user |
| PUT | `/users/{id}` | Update user |
| DELETE | `/users/{id}` | Delete user |
| GET/POST | `/roles` | List / create roles |
| GET/POST/PUT | `/distributors` | Distributor accounts |
| GET | `/distributors/commission-balances` | Earned / paid / unpaid commissions |
| GET | `/distributors/{id}/commission-balance` | Commission balance for one distributor |
| POST/GET | `/distributors/{id}/commission-payouts` | Pay all/part of commission + history |
| POST/GET | `/distributors/{id}/uv-purchases` | UV top-up + history |
| PUT | `/distributors/me/registration` | KYC (RCCM, NIF, NINA) |
| POST/GET | `/distributors/me/attachments` | Upload/list registration documents |
| POST | `/distributors/me/registration-fee` | Pay registration fee |
| GET | `/distributors/registration-fee` | Configured fee amount |
| POST | `/distributors/{id}/approve` | Approve registration |
| POST | `/distributors/{id}/reject` | Reject registration (reason) |

## Gateways (`:8083`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/gateways` | Filter by `operator`, `status` |
| POST | `/gateways` | Register device |
| PUT | `/gateways/{id}` | Update |
| POST | `/gateways/{id}/heartbeat` | Battery, network, GPS, memory, storage, temp, internet |
| GET | `/gateways/select` | Best gateway for operator |
| WS | `/ws/gateways` | Realtime status (STOMP `/topic/gateways`) |

## USSD (`:8084`)

| Method | Path | Description |
|--------|------|-------------|
| GET/POST | `/ussd/operators` | Operators CRUD (list/create) |
| POST | `/ussd/templates` | Template + ordered steps |
| GET | `/ussd/templates/{id}` | Template detail |
| GET | `/ussd/templates?operator=&type=` | Resolve by operator + TX type |
| POST | `/ussd/templates/{id}/run` | Scenario engine with `{{vars}}` |

## SMS (`:8085`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/sms/send` | Send one |
| GET | `/sms/history` | Paginated history |
| POST | `/sms/bulk` | Bulk send |
| POST | `/sms/schedule` | Schedule |
| GET/POST | `/sms/auto-reply` | Auto-reply rules |

## Transactions (`:8086`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/transactions` | Create → QUEUED + RabbitMQ |
| GET | `/transactions` | History filters |
| GET | `/transactions/{id}` | Detail |
| GET | `/transactions/{id}/timeline` | Status history |
| PATCH | `/transactions/{id}/status` | Workflow transition |

Types: `DEPOT`, `RETRAIT`, `TRANSFERT`, `SOLDE`, `ACHAT_CREDIT`, `PAIEMENT`  
Status: `PENDING` → `QUEUED` → `ASSIGNED` → `PROCESSING` → `SUCCESS`/`FAILED`  
Priority: `URGENT` | `NORMAL` | `BASSE`

## Notifications (`:8087`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/notifications/alerts` | Create alert |
| GET | `/notifications` | List |
| GET | `/notifications/stream` | SSE |
| WS | `/ws/notifications` | STOMP topic |

## Audit (`:8088`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/audit` | Append log |
| GET | `/audit` | Search |

## Reports (`:8089`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/reports/transactions?from=&to=` | TX report JSON |
| GET | `/reports/sms?from=&to=` | SMS report |
| GET | `/reports/commissions?from=&to=` | Commissions |
| GET | `/reports/operators` | Operators |
| GET | `/reports/gateways` | Gateways |
| GET | `/reports/export/{report}.{pdf\|xlsx}` | Export stub file |

## Scheduler (`:8090`)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/scheduler/run` | Trigger poll/dispatch now |
| GET | `/scheduler/jobs` | Job history |

## Monitoring (`:8091`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/monitoring/dashboard` | Gateway counts, tx/min, sms/min, alerts |
| GET | `/monitoring/gateways/health` | Health metrics |
| GET | `/actuator/prometheus` | Prometheus scrape |

## Common response envelope

```json
{
  "success": true,
  "message": "OK",
  "data": {},
  "timestamp": "2026-07-26T12:00:00Z"
}
```
