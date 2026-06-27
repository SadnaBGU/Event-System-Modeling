# Event System — Ticketing Platform (V3)

A modular event-management and ticketing platform (Ben-Gurion University Software
Engineering Workshop). V3 adds **persistent storage** through an ORM
(Spring Data JPA + Hibernate) over **PostgreSQL**, robustness against external-system
and database failures, and a file-driven **system initialization** mechanism.

> The ORM is the **Data-Access Layer**: the database schema is generated *from* the
> domain entities, not designed independently.

---

## 1. Architecture

The system is a multi-module Maven project layered as required for V3:

| Component | Module / location | Responsibility |
|---|---|---|
| **Client** | `event-system-ui/` | React/Vite front-end (and init scripts) |
| **Communication** | `infrastructure/.../api`, WebSocket/STOMP | REST controllers, real-time notifications, JWT auth interceptor |
| **Application** | `application/` | Use-case services + ports (payment, ticketing, notifications) |
| **Domain** | `domain/` | Aggregates + repository ports (JPA-annotated entities) |
| **ORM (Data-Access Layer)** | `infrastructure/.../persistence/springrepos` | `Postgres*Repository` adapters + `SpringData*Repository` (Hibernate) |
| **Infrastructure** | `infrastructure/` | Spring wiring, security, external HTTP adapters, bootstrap/init |
| **Database** | PostgreSQL | Persistent state (local H2/Docker for dev/test, Google Cloud SQL for deploy) |

Diagrams:
- Database schema (ERD): [docs/diagrams/erd/v3_database_ERD.mmd](docs/diagrams/erd/v3_database_ERD.mmd)
- V3 persistence + init + HTTP adapters: [docs/diagrams/class_diagrams/9_v3_Persistence_And_Init.mmd](docs/diagrams/class_diagrams/9_v3_Persistence_And_Init.mmd)
- Full system overview: [docs/diagrams/class_diagrams/0_v2_System_Overview.mmd](docs/diagrams/class_diagrams/0_v2_System_Overview.mmd)

---

## 2. Prerequisites

- **Java 17** (JDK)
- **Maven 3.9+**
- A reachable **PostgreSQL** database (Google Cloud SQL for deployment; Docker locally)
- For running tests: **Docker** (the test suite uses a throwaway PostgreSQL on `localhost:5434`)

---

## 3. Running the server

All connection details and secrets are supplied via **environment variables** — nothing
is hard-coded in the code or committed to git. The repository ships per-developer
launch scripts (git-ignored) that set those variables and start the server.

### Windows (PowerShell)

```powershell
# one-time, if scripts are blocked:
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

.\start-app.ps1
```

### macOS / Linux

```bash
chmod +x start-app.sh   # one time only
./start-app.sh
```

Both scripts ultimately run:

```bash
mvn spring-boot:run -pl infrastructure
```

> `start-app.ps1` / `start-app.sh` are intentionally **git-ignored** (they contain
> connection settings). Each developer keeps their own copy in the project root.

### Running manually

If you prefer to export the variables yourself, set the ones listed in
[section 4](#4-configuration-file--environment-variables) and
[section 5](#5-startup-modes-and-initial-state-file), then run
`mvn spring-boot:run -pl infrastructure`.

---

## 4. Configuration file & environment variables

Startup parameters live in [infrastructure/src/main/resources/application-main.yml](infrastructure/src/main/resources/application-main.yml)
(activated by `SPRING_PROFILES_ACTIVE=main`). All sensitive / environment-specific
values are injected through environment variables; defaults exist only for the test profile.

### Connecting to PostgreSQL on Google Cloud

The datasource URL is assembled from variables:

```yaml
spring:
  datasource:
    url: ${DB_URL}${DB_IP}:${DB_PORT}/eventsystem_db?sslmode=require
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update     # Hibernate evolves the schema from the entities
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

Set these for a **Google Cloud SQL (PostgreSQL)** instance:

| Variable | Meaning | Example |
|---|---|---|
| `DB_URL` | JDBC prefix | `jdbc:postgresql://` |
| `DB_IP` | Cloud SQL public IP | `34.10.240.178` |
| `DB_PORT` | Port | `5432` |
| `DB_USERNAME` | DB user | `postgres` |
| `DB_PASSWORD` | DB password | `********` |
| `SPRING_PROFILES_ACTIVE` | Active profile | `main` |

> Switching between local (H2 / Docker) and Google Cloud requires **only a
> configuration change** — no code changes. The recommended Cloud SQL setup is the
> cheapest tier (`db-f1-micro`, HDD, 10 GB, backups/HA disabled, region `us-east1`/`us-west1`).

### Admin & platform bootstrap variables

On first startup, or after `EMPTY_DB` / `INIT_FILE` reset, the platform creates a singleton
`Platform` and the required initial system admin:

| Variable | Meaning |
|---|---|
| `ADMIN_USERNAME` | Initial admin username |
| `ADMIN_PASSWORD` | Initial admin password (≥ 8 chars) |
| `ADMIN_FIRST_NAME` / `ADMIN_LAST_NAME` | Admin name |
| `ADMIN_EMAIL` | Admin email |
| `ADMIN_DOB` | Admin date of birth (`yyyy-MM-dd`) |
| `JWT_SECRET` | HMAC-SHA256 signing secret (≥ 32 bytes) |

### External systems

The platform uses the WSEP external payment/ticketing system through infrastructure adapters.

| Variable | Meaning | Example |
|---|---|---|
| `WSEP_BASE_URL` | External payment/ticketing base URL | `https://damp-lynna-wsep-1984852e.koyeb.app/` |
| `VALIDATE_EXTERNAL_SYSTEMS` | Whether startup validates external systems before initialization | `true` |

Other tunables (under `eventsystem.*` in the YAML): `bootstrap.queue-load-threshold`
(how many users may reserve concurrently before the virtual queue engages),
`bootstrap.default-reservation-timeout`, `security.token-validity`, `security.bcrypt-strength`.

A template of all keys is in [.env.example](.env.example).

---

## 5. Startup modes and initial-state file

Startup behavior is selected explicitly using `STARTUP_MODE`.

Supported modes:

| Mode | Behavior |
|---|---|
| `EXISTING_DB` | Keep the current DB state. Bootstrap the required admin/platform baseline only if missing. Does not run an init file. |
| `EMPTY_DB` | Reset persisted DB state, then create the required admin/platform baseline. Does not run an init file. |
| `INIT_FILE` | Reset persisted DB state, create the required admin/platform baseline, then replay the configured initial-state file. |

### Environment variables

Set these in your local `start-app.ps1`, `start-app.sh`, or shell environment:

| Variable | Required | Meaning |
|---|---:|---|
| `STARTUP_MODE` | No | One of `EXISTING_DB`, `EMPTY_DB`, `INIT_FILE`. Default: `EXISTING_DB`. |
| `INIT_STATE_FILE` | Only for `INIT_FILE` | Path to the initial-state file. Supports filesystem paths, `file:...`, and `classpath:...`. |
| `VALIDATE_EXTERNAL_SYSTEMS` | No | Whether startup validates payment/ticketing availability before initialization. Default: `true` in the main profile, `false` in tests. |

### Examples

Start normally using the existing DB:

```powershell
$env:STARTUP_MODE="EXISTING_DB"
$env:INIT_STATE_FILE=""
.\start-app.ps1
```

Start from an empty DB with only the required admin/platform baseline:

```powershell
$env:STARTUP_MODE="EMPTY_DB"
$env:INIT_STATE_FILE=""
.\start-app.ps1
```

Start from an initial state file:
```powershell
$env:STARTUP_MODE="INIT_FILE"
$env:INIT_STATE_FILE="config/initial-state.sample.txt"
.\start-app.ps1
```

### File format

- **One command per line:** `command-name(arg1, arg2, ...)`
- Blank lines are ignored; lines starting with `#` or `//` are comments.
- A trailing `;` is allowed.
- Wrap an argument in **double quotes** to embed spaces, commas or parentheses
  (`\"` and `\\` are escapes inside quotes).
- Entities are referenced by **aliases** (a username, a company alias, an event alias…)
  declared by earlier commands. A `login(...)` makes that member the *actor* for
  subsequent authenticated commands.

### Failure behavior

Startup/config errors are fatal and stop the server with a clear startup error. Examples:

- invalid `STARTUP_MODE`
- `STARTUP_MODE=INIT_FILE` without `INIT_STATE_FILE`
- configured init-state file does not exist
- configured init-state file cannot be read
- external payment/ticketing services are unavailable while `VALIDATE_EXTERNAL_SYSTEMS=true`

Init-file content errors are handled differently. If the file is readable but contains an illegal command,
the server logs a clear `INIT_FILE_FAILED` message, rolls back the init-file transaction, and continues
running with the required admin/platform baseline.
```

### Supported commands

| Command | Arguments |
|---|---|
| `register(username, password, firstName, lastName, email, yyyy-MM-dd)` | creates a member (alias = username) |
| `login(username, password)` | logs the member in (required before authenticated actions) |
| `open-production-company(actor, companyAlias, "name", "description", rating)` | founds a company |
| `appoint-owner(actor, companyAlias, targetUsername)` | appoints a co-owner |
| `appoint-manager(actor, companyAlias, targetUsername, PERMS)` | appoints a manager (`PERMS` = `PERM1\|PERM2`, or `ALL`/`NONE`) |
| `accept-appointment(username, companyAlias)` | appointee accepts |
| `modify-manager-permissions(actor, companyAlias, managerUsername, PERMS)` | changes manager permissions |
| `remove-appointee(actor, companyAlias, targetUsername)` | removes an owner/manager |
| `close-production-company(actor, companyAlias)` / `reopen-production-company(actor, companyAlias)` | suspend / reopen |
| `create-event(actor, companyAlias, eventAlias, "name", isoDateTimes, category, "location", "description")` | creates a draft event (`isoDateTimes` = `2026-09-01T20:00\|2026-09-02T20:00`) |
| `create-standing-zone(actor, eventAlias, zoneAlias, "name", price, currency, capacity)` | adds a standing zone |
| `create-seated-zone(actor, eventAlias, zoneAlias, "name", price, currency, rowSpec)` | adds a seated zone (`rowSpec` = `A:10\|B:8`) |
| `publish-event(actor, eventAlias)` | publishes the event |
| `set-purchase-policy(actor, companyAlias, "policyName", RULE_TYPE, value)` | e.g. `MAX_TICKETS, 4` |
| `set-discount-policy(actor, companyAlias, "policyName", "discountName", percent)` | company-wide discount |
| `open-lottery(actor, eventAlias, lotteryAlias)` | opens a lottery for the event |
| `register-lottery(username, lotteryAlias)` | enters a member into the lottery |
| `draw-lottery(actor, lotteryAlias, winnerCount)` | closes and draws winners |
| `reserve(username, orderAlias, zoneAlias, quantity)` | reserves tickets into an active order |
| `checkout(orderAlias, paymentToken, discountCode)` | completes purchase (empty `discountCode` = none) |

Manager permission names: `EVENT_INVENTORY_MANAGEMENT`, `VENUE_CONFIGURATION`,
`MODIFY_POLICIES`, `VIEW_PURCHASE_HISTORY`, `GENERATE_SALES_REPORT`.

Example:

```text
register(rina, password123, Rina, Cohen, rina@example.com, 1990-05-20)
login(rina, password123)
open-production-company(rina, acme, "Acme Live", "Top live concerts", 4.5)
create-event(rina, acme, summerfest, "Summer Fest", 2026-09-01T20:00, Festival, "Tel Aviv Park", "Annual festival")
create-standing-zone(rina, summerfest, ga, "General Admission", 150.00, ILS, 500)
publish-event(rina, summerfest)
```

---

## 6. Building & testing

```bash
mvn clean install            # build all modules
mvn test                     # run all tests
mvn test -pl infrastructure -am   # test a single module (with its deps)
```

### Test database

Repository/integration/concurrency tests run against a **real PostgreSQL** (the domain
entities use `jsonb`/`enum` columns that H2 cannot host). Start it with Docker:

```bash
docker compose up -d         # PostgreSQL on localhost:5434
```

- DB-backed tests are **auto-skipped** when `localhost:5434` is unreachable, so the build
  stays green without Docker (see `PostgresAvailableCondition`).
- Tests never touch the real cloud database: they use the dedicated test datasource and
  a fresh schema (`ddl-auto: create-drop`), so they don't pollute production data.
- Robustness/init tests verify that the server **does not start** when initialization is
  invalid, and that a failed init-state file leaves the database unchanged.

---

## 7. Project layout

```
event-system-parent
├── domain/           # JPA-annotated aggregates + repository ports
├── application/      # use-case services + ports
├── infrastructure/   # Spring Boot app: API, security, persistence adapters,
│                     #   external HTTP (WSEP) adapters, bootstrap & init loader
├── coverage-aggregate/   # JaCoCo aggregated coverage
├── event-system-ui/      # React/Vite client
├── docs/diagrams/        # ERD + class/architecture diagrams (.mmd)
├── config/               # sample initial-state file
└── docker-compose.yml    # local/test PostgreSQL
```
