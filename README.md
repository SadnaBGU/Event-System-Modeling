# Event System

Ticketing platform (BGU Software Engineering Workshop).
Spring Boot + React, backed by **PostgreSQL on Google Cloud**.

## Prerequisites
- Java 17
- Maven 3.9+
- Node.js 22+

## Run

**1. Start the backend** (connects to Google Cloud PostgreSQL):
```powershell
.\start-app.ps1
```
Wait for: `Started EventSystemApplication`.

**2. Start the UI** (new terminal):
```powershell
cd event-system-ui
npm install   # first time only
npm run dev
```

**3. Open** <http://localhost:5173>

> If scripts are blocked, run once: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`

## Cloud connection

`start-app.ps1` sets the connection via environment variables, then runs
`mvn spring-boot:run -pl infrastructure` with profile `main`.

| Variable | Example |
|---|---|
| `DB_IP` | `34.10.240.178` |
| `DB_PORT` | `5432` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | _(secret)_ |

Final URL: `jdbc:postgresql://<DB_IP>:<DB_PORT>/eventsystem_db?sslmode=require`

## Admin login

| Username | Password |
|---|---|
| `admin` | `SuperSecretPassword123!` |

## Build & test
```powershell
mvn -DskipTests install   # build all modules
mvn test                  # run tests (DB tests need Docker on :5434)
```

## Docs
- Database (ERD): `docs/diagrams/erd/v3_database_ERD.mmd`
- Architecture: `docs/diagrams/class_diagrams/`
