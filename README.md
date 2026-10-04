<p align="center">
  <img src="frontend/public/brand/lankawear-lockup.png" alt="LankaWear Apparel" width="460">
</p>

<h1 align="center">LankaWear Apparel</h1>

<p align="center">
  <strong>Web-Based Textile &amp; Garment Management System</strong>
</p>

<p align="center">
  React 19 · Spring Boot 4 · MySQL 8 · Liquibase
</p>

LankaWear Apparel is a full-stack operations platform built for a Sri Lankan garment
business. It brings product cataloguing, supplier coordination, material inventory,
customer orders, quotations, billing, production, quality control, delivery,
notifications, reporting, and role-based administration into one system of record.

Repository: <https://github.com/IT24100351/Web-based-textile-and-garment-management-system.git>

## Contents

- [Project team and responsibilities](#project-team-and-responsibilities)
- [Core capabilities](#core-capabilities)
- [Business workflow](#business-workflow)
- [Application roles](#application-roles)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Database setup](#database-setup)
- [Environment configuration](#environment-configuration)
- [Demo data](#demo-data)
- [Development commands](#development-commands)
- [Testing and quality](#testing-and-quality)
- [Project structure](#project-structure)
- [Security model](#security-model)
- [Troubleshooting](#troubleshooting)
- [Deployment](#deployment)
- [Project documentation](#project-documentation)

## Project team and responsibilities

| Member | IT No. | Major function | Scrum role | Receives data from | Hands data/work to |
| --- | --- | --- | --- | --- | --- |
| Nimsara V.G.P | IT25101310 | Order Management | Product Owner | Customer and Product modules | Production, Delivery, Customer tracking/reporting |
| Basnagoda B.L.K | IT25102148 | Fabric Inventory | Scrum Master | Supplier supply data | Production material availability/consumption |
| Gunarathna P.G.S.C | IT25103068 | Delivery Management | Developer | Order after Production marks ready | Order completion and Customer tracking |
| Ketipearachchi R.P | IT25100244 | Supplier Management | Developer | Supplier's own input | Inventory context/source records |
| Gunathilaka N.T.D.S | IT25102427 | Garment Product Management | Developer | Sales/product definition | Customer catalogue and Order item selection |
| Dilwan A.W.W.A.S | IT24100351 | Production Management | Developer | Confirmed Order and Inventory | Ready-for-delivery Order |

## Core capabilities

| Area | Capabilities |
| --- | --- |
| Authentication | Verified customer registration, login/logout, session recovery, password reset, secure cookies |
| Administration | Account provisioning, role assignment, account activation and deactivation |
| Product catalogue | Categories, products, variants, sizes, colours, prices, availability, product images |
| Supplier management | Supplier profiles, materials, quantities, prices, lead times and lifecycle status |
| Inventory | Linked supply records, stock quantities, low-stock thresholds, availability and consumption |
| Sales | Customer quotations, order creation, order status history, invoicing and payment recording |
| Production | Production tasks, material requirements, material usage and quality-control results |
| Delivery | Eligible-order selection, scheduling, lifecycle tracking and safe cancellation |
| Operations | Role-aware dashboards, shared search and in-system notifications |

The supported roles are `ADMINISTRATOR`, `SUPPLIER`, `INVENTORY_MANAGER`,
`PRODUCTION_MANAGER`, `SALES_OFFICER`, and `CUSTOMER`. The API enforces permissions;
hiding a route or navigation item in React is only an additional user-interface guard.

## Business workflow

The system is built around a controlled handoff from one module to the next. Each
handoff stores the authoritative state in the backend and shows the correct next step
to the role responsible for that stage.

```text
Garment Product Management
  -> Customer catalogue
  -> Customer places order
  -> Sales Officer confirms order
  -> Production Manager creates and starts production
  -> Inventory Manager receives/releases materials as needed
  -> Production Manager records material usage and quality control
  -> Production Manager marks production completed
  -> Order becomes READY_FOR_DELIVERY, shown as "Completed Preparation"
  -> Sales Officer prepares/schedules delivery
  -> Delivery progresses to delivered
  -> Order becomes COMPLETED for customer tracking/reporting
```

Important status handoffs:

| Handoff | System status | User-facing meaning |
| --- | --- | --- |
| Sales confirms order | `CONFIRMED` | Order is approved for Production Management |
| Production starts | `IN_PRODUCTION` | Order is being manufactured |
| Production completes | `READY_FOR_DELIVERY` | Shown as `Completed Preparation`; Sales can prepare delivery |
| Delivery delivered | `COMPLETED` | Customer order is fully completed |
| Cancellation | `CANCELLED` | Order or delivery flow has been stopped safely |

## Application roles

| Role | Main pages | Responsibility |
| --- | --- | --- |
| `CUSTOMER` | Product catalogue, place order, order history, order tracking | Browse products, place orders, and track order/delivery progress |
| `SALES_OFFICER` | Customer orders, create customer order, quotations, prepare delivery, delivery records | Confirm orders, manage sales documents, schedule delivery after production |
| `PRODUCTION_MANAGER` | Create production task, production records, production task detail | Start production, record material usage, perform quality control, complete production |
| `INVENTORY_MANAGER` | Material supplies, inventory materials, add inventory material | Link supplier supplies, receive stock, monitor/release material availability |
| `SUPPLIER` | Supplier profile, material supplies | Maintain supplier business details and available material supply records |
| `ADMINISTRATOR` | Authorization check, user administration, shared search/report access | Manage internal access and verify secured system boundaries |

## Architecture

```text
Browser
  |
  |  React + Axios
  v
Vite development server :5173
  |
  |  /api proxy
  v
Spring Boot API :3000
  |
  |  services, transactions, validation and authorization
  v
Spring JDBC / HikariCP
  |
  v
MySQL 8+  <--- Liquibase migrations
```

The application follows a modular controller → service/domain → repository structure.
Each module owns its authoritative records. Cross-module workflows use stable database
identifiers, server-side authorization, and validated state transitions.

## Technology stack

| Layer | Technology |
| --- | --- |
| Frontend | React 19, TypeScript 6, Vite 8, React Router 7, Tailwind CSS 4, Axios |
| Backend | Java 17, Spring Boot 4, Spring Security, Spring JDBC, Jakarta Validation |
| Database | MySQL 8+, Liquibase 5, HikariCP |
| Testing | Vitest, Testing Library, JUnit, MockMvc, H2 in MySQL compatibility mode |
| Tooling | npm workspaces, Maven Wrapper, ESLint, GitHub Actions |

## Prerequisites

Install these tools before running the project:

- Node.js 22.22.2+, 24.15.0+, or 26+ (required by the locked jsdom test environment; `.nvmrc` pins 24.15.0)
- npm 11 or a compatible version
- Java Development Kit 17 or newer
- MySQL 8 or newer
- Git

Confirm the main tools:

```bash
node --version
npm --version
java -version
mysql --version
```

The repository includes Maven wrappers for both Unix (`backend/mvnw`) and Windows
(`backend/mvnw.cmd`), so a separate Maven installation is unnecessary.

## CRUD deletion and operational statuses

TGMS treats deletion as CRUD and statuses as business state:

- **Inventory Material** supports safe hard Delete. An unused record is physically deleted.
  A material referenced by Production is protected and returns `409 MATERIAL_IN_USE`.
- **Material Supply** supports safe hard Delete for the owning Supplier. An unused supply is
  physically deleted; a supply referenced by Inventory returns `409 SUPPLY_IN_USE`. Suppliers
  can use `ACTIVE` / `INACTIVE` to control operational availability without faking deletion.
- **Product** supports safe hard Delete for Sales Officers. An unused Product and its owned
  variants are physically deleted; a Product referenced by an Order or Quotation returns
  `409 PRODUCT_IN_USE`. Historical transactional rows are never cascade-deleted.

`ACTIVE` / `INACTIVE` describe current operational availability and are not fake deletion.
Product variants use `AVAILABLE` / `UNAVAILABLE`. `DISCONTINUED` is a legacy migration-only
value and is not part of current Inventory, Material Supply, Product, or Product Variant CRUD
contracts. Public `/products` remains sellable-only, while Sales Officers use the protected
`/products/manage` workspace to find and maintain inactive Products and unavailable variants.

The legacy cleanup migrations for these former `DISCONTINUED` states are destructive and
forward-only. They preserve referenced history but can delete unreferenced legacy rows; rollback
cannot reconstruct that data. Restore a database backup if pre-cleanup data must be recovered.

## Quick start

Clone the repository, then run the platform launcher for your operating system. On its
first run, the launcher creates `backend/.env`. The macOS/Linux launcher generates a
random `JWT_SECRET`; add your local MySQL password before starting the app.

### macOS or Linux

```bash
git clone https://github.com/IT24100351/Web-based-textile-and-garment-management-system.git
cd Web-based-textile-and-garment-management-system
chmod +x run.sh
./run.sh
```

### Windows

```bat
git clone https://github.com/IT24100351/Web-based-textile-and-garment-management-system.git
cd Web-based-textile-and-garment-management-system
run.bat
```

On the first launch, the script:

1. verifies Node.js, npm and Java;
2. creates `backend/.env` from `backend/.env.example` when it is missing;
3. validates the required application settings and optional SMTP configuration;
4. installs the locked npm dependencies when they are missing;
5. starts the API with incremental compilation; and
6. starts the frontend after the API health check succeeds.

Before running the launcher again, create the local database as described in
[Database setup](#database-setup), then set `DB_PASSWORD` in `backend/.env`.
On Windows, also set `JWT_SECRET` to a random value of at least 32 bytes.
Spring Boot applies pending Liquibase migrations automatically during API startup.

Use validation-only mode to check a machine without starting servers:

```bash
./run.sh --check
```

```bat
run.bat --check
```

Application URLs:

| Service | URL |
| --- | --- |
| Public application | <http://localhost:5173> |
| Product catalogue | <http://localhost:5173/products> |
| Login | <http://localhost:5173/login> |
| Customer registration | <http://localhost:5173/register> |
| Customer email verification | <http://localhost:5173/verify-email> |
| API health | <http://localhost:3000/api/health> |

Press `Ctrl+C` in the launcher terminal to stop both servers.

The first backend compilation can take several minutes on slower machines. The
launcher continues waiting and starts the frontend as soon as the API becomes healthy.

## Database setup

### 1. Start MySQL

macOS with Homebrew:

```bash
brew services start mysql
```

Ubuntu/Debian Linux:

```bash
sudo systemctl start mysql
```

Windows installations commonly use the `MySQL80` service name. Open an Administrator
Command Prompt and run:

```bat
net start MySQL80
```

If the service has a different name, open `services.msc`, locate the MySQL service,
and select **Start**.

### 2. Create the database and application account

Open MySQL as an administrative user:

```bash
mysql -u root -p
```

Run the following once, replacing the placeholder with a strong local password:

```sql
CREATE DATABASE tgms
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'tgms_user'@'localhost'
    IDENTIFIED BY '<local-password>';

GRANT ALL PRIVILEGES ON tgms.*
    TO 'tgms_user'@'localhost';
```

Use the same password for `DB_PASSWORD` in `backend/.env`. Do not rerun `CREATE USER`
when the account already exists.

### 3. Inspect and apply migrations

From the repository root:

```bash
npm run db:status
npm run db:migrate
```

Never create application tables manually. The migration source of truth is
`database/migrations/db.changelog-master.xml` and its ordered changesets.

## Environment configuration

Create the local configuration manually if the launcher has not already done so:

macOS/Linux:

```bash
cp backend/.env.example backend/.env
```

Windows:

```bat
copy backend\.env.example backend\.env
```

Important variables:

| Variable | Local purpose |
| --- | --- |
| `PORT` | Spring Boot port; defaults to `3000` |
| `CLIENT_ORIGIN` | Allowed browser origin; defaults to `http://localhost:5173` |
| `DB_URL` | JDBC connection to the `tgms` database |
| `DB_USERNAME` | MySQL application account |
| `DB_PASSWORD` | MySQL application password |
| `JWT_SECRET` | Random secret of at least 32 UTF-8 bytes |
| `JWT_SESSION_HOURS` | Authentication session lifetime |
| `COOKIE_SECURE` | `false` only for local HTTP; production must use secure cookies |
| `SMTP_ENABLED` | Enables customer-verification and password-reset email delivery |
| `EMAIL_VERIFICATION_TTL_MINUTES` | Lifetime of each six-digit verification code |
| `EMAIL_VERIFICATION_RESEND_SECONDS` | Minimum delay before another verification email is issued |
| `EMAIL_VERIFICATION_MAX_ATTEMPTS` | Invalid attempts allowed before a code is locked |
| `PASSWORD_RESET_RESEND_SECONDS` | Minimum delay before another reset email is issued |
| `NOTIFICATIONS_ENABLED` | Enables operational in-system notifications |
| `DEMO_DATA_ALLOWED` | Explicit safety switch for disposable demo seeding |

Generate a suitable local JWT secret on macOS/Linux:

```bash
openssl rand -base64 48
```

Never commit `.env`, `backend/.env`, real credentials, password hashes, or tokens.

### Configure Gmail for authentication email

The application reads mail settings from `backend/.env`. For a Gmail sender, enable
two-step verification for the Google account and create an app password for this
application. Do not use the normal Google account password.

```dotenv
SMTP_ENABLED=true
MAIL_FROM=your-address@gmail.com
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-address@gmail.com
SMTP_PASSWORD=your_16_character_google_app_password
SMTP_AUTH=true
SMTP_USE_STARTTLS=true
```

Keep `MAIL_FROM` equal to the authenticated Gmail address unless the SMTP account is
explicitly allowed to send from an alias. Restart `run.sh` or `run.bat` after changing
the file. New customer accounts receive a six-digit code and cannot sign in until the
code is accepted at `/verify-email`. Password-reset requests use the same SMTP setup.
Verify the configured credentials without sending a message:

```bash
npm run smtp:check
```

The launchers run the same check before starting TGMS when SMTP is enabled, so an
expired, revoked, or incorrect app password is reported immediately instead of leaving
registration emails to fail silently in the background.

## Demo data

The project includes deterministic Sri Lankan demonstration data covering all six
roles and the complete operational lifecycle. It creates:

- Sri Lankan user identities and a supplier profile;
- material supplies and linked inventory;
- four garment products with local catalogue photography and variants;
- quotations, orders, status history, invoices and payments;
- production tasks, material requirements/usage and quality control;
- deliveries and notifications.

The seed is intentionally separate from Liquibase and must only run against a local,
disposable database. On macOS/Linux or Git Bash/WSL:

```bash
export DEMO_DATA_ALLOWED=true
read -s "DEMO_USER_PASSWORD?Demo login password: "
export DEMO_USER_PASSWORD
npm run demo:reset
npm run demo:verify
unset DEMO_USER_PASSWORD DEMO_DATA_ALLOWED
```

All demo accounts use the runtime password entered above. The password and its reusable
hash are not stored in source control. Account emails and detailed demo instructions
are documented in [the demo-data guide](database/demo/README.md).

## Development commands

Run commands from the repository root unless stated otherwise.

### Application lifecycle

| Command | Purpose |
| --- | --- |
| `./run.sh` | Validate configuration and start both services on macOS/Linux |
| `run.bat` | Validate configuration and start both services on Windows |
| `npm run dev` | Start the API and web development servers directly |
| `npm run dev:backend` | Start only the Spring Boot API |
| `npm run dev --workspace frontend` | Start only the Vite frontend |
| `npm run build` | Build the frontend assets and executable backend JAR |

### Quality checks

| Command | Purpose |
| --- | --- |
| `npm run lint` | Lint the frontend and repository scripts |
| `npm run typecheck` | Type-check TypeScript and compile Java |
| `npm test` | Run the complete frontend and backend test suites |
| `npm run test:frontend` | Run Vitest and Testing Library tests |
| `npm run test:backend` | Run Maven/JUnit backend tests |
| `npm run security:check` | Check tracked files for common secret-exposure risks |
| `npm run verify` | Run the full local quality gate used by CI |

### Database and operations

| Command | Purpose |
| --- | --- |
| `npm run db:status` | Show applied and pending Liquibase changesets |
| `npm run db:migrate` | Apply pending changesets |
| `npm run db:rollback:preview` | Preview rollback SQL for the latest changeset |
| `npm run db:rollback` | Roll back the latest changeset when explicitly enabled |
| `npm run db:verify` | Apply the migration chain and run schema plus legacy-data migration verification |
| `npm run smtp:check` | Validate SMTP configuration without sending a message |
| `npm run demo:reset` | Recreate the guarded disposable demonstration dataset |
| `npm run demo:verify` | Verify the demonstration dataset |

Rollback commands are deliberately guarded. Read `database/migrations/README.md` and
preview rollback SQL before using them.

## Testing and quality

The same quality gate used by GitHub Actions is available locally:

```bash
npm ci
npm run verify
```

The gate performs security checks, linting, TypeScript/Java compilation, frontend and
backend tests, isolated migration verification, and production builds. For a focused
frontend run with limited worker resources:

```bash
npm run test --workspace frontend -- --maxWorkers=1 --fileParallelism=false
```

Contribution rules and the Definition of Done are maintained in
[CONTRIBUTING.md](CONTRIBUTING.md).

## Project structure

```text
.
├── backend/
│   ├── src/main/java/lk/ac/sliit/tgms/   Spring Boot modules
│   ├── src/main/resources/               Runtime configuration
│   ├── src/test/                          Backend and schema tests
│   ├── mvnw / mvnw.cmd                    Maven wrappers
│   └── pom.xml
├── database/
│   ├── migrations/                        Liquibase schema source of truth
│   └── demo/                              Disposable presentation data
├── frontend/
│   ├── public/                            Brand, video and product media
│   ├── src/api/                           Typed API clients
│   ├── src/                               React features, pages and components
│   └── vite.config.ts
├── scripts/                               Database, demo, deployment and QA tools
├── run.sh                                 macOS/Linux launcher
├── run.bat                                Windows launcher
├── package.json                           Shared command surface
└── package-lock.json                      Reproducible npm dependency graph
```

Generated `node_modules`, `frontend/dist`, `backend/target`, coverage output and local
environment files are excluded from version control.

## Security model

- Passwords are stored as BCrypt hashes, and authenticated sessions use signed tokens
  carried in HTTP-only cookies.
- New customer accounts cannot sign in until their emailed verification code is
  confirmed.
- Verification codes are random, short-lived, single-use, attempt-limited,
  resend-throttled, and stored only as salted SHA-256 hashes.
- Password-reset tokens are random, time-limited, single-use, stored only as SHA-256
  hashes, and invalidated together after a successful reset.
- API endpoints enforce role, ownership, validation, and lifecycle rules independently
  of frontend routing.
- SQL access uses prepared statements through Spring JDBC, and unexpected errors use a
  safe response envelope without exposing stack traces or internal details.
- Production requires TLS, secure cookies, externally managed secrets, and a private
  MySQL service that is never exposed directly to the internet.

## Troubleshooting

### The browser is blank after restarting Vite

Use a hard refresh:

- macOS Chrome: `Command + Shift + R`
- Windows/Linux Chrome: `Ctrl + Shift + R`

If necessary, clear the site data for `localhost:5173` in Chrome DevTools →
**Application** → **Storage**, then reload.

### The launcher says API startup is still in progress

Keep the launcher terminal open. Java may still be compiling or Liquibase may still be
checking migrations. The frontend starts automatically after the API health endpoint
returns successfully. A later run is faster because normal development startup is
incremental.

### Vite reports `ECONNREFUSED` for `/api/...`

The backend stopped after the frontend had started, or database startup failed. Check
the API terminal output and verify:

```bash
curl http://localhost:3000/api/health
```

### MySQL connection fails

Confirm that MySQL is running and the four `DB_*` values in `backend/.env` match the
database account:

```bash
mysql -u tgms_user -p tgms
npm run db:status
```

### Port 3000 or 5173 is already in use

Stop the older project terminal with `Ctrl+C`. To identify the process:

macOS/Linux:

```bash
lsof -nP -iTCP:3000 -sTCP:LISTEN
lsof -nP -iTCP:5173 -sTCP:LISTEN
```

Windows:

```bat
netstat -ano | findstr :3000
netstat -ano | findstr :5173
```

### `concurrently` or another npm command is missing

Reinstall the locked workspace dependencies:

```bash
npm ci
```

### Verification or password-reset email does not arrive

Confirm `SMTP_ENABLED=true` in `backend/.env`, not only in the root `.env`, and restart
the application. With Gmail, use a Google app password and keep `MAIL_FROM` aligned
with `SMTP_USERNAME`. Check Spam/Junk and the backend terminal for an email-delivery
error. The verification page can request another code after the configured resend
delay; the response deliberately does not reveal whether an account exists.

Run `npm run smtp:check`. Gmail response `535` means the configured app password is
invalid or has been revoked. Generate a new app password while signed in to the same
Google account as `SMTP_USERNAME`, replace `SMTP_PASSWORD` in `backend/.env` without
quotes or spaces, rerun the check, and restart the application.

### Windows blocks `run.bat`

Open Command Prompt in the repository directory and run `run.bat` directly. The batch
launcher does not require PowerShell execution-policy changes.

## Deployment

Production deployment requires TLS, secure cookies, externally supplied secrets, a
private MySQL service, backups, SPA fallback routing, and an `/api` reverse proxy.
No hosting provider is prescribed by the repository. Validate the environment and
build the release artifacts before deploying:

```bash
npm run deploy:env:check
npm run build
```

After deployment, run the HTTPS smoke test against the public base URL:

```bash
TGMS_BASE_URL=https://example.com npm run deploy:smoke
```

Operational scripts are also available for transaction-consistent MySQL backups and
guarded restores:

```bash
npm run db:backup -- backups
DB_ALLOW_RESTORE=true npm run db:restore -- backups/example.sql.gz --confirm
```

Restoration changes the configured database. Confirm the target, take a fresh backup,
and test the procedure in a non-production environment before relying on it.

## Project documentation

| Document | Purpose |
| --- | --- |
| [Contribution guide](CONTRIBUTING.md) | Engineering standards, testing expectations, and Definition of Done |
| [Database migration guide](database/migrations/README.md) | Schema workflow, conventions, and rollback safety |
| [Demo-data guide](database/demo/README.md) | Demo identities, reset controls, and product media |

These documents, together with this README, are the maintained project references.
