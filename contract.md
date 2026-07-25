# Virtual Bank System — API Contracts

> **Status: FROZEN as of <date>.** Any change to this file requires agreement from both team members and a PR updating this document *before* the implementation changes.
> Source of truth: Ejada internship spec (Yomna El-Soufy). Deviations from the spec are marked with ⚠️.

---

## 0. Global conventions

| Concern | Convention |
|---|---|
| Java / Spring | Java 21, Spring Boot 4.1.0 (same version in every service, inherited from the parent pom) |
| Build | Multi-module Maven — parent `com.vbank:vbank` declares Java/Boot versions once; one module per service |
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

## 1. User Service (owner: Ahmed)

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

## 4. BFF Service (owner: Ahmed)

### BFF Role: Middleware/Passthrough Gateway
As per mentor guidance, the BFF does **not** perform data aggregation or orchestration. It acts as a lightweight HTTP middleware that forwards requests to the underlying microservices.

**Key principle:** The BFF is stateless and does no business logic. It only:
1. Receives a request from the gateway/frontend.
2. Extracts the `APP-NAME` header (set by the external API gateway).
3. Adds the `APP-NAME` header to the forwarded request.
4. Calls the appropriate microservice endpoint using RestClient.
5. Returns the response as-is to the client.

### BFF Endpoints (Passthrough)

| External Request | BFF endpoint | Forwarded to | Notes |
|---|---|---|---|
| `POST /register` | N/A (routes to user-service via gateway) | user-service `POST /users/register` | No BFF involvement; gateway routes directly |
| `POST /login` | N/A | user-service `POST /users/login` | No BFF involvement; gateway routes directly |
| `GET /users/{userId}/profile` | `GET /bff/users/{userId}/profile` | user-service `GET /users/{userId}/profile` | Adds `APP-NAME` header |
| `GET /users/{userId}/accounts` | `GET /bff/users/{userId}/accounts` | account-service `GET /users/{userId}/accounts` | Adds `APP-NAME` header |
| `GET /accounts/{accountId}` | `GET /bff/accounts/{accountId}` | account-service `GET /accounts/{accountId}` | Adds `APP-NAME` header |
| `POST /accounts` | `POST /bff/accounts` | account-service `POST /accounts` | Adds `APP-NAME` header |
| `PUT /accounts/transfer` | `PUT /bff/accounts/transfer` | account-service `PUT /accounts/transfer` | Adds `APP-NAME` header |
| `GET /accounts/{accountId}/transactions` | `GET /bff/accounts/{accountId}/transactions` | transaction-service `GET /accounts/{accountId}/transactions` | Adds `APP-NAME` header |
| `POST /transactions/transfer/initiation` | `POST /bff/transactions/transfer/initiation` | transaction-service `POST /transactions/transfer/initiation` | Adds `APP-NAME` header |
| `POST /transactions/transfer/execution` | `POST /bff/transactions/transfer/execution` | transaction-service `POST /transactions/transfer/execution` | Adds `APP-NAME` header |

### Dashboard / UI Composition
**No BFF aggregation endpoint.** The frontend is responsible for assembling dashboards by making multiple independent API calls:
1. Call `GET /bff/users/{userId}/profile`
2. Call `GET /bff/users/{userId}/accounts`
3. For each account returned, call `GET /bff/accounts/{accountId}/transactions`

The frontend then combines these responses into a dashboard view. This distributes coupling to the client, which is acceptable for a single frontend.

### BFF Implementation Notes
- **No database**, no repository, no entity models.
- **RestClient-based forwarding**: one method per endpoint, each follows the same pattern:
  ```java
  @GetMapping("/users/{userId}/profile")
  public ProfileResponse getUserProfile(@PathVariable String userId,
                                        @RequestHeader("APP-NAME") String appName) {
    return restClient.get()
      .uri(userServiceUrl + "/users/{userId}/profile", userId)
      .header("APP-NAME", appName)
      .retrieve()
      .body(ProfileResponse.class);
  }
  ```
- **Error handling**: If a downstream service returns an error, BFF forwards the error response as-is (same status, same error envelope from §0).
- **No request/response transformation**: Pass through all bodies unmodified.
- **Logging**: Each request/response logged to Kafka (same as other services).

---

## 5. Kafka logging contract (producer: everyone; consumer: Ahmed)

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

| API | External resource | Backend endpoint | Notes |
|---|---|---|---|
| Register | `POST /register` | user-service `POST /users/register` | Direct gateway→user-service |
| Login | `POST /login` | user-service `POST /users/login` | Direct gateway→user-service |
| Profile | `GET /profile/{userId}` | BFF `GET /bff/users/{userId}/profile` | BFF forwards to user-service |
| Accounts | `GET /accounts/{userId}`, `POST /accounts`, `PUT /transfer` | BFF `GET /bff/users/{userId}/accounts`, etc. | BFF forwards to account-service |
| Transactions | `POST /transactions/transfer/initiation`, `POST /transactions/transfer/execution` | BFF `POST /bff/transactions/transfer/initiation`, etc. | BFF forwards to transaction-service |
| **vbank** (API product) | `/vbank/*` | bundles all above endpoints | Wrapped under `/vbank` prefix |

- Security: OAuth2 + API key on all APIs.
- Applications: `vbank portal`, `vbank mobile`.
- Gateway injects header `APP-NAME: PORTAL | MOBILE` on every forwarded request (to BFF or microservices).
- **Dashboard assembly:** No aggregation endpoint. Frontend is responsible for calling multiple endpoints and combining the responses (profile + accounts + transactions per account).

---

## 7. Work plan — ordered steps per person

### Both together (Day 1–2)
1. Create the GitHub monorepo (parent `pom.xml`, one module per service, `docker-compose.yml`, `init/`, `http/`, `contracts.md`).
2. Agree on and commit this contract; protect `main`; agree PR review rules.
3. Generate the 5 Spring Boot 4.1 (Java 21) modules with agreed package naming (`com.vbank.<service>`); each child pom inherits from the parent.
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

### Ahmed — the user-facing path (in order)
1. **User Service — register**: entity + BCrypt hashing + `POST /users/register` with 409 handling. Test with Postman.
2. **User Service — login + profile**: `POST /users/login` (401 on bad credentials), `GET /users/{userId}/profile`.
3. **BFF — Map the Network & HTTP Engine**: Put target service URLs in `application.yml` and configure a `RestClient` setup file.
4. **BFF — Front Desk Controllers**: Create endpoints (e.g. `/bff/users/{userId}/profile`) that match what the frontend expects.
5. **BFF — Proxy Logic (Delivery Driver)**: Use the `RestClient` inside your controllers to forward the request to the target service and return the response exactly as-is, ensuring downstream errors (404, 500) are passed cleanly.
6. **Logging Service**: `@KafkaListener` consumer on `logging` topic + dump table insert (schema in §5).
7. **Kafka producers** in user-service and bff-service.
8. **Hardening**: Postman collection entries for register/login and routed endpoints, edge cases vs. this contract.

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
- **Spring Boot 4.1 fundamentals**: controller → service → repository layering, `@RestController`, `@Service`, dependency injection, `application.yml`, Jakarta namespace (not `javax`). ⚠️ Boot 4 renamed starters — tutorials showing `spring-boot-starter-web` mean `spring-boot-starter-webmvc` here.
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

### Ahmed-specific
- **Password security**: BCrypt (why hashing + salt, never plaintext, never reversible), Spring Security's `PasswordEncoder` (used standalone, no full Spring Security needed).
- **BFF as a Gateway (RestClient approach)**: Abstracting URLs in `application.yml`, configuring `RestClient` beans, and building proxy controllers to act as a pure "delivery driver" without data aggregation.
- **Kafka consumer details**: deserialization, error handling in `@KafkaListener`, what happens when the consumer is down (offset catch-up).

---

## 9. Repository structure

```
vbank/                                  # monorepo root
├── pom.xml                             # parent aggregator — Java/Boot versions, module list
├── contracts.md                        # this file — frozen API contracts (incl. §11 decisions)
├── README.md                           # setup + run instructions + demo script
├── docker-compose.yml                  # postgres, kafka, zookeeper (+ services later)
├── init/
│   └── create-databases.sql            # creates the four per-service databases
├── .github/
│   └── workflows/
│       └── build.yml                   # optional CI: build all modules on PR
├── http/
│   └── account-service.http            # IntelliJ HTTP Client tests, one file per service
├── postman/
│   └── vbank.postman_collection.json   # shared regression collection
│
├── user-service/                       # :8081 (Ahmed)
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
├── bff-service/                        # :8084 (Ahmed) — same layout, com.vbank.bff
│   └── src/main/java/com/vbank/bff/
│       ├── controller/ service/ dto/ exception/ logging/   # no repository/model — BFF has no DB
│       └── client/
│           ├── UserClient.java
│           ├── AccountClient.java
│           └── TransactionClient.java  # fan-out lives in service layer using these
│
└── logging-service/                    # :8085 (Ahmed) — same layout, com.vbank.logging
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
- **`KafkaLogProducer`** is intentionally duplicated per service (a shared library module is overkill for a 1-month project — see §11).
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
| `created_at` | TIMESTAMPTZ | NOT NULL | |

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

⚠️ The spec's history response example includes a `deliveryStatus` field (`SENT`/`DELIVERED`) that is never defined anywhere else in the document. Options: (a) add a `delivery_status VARCHAR(10)` column defaulting to `SENT`, or (b) drop the field. **Ask the mentor; record the answer in §11.**

### `vbank_logs.logs` (dump table)

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | BIGSERIAL | PK | high-volume append-only → sequence, not UUID |
| `message` | TEXT | NOT NULL | escaped request/response JSON |
| `message_type` | VARCHAR(10) | NOT NULL, CHECK in (Request, Response) | |
| `date_time` | TIMESTAMP | NOT NULL | when the producer generated the log |
| `created_at` | TIMESTAMP | NOT NULL DEFAULT now() | when the consumer inserted it |

### Operational notes

- **Schema management**: `spring.jpa.hibernate.ddl-auto=update` is acceptable for this project's scope (see §11); switch to `validate` + explicit DDL if the mentor prefers.
- **docker-compose**: one `postgres:16` container with an init script creating the four databases; each service's `application.yml` points to its own database URL.
- **Concurrency on transfers**: the debit+credit in account-service runs in one `@Transactional` method; Postgres row locks on the two `UPDATE`s prevent lost updates. Lock accounts in a **consistent order (e.g. by accountId)** to avoid deadlocks when two opposite transfers run concurrently.
- Money is `NUMERIC(19,2)` / `BigDecimal` everywhere. Floating point for money is a bug, full stop.

---

## 11. Technical decisions

Recorded here rather than in a separate file. Anything that deviates from the spec, or that a reader would otherwise question, belongs in this table.

| Decision | Reasoning |
|---|---|
| Java 21 instead of the spec's Java 11 | Mentor-approved. Enables records for DTOs and modern language features. |
| Spring Boot 4.1.0 | Latest at project start. ⚠️ Starters were renamed: `spring-boot-starter-webmvc` (not `-web`), split `*-test` starters. Translate any tutorial written for Boot 3. |
| Multi-module Maven with a parent pom | Java and Boot versions declared once instead of copied into five poms. `mvn clean install` at the root builds everything. |
| Database-per-service in one Postgres container | Preserves the microservice ownership rule (no cross-service table reads) without running four containers. No FK constraints across databases. |
| `ddl-auto: update` | Acceptable at this scope; avoids hand-maintaining DDL for five services in a month. |
| `open-in-view: false` | Spring's default keeps a DB session open for the whole request, hurting performance and hiding lazy-loading bugs. |
| Lombok only on entities (`@Getter`/`@Setter`/`@NoArgsConstructor`) | Removes real boilerplate there. Never `@Data`/`@EqualsAndHashCode`/`@ToString` on entities — they break Hibernate identity semantics. DTOs are records, so Lombok adds nothing. |
| DTOs are records; entity→DTO mapping via a static `from()` factory | One mapping point per DTO instead of a mapping library. |
| One `ApiException` carrying its own `HttpStatus` | Avoids a class per error type; a single `build()` method produces the §0 envelope everywhere. |
| `Instant` fields → `TIMESTAMPTZ` columns | Unambiguous points in time; no timezone confusion when the BFF or scheduled jobs compare timestamps. |
| `KafkaLogProducer` duplicated per service | A shared library module costs more coordination than it saves for a 1-month, 2-person project. |
| No Spring Security starter in user-service | Only BCrypt is needed; the full starter would auto-secure every endpoint and require config to undo. |

---

## Change log

| Date | Change | Agreed by |
|---|---|---|
| 2025-07-24 | Initial version from spec | Loay + Ahmed |
| 2025-07-24 | **Mentor guidance applied:** BFF simplified to passthrough middleware. Removed aggregation, removed dashboard endpoint, frontend responsible for multi-call composition. Updated §4 with explicit endpoint table and RestClient pattern. Updated §6 gateway route map to clarify BFF's transparent role. | Mentor + Loay + Ahmed |