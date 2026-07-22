# Virtual Bank System — API Contracts

> **Status: FROZEN as of <date>.** Any change to this file requires agreement from both team members and a PR updating this document *before* the implementation changes.
> Source of truth: Ejada internship spec (Yomna El-Soufy). Deviations from the spec are marked with ⚠️.

---

## 0. Global conventions

| Concern | Convention |
|---|---|
| Java / Spring | Java 21, Spring Boot 3.x (same version in every service; ⚠️ deviates from spec's Java 11, approved by mentor) |
| Base path style | No `/api/v1` prefix — paths exactly as below (gateway does external mapping) |
| IDs | UUID v4 strings, generated server-side |
| Money | `BigDecimal` in code, JSON number with 2 decimal places. Currency implicit (single currency) |
| Timestamps | ISO-8601 UTC, e.g. `2025-07-15T07:16:49.822Z` |
| Content type | `application/json` for all request/response bodies |
| Auth header (internal) | Requests forwarded by the gateway carry `APP-NAME: PORTAL \| MOBILE` |

### Service ports (local dev)

| Service | Port |
|---|---|
| user-service | 8081 |
| account-service | 8082 |
| transaction-service | 8083 |
| bff-service | 8084 |
| logging-service | 8085 (no public endpoints; Kafka consumer only) |

### Standard error envelope (all services, all errors)

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Human-readable description."
}
```

`error` values by status: `400 → "Bad Request"`, `401 → "Unauthorized"`, `404 → "Not Found"`, `409 → "Conflict"`, `500 → "Internal Server Error"`.

---

## 1. User Service (owner: Friend)

### POST /users/register
Registers a new user. Password stored **hashed (BCrypt)** — never plaintext.

Request:
```json
{
  "username": "john.doe",
  "password": "securePassword123",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

Responses:
- `201 Created`
```json
{
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "username": "john.doe",
  "message": "User registered successfully."
}
```
- `409 Conflict` — username or email already exists (standard error envelope).

### POST /users/login
Authenticates a user (credential check only; token issuance is the gateway's job).

Request:
```json
{ "username": "john.doe", "password": "securePassword123" }
```

Responses:
- `200 OK`
```json
{ "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef", "username": "john.doe" }
```
- `401 Unauthorized` — invalid username or password.

### GET /users/{userId}/profile
Returns basic profile details.

Responses:
- `200 OK`
```json
{
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```
- `404 Not Found` — user does not exist.

---

## 2. Account Service (owner: Loay)

Account fields: `accountId` (UUID), `accountNumber` (10-digit string, unique), `accountType` (`SAVINGS` | `CHECKING` | `SYSTEM`), `balance` (decimal ≥ 0), `status` (`ACTIVE` | `INACTIVE`), `userId`, `lastTransactionAt` (timestamp, used by the stale-account job).

### POST /accounts
Creates a new account for a user.

Request:
```json
{
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "accountType": "SAVINGS",
  "initialBalance": 100.00
}
```

Responses:
- `201 Created`
```json
{
  "accountId": "f1e2d3c4-b5a6-9876-5432-10fedcba9876",
  "accountNumber": "1234567890",
  "message": "Account created successfully."
}
```
- `400 Bad Request` — invalid account type or negative initial balance.

### GET /accounts/{accountId}
Responses:
- `200 OK`
```json
{
  "accountId": "f1e2d3c4-b5a6-9876-5432-10fedcba9876",
  "accountNumber": "1234567890",
  "accountType": "SAVINGS",
  "balance": 100.00,
  "status": "ACTIVE"
}
```
- `404 Not Found`.

### GET /users/{userId}/accounts
Lists all accounts for a user.

Responses:
- `200 OK` — JSON array of account objects (same shape as GET /accounts/{accountId}).
- `404 Not Found` — no accounts for this user.
    - ⚠️ Spec says 404; we follow it. (Arguably `200 []` is cleaner — revisit only with mentor approval.)

### PUT /accounts/transfer
Atomically debits `fromAccountId` and credits `toAccountId`. **Single DB transaction (`@Transactional`)** — both updates commit or neither does. Updates `lastTransactionAt` on both accounts.

Request:
```json
{
  "fromAccountId": "f1e2d3c4-b5a6-9876-5432-10fedcba9876",
  "toAccountId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "amount": 100.00
}
```
(⚠️ Spec's example JSON has a typo — missing opening quote on `fromAccountId` and a trailing comma. Shape above is the corrected, canonical one.)

Responses:
- `200 OK`
```json
{ "message": "Account updated successfully." }
```
- `400 Bad Request` — unknown account ID, `amount <= 0`, insufficient funds, or account not `ACTIVE`.

### Scheduled job: inactivate stale accounts
- Runs **every 1 hour** inside account-service.
- Rule: `status = ACTIVE` AND `lastTransactionAt < now() - 24h` → set `status = INACTIVE`.
- No HTTP contract; internal only.

---

## 3. Transaction Service (owner: Loay)

Transaction fields: `transactionId` (UUID), `fromAccountId`, `toAccountId`, `amount` (decimal), `description` (string), `status` (`INITIATED` | `SUCCESS` | `FAILED`), `timestamp`.

### POST /transactions/transfer/initiation
Validates accounts + funds (via account-service reads), inserts row with status `INITIATED`. **No money moves.**

Request:
```json
{
  "fromAccountId": "f1e2d3c4-b5a6-9876-5432-10fedcba9876",
  "toAccountId": "g7h8i9j0-k1l2-3456-7890-abcdef123456",
  "amount": 30.00,
  "description": "Transfer to checking account"
}
```

Responses:
- `200 OK`
```json
{
  "transactionId": "t1r2a3n4-s5a6-7890-1234-567890abcdef",
  "status": "Initiated",
  "timestamp": "2025-07-15T07:16:49.822Z"
}
```
- `400 Bad Request` — invalid account IDs or insufficient funds.

### POST /transactions/transfer/execution
Looks up the `INITIATED` transaction, calls account-service `PUT /accounts/transfer`, then updates the row to `SUCCESS` (transfer OK) or `FAILED` (transfer rejected/errored).

Request:
```json
{ "transactionId": "t1r2a3n4-s5a6-7890-1234-567890abcdef" }
```

Responses:
- `200 OK`
```json
{
  "transactionId": "t1r2a3n4-s5a6-7890-1234-567890abcdef",
  "status": "Success",
  "timestamp": "2025-07-15T07:16:49.822Z"
}
```
- `400 Bad Request` — unknown transactionId, transaction not in `INITIATED` state, invalid accounts, or insufficient funds at execution time.

### GET /accounts/{accountId}/transactions
⚠️ **Lives in transaction-service** despite the `/accounts/...` path (the spec defines it this way; the BFF calls transaction-service:8083 for it).

Responses:
- `200 OK` — array, newest first:
```json
[
  {
    "transactionId": "t1r2a3n4-s5a6-7890-1234-567890abcdef",
    "fromAccountId": "f1e2d3c4-b5a6-9876-5432-10fedcba9876",
    "toAccountId": "f2e3d3c4-b5a6-9876-5432-10fedbba9876",
    "amount": 50.00,
    "description": "Cash deposit",
    "timestamp": "2025-06-30T10:05:00Z",
    "deliveryStatus": "SENT"
  }
]
```
- `404 Not Found` — no transactions for this account.

---

## 4. BFF Service (owner: Friend)

### GET /bff/dashboard/{userId}
Aggregation. Internal calls (WebClient):
1. `GET user-service:8081/users/{userId}/profile`
2. `GET account-service:8082/users/{userId}/accounts`
3. For each account (in parallel): `GET transaction-service:8083/accounts/{accountId}/transactions`

Responses:
- `200 OK`
```json
{
  "userId": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "accounts": [
    {
      "accountId": "f1e2d3c4-b5a6-9876-5432-10fedcba9876",
      "accountNumber": "1234567890",
      "accountType": "SAVINGS",
      "balance": 120.00,
      "transactions": [
        {
          "transactionId": "t1r2a3n4-s5a6-7890-1234-567890abcdef",
          "amount": 50.00,
          "toAccountId": "g7h8i9j0-k1l2-3456-7890-abcdef123456",
          "description": "Cash deposit",
          "timestamp": "2025-06-30T10:05:00Z"
        }
      ]
    }
  ]
}
```
- Account with no transactions → `"transactions": []` (BFF converts downstream 404 into an empty list).
- `404 Not Found` — user does not exist.
- `500 Internal Server Error` — a downstream service failed.

---

## 5. Kafka logging contract (producer: everyone; consumer: Friend)

- **Topic:** `logging` (single topic, all services).
- Every microservice produces **two messages per handled request**: one `Request`, one `Response`, sent just before returning the response.

Message format (JSON string value):
```json
{
  "message": "<escaped JSON request or response body>",
  "messageType": "Request",
  "dateTime": "2025-07-15T07:16:49.822Z"
}
```

- `messageType` ∈ `"Request" | "Response"` (exact casing).
- logging-service consumes, parses, and inserts into dump table:
  `logs(id BIGSERIAL PK, message TEXT, message_type VARCHAR(10), date_time TIMESTAMP, created_at TIMESTAMP DEFAULT now())`

---

## 6. Gateway route map (owners: both, week 4)

| API | External resource | Backend endpoint |
|---|---|---|
| Register | `POST /register` | user-service `POST /users/register` |
| Login | `POST /login` | user-service `POST /users/login` |
| Dashboard | `GET /dashboard/{userId}` | bff `GET /bff/dashboard/{userId}` |
| Transactions | `POST /initiation`, `POST /execution` | transaction-service `POST /transactions/transfer/initiation`, `.../execution` |
| **vbank** (API product) | `/vbank/*` | bundles Login, Dashboard, Transactions |

- Security: OAuth2 + API key on all APIs.
- Applications: `vbank portal`, `vbank mobile`.
- Gateway injects header `APP-NAME: PORTAL | MOBILE` on every forwarded request.

---

## 7. Work plan — ordered steps per person

### Both together (Day 1–2)
1. Create the GitHub monorepo (`user-service/`, `account-service/`, `transaction-service/`, `bff-service/`, `logging-service/`, `docker-compose.yml`, `contracts.md`, `decisions.md`).
2. Agree on and commit this contract; protect `main`; agree PR review rules.
3. Generate the 5 Spring Boot 3.x (Java 21) skeletons from start.spring.io with agreed package naming (`com.vbank.<service>`).
4. Set up the shared GitHub Projects board with one card per endpoint/feature below.

### Loay — the money path (in order)
1. **docker-compose infra**: Postgres (one instance, schema per service) — Kafka/Zookeeper added later in step 7.
2. **Account Service — CRUD**: entity + repository + `POST /accounts`, `GET /accounts/{accountId}`, `GET /users/{userId}/accounts`. Test with Postman.
3. **Account Service — transfer**: `PUT /accounts/transfer` with `@Transactional` atomicity, insufficient-funds and ACTIVE-status validation, `lastTransactionAt` update.
4. **Transaction Service — initiation**: entity + `POST /transactions/transfer/initiation` (validates via account-service reads, inserts `INITIATED`).
5. **Transaction Service — execution**: `POST /transactions/transfer/execution` calling account-service transfer, updating to `SUCCESS`/`FAILED`.
6. **Transaction Service — history**: `GET /accounts/{accountId}/transactions`, newest first.
7. **Kafka in docker-compose** + create `logging` topic.
8. **Kafka producers** in account-service and transaction-service (Request + Response messages per handled call).
9. **Stale-account scheduled job** (`@Scheduled`, hourly, 24h rule).
10. **Hardening**: edge cases vs. this contract, Postman collection entries for all money-path requests.

### Friend — the user-facing path (in order)
1. **User Service — register**: entity + BCrypt hashing + `POST /users/register` with 409 handling. Test with Postman.
2. **User Service — login + profile**: `POST /users/login` (401 on bad credentials), `GET /users/{userId}/profile`.
3. **BFF — skeleton + profile passthrough**: `GET /bff/dashboard/{userId}` calling user-service only.
4. **BFF — accounts aggregation**: add account-service call, merge into response (Loay's step 2 is ready by now).
5. **BFF — transactions fan-out**: parallel per-account calls to transaction-service (WebClient or RestClient + virtual threads); downstream 404 → `"transactions": []`.
6. **BFF — error handling**: user 404 passthrough, downstream failure → 500 envelope.
7. **Logging Service**: `@KafkaListener` consumer on `logging` topic + dump table insert (schema in §5).
8. **Kafka producers** in user-service and bff-service.
9. **Hardening**: Postman collection entries for register/login/dashboard, edge cases vs. this contract.

### Both together (Week 4)
1. Install WSO2 API Manager; walk through Publisher → Dev Portal → Key Manager once with a hello-world API.
2. Create the 4 APIs + `vbank` product per the route map in §6.
3. Configure OAuth2 + API key security; create `vbank portal` and `vbank mobile` applications.
4. Add mediation policy injecting `APP-NAME` header; configure throttling tiers.
5. Full end-to-end run of the demo script through the gateway; fix gaps.
6. README, architecture notes, demo rehearsal.

**Sync points:** end of every week, run the full shared Postman collection together against docker-compose. Daily 10–15 min standup. Blockers never live longer than one day.

---

## 8. Topics to know (study checklist)

### Both
- **REST API design**: status codes (200/201/400/401/404/409/500), request/response DTOs, validation.
- **Spring Boot 3.x fundamentals**: controller → service → repository layering, `@RestController`, `@Service`, dependency injection, `application.yml` profiles, Jakarta namespace (not `javax`).
- **Spring Data JPA**: entities, repositories, derived query methods, `@Transactional` semantics.
- **Java 21 features worth using**: records for DTOs, switch expressions, (optional) virtual threads.
- **Kafka basics**: topics, producers, consumers, consumer groups, why async logging beats synchronous DB writes; `spring-kafka` (`KafkaTemplate`, `@KafkaListener`).
- **Docker Compose**: services, ports, volumes, health checks; running Postgres + Kafka locally.
- **Postman**: collections, environment variables, chaining requests (e.g. save `transactionId` from initiation into execution).
- **WSO2 API Manager concepts**: Publisher vs Developer Portal vs Key Manager, API vs API Product, OAuth2 client-credentials flow, API keys, throttling tiers, mediation/policy for header injection.
- **Git workflow**: feature branches, small PRs, reviews, protected main.

### Loay-specific
- **Database transactions and atomicity**: why debit+credit must be one transaction; isolation basics; race conditions on concurrent transfers (optimistic vs pessimistic locking — at minimum know the problem exists).
- **Two-phase transfer / saga-lite pattern**: state machine `INITIATED → SUCCESS | FAILED`, idempotency of the execution call.
- **Spring `@Scheduled`**: fixedRate vs cron, and how the hourly stale-account query works.
- **Inter-service HTTP calls from a service** (transaction → account): RestClient/WebClient, timeout and error mapping.

### Friend-specific
- **Password security**: BCrypt (why hashing + salt, never plaintext, never reversible), Spring Security's `PasswordEncoder` (used standalone, no full Spring Security needed).
- **BFF pattern**: why it exists, aggregation vs orchestration, shaping responses for the frontend.
- **Concurrent fan-out calls**: `WebClient` + `Mono.zip`/`Flux.merge`, or `RestClient` + virtual threads / `CompletableFuture.allOf` — pick one and understand its error handling.
- **Kafka consumer details**: deserialization, error handling in `@KafkaListener`, what happens when the consumer is down (offset catch-up).

---

## 9. Repository structure

```
vbank/                                  # monorepo root
├── contracts.md                        # this file — frozen API contracts
├── decisions.md                        # log of agreed decisions/deviations
├── README.md                           # setup + run instructions + demo script
├── docker-compose.yml                  # postgres, kafka, zookeeper (+ services later)
├── .github/
│   └── workflows/
│       └── build.yml                   # optional CI: build all services on PR
├── postman/
│   └── vbank.postman_collection.json   # shared regression collection
│
├── user-service/                       # :8081 (Friend)
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/vbank/user/
│       │   │   ├── UserServiceApplication.java
│       │   │   ├── controller/
│       │   │   │   └── UserController.java
│       │   │   ├── service/
│       │   │   │   └── UserService.java
│       │   │   ├── repository/
│       │   │   │   └── UserRepository.java
│       │   │   ├── model/
│       │   │   │   └── User.java               # JPA entity
│       │   │   ├── dto/
│       │   │   │   ├── RegisterRequest.java    # records
│       │   │   │   ├── RegisterResponse.java
│       │   │   │   ├── LoginRequest.java
│       │   │   │   ├── LoginResponse.java
│       │   │   │   └── ProfileResponse.java
│       │   │   ├── exception/
│       │   │   │   ├── GlobalExceptionHandler.java  # @RestControllerAdvice → §0 error envelope
│       │   │   │   └── NotFoundException.java       # + ConflictException, etc.
│       │   │   ├── config/
│       │   │   │   └── PasswordEncoderConfig.java   # BCrypt bean
│       │   │   └── logging/
│       │   │       └── KafkaLogProducer.java        # §5 producer (same class in every service)
│       │   └── resources/
│       │       └── application.yml     # port, datasource, kafka bootstrap
│       └── test/
│           └── java/com/vbank/user/    # unit tests per layer
│
├── account-service/                    # :8082 (Loay) — same layout, com.vbank.account
│   └── src/main/java/com/vbank/account/
│       ├── controller/ service/ repository/ model/ dto/ exception/ logging/
│       └── job/
│           └── StaleAccountJob.java    # @Scheduled hourly inactivation
│
├── transaction-service/                # :8083 (Loay) — same layout, com.vbank.transaction
│   └── src/main/java/com/vbank/transaction/
│       ├── controller/ service/ repository/ model/ dto/ exception/ logging/
│       └── client/
│           └── AccountClient.java      # RestClient wrapper for account-service calls
│
├── bff-service/                        # :8084 (Friend) — same layout, com.vbank.bff
│   └── src/main/java/com/vbank/bff/
│       ├── controller/ service/ dto/ exception/ logging/   # no repository/model — BFF has no DB
│       └── client/
│           ├── UserClient.java
│           ├── AccountClient.java
│           └── TransactionClient.java  # fan-out lives in service layer using these
│
└── logging-service/                    # :8085 (Friend) — same layout, com.vbank.logging
    └── src/main/java/com/vbank/logging/
        ├── consumer/
        │   └── LogConsumer.java        # @KafkaListener on "logging" topic
        ├── model/
        │   └── LogEntry.java           # dump table entity (§5 schema)
        ├── repository/
        │   └── LogRepository.java
        └── dto/
            └── LogMessage.java         # record matching §5 message format
```

Conventions:
- Every service follows the same `controller / service / repository / model / dto / exception / logging` package layout — user-service is shown fully as the template; the rest only show what differs.
- **DTOs are Java 21 records**, one per request/response shape in this contract.
- **`GlobalExceptionHandler`** in each service maps exceptions to the §0 error envelope — copy the same class everywhere so error responses stay identical.
- **`KafkaLogProducer`** is intentionally duplicated per service (a shared library module is overkill for a 1-month project — note it in `decisions.md`).
- BFF has **no database** (no model/repository); logging-service has **no controller** (no public HTTP endpoints).
- `application.yml` per service holds its port (§0 table), datasource, and `spring.kafka.bootstrap-servers`.

---

## 10. Database design

**Principle: database-per-service.** One Postgres container (docker-compose), four logical databases inside it. A service only ever touches its own database; cross-service data access goes through REST APIs. **No foreign keys across databases** — references like `accounts.user_id` are plain UUID values, validated via API calls, not FK constraints. BFF has no database.

| Database | Owner service |
|---|---|
| `vbank_users` | user-service |
| `vbank_accounts` | account-service |
| `vbank_transactions` | transaction-service |
| `vbank_logs` | logging-service |

### `vbank_users.users`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `user_id` | UUID | PK | generated server-side |
| `username` | VARCHAR(50) | UNIQUE, NOT NULL | 409 on duplicate |
| `email` | VARCHAR(255) | UNIQUE, NOT NULL | 409 on duplicate |
| `password_hash` | VARCHAR(60) | NOT NULL | BCrypt output is exactly 60 chars; never store plaintext |
| `first_name` | VARCHAR(100) | NOT NULL | |
| `last_name` | VARCHAR(100) | NOT NULL | |
| `created_at` | TIMESTAMP | NOT NULL DEFAULT now() | |

### `vbank_accounts.accounts`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `account_id` | UUID | PK | |
| `user_id` | UUID | NOT NULL | logical reference only — no FK (different DB) |
| `account_number` | VARCHAR(10) | UNIQUE, NOT NULL | 10-digit, generated |
| `account_type` | VARCHAR(10) | NOT NULL, CHECK in (SAVINGS, CHECKING, SYSTEM) | |
| `balance` | NUMERIC(19,2) | NOT NULL, CHECK >= 0 | **never FLOAT/DOUBLE** — `BigDecimal` in Java |
| `status` | VARCHAR(10) | NOT NULL DEFAULT 'ACTIVE', CHECK in (ACTIVE, INACTIVE) | |
| `last_transaction_at` | TIMESTAMP | NULL | updated by every transfer; drives stale job |
| `created_at` | TIMESTAMP | NOT NULL DEFAULT now() | |

Indexes: `idx_accounts_user_id (user_id)` for `GET /users/{userId}/accounts`; `idx_accounts_stale (status, last_transaction_at)` for the hourly job.

### `vbank_transactions.transactions`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `transaction_id` | UUID | PK | |
| `from_account_id` | UUID | NOT NULL | logical reference to accounts |
| `to_account_id` | UUID | NOT NULL | logical reference to accounts |
| `amount` | NUMERIC(19,2) | NOT NULL, CHECK > 0 | |
| `description` | VARCHAR(255) | NULL | |
| `status` | VARCHAR(10) | NOT NULL, CHECK in (INITIATED, SUCCESS, FAILED) | the two-phase state machine |
| `timestamp` | TIMESTAMP | NOT NULL DEFAULT now() | |

Indexes: `idx_tx_from (from_account_id)`, `idx_tx_to (to_account_id)` — history query is `WHERE from_account_id = ? OR to_account_id = ? ORDER BY timestamp DESC`.

⚠️ The spec's history response example includes a `deliveryStatus` field (`SENT`/`DELIVERED`) that is never defined anywhere else in the document. Options: (a) add a `delivery_status VARCHAR(10)` column defaulting to `SENT`, or (b) drop the field. **Ask the mentor; record the answer in decisions.md.**

### `vbank_logs.logs` (dump table)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | high-volume append-only → sequence, not UUID |
| `message` | TEXT | NOT NULL | escaped request/response JSON |
| `message_type` | VARCHAR(10) | NOT NULL, CHECK in (Request, Response) | |
| `date_time` | TIMESTAMP | NOT NULL | when the producer generated the log |
| `created_at` | TIMESTAMP | NOT NULL DEFAULT now() | when the consumer inserted it |

### Operational notes

- **Schema management**: `spring.jpa.hibernate.ddl-auto=update` is acceptable for this project's scope (note it in decisions.md); switch to `validate` + an `init.sql` if the mentor prefers explicit DDL.
- **docker-compose**: one `postgres:16` container with an init script creating the four databases; each service's `application.yml` points to its own database URL.
- **Concurrency on transfers**: the debit+credit in account-service runs in one `@Transactional` method; Postgres row locks on the two `UPDATE`s prevent lost updates. Lock accounts in a **consistent order (e.g. by accountId)** to avoid deadlocks when two opposite transfers run concurrently.
- Money is `NUMERIC(19,2)` / `BigDecimal` everywhere. Floating point for money is a bug, full stop.

---



| Date | Change | Agreed by |
|---|---|---|
| <date> | Initial version from spec | Loay + <friend> |