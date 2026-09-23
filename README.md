# Divine Laundry Admin — Spring Boot + Thymeleaf

Read **START-HERE-THYMELEAF.md** first. The new website runs at localhost:8080
through `backend/start-demo.cmd` or Maven, not by opening `prototype/index.html`.
The historical instructions below describe the older React/prototype checkpoint.
Their Basic-auth defaults and readiness claims do not apply to the new website.
The current validation status is in **THYMELEAF-VERIFICATION.md**.

## Historical React / prototype checkpoint

Admin-only laundry operations platform inspired by the client's existing workflow and rebuilt with clearer billing, safer invoice generation, garment tagging, and WhatsApp-ready receipts.

## Project layout

- `backend/` - Java 25 + Spring Boot REST API
- `frontend/` - React admin website source
- `prototype/` - dependency-free interactive UI preview
- `docs/` - agreed scope and business rules

## First-release modules

- Secure admin login
- Dashboard
- Customer records
- New order/POS
- Grouped Dry Clean catalog for Men, Women, Kids, Household and Accessories
- Legacy prices preloaded as editable starting prices; unpriced legacy items stay inactive
- Quick actions and custom-item billing fields
- In-process and ready orders
- Per-piece and per-kilogram pricing
- Separate physical-piece and billable-quantity tracking
- Duplicate-order and duplicate-invoice protection
- Payment status and balance tracking
- Receipt and garment-tag data model
- Manual pickup/delivery slots
- Sales-report foundation

## Run the UI prototype

The easiest option on Windows is to extract the latest ZIP and double-click `prototype/index.html`. The preview keeps its JavaScript and CSS beside the HTML so it also works from a local `file:///` address.

Alternatively, run a local server:

```bash
cd divine-laundry-admin
python3 -m http.server 4173
```

Open `http://localhost:4173/prototype/` and sign in with the pre-filled prototype credentials.

The offline prototype now supports a complete safe test flow. Test customers, bills and payments are saved only in that browser with `localStorage`:

1. Sign in with the pre-filled prototype credentials.
2. Open **Customers** and choose **Add customer**.
3. The new customer is automatically selected in **New order**.
4. Select services, adjust quantity/pieces and choose **Create bill & payment**.
5. Record a full or partial payment.
6. Download the invoice PNG or choose **WhatsApp invoice image**.

On a compatible phone, the system share sheet can include the PNG invoice. From a Windows desktop opened with `file:///`, the preview downloads the PNG and opens WhatsApp with the bill text; attach the downloaded PNG before sending. Direct automatic image delivery requires the deployed backend and an official WhatsApp Business provider.

## Run the functional website

The React website now uses the Spring Boot API by default. Start the backend first, then the frontend in a second terminal.

### 1. Backend

```bash
cd backend
$env:JAVA_HOME='C:\Users\acer\.jdks\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host 'Local MySQL runtime password' -AsSecureString)).Password
$env:FLYWAY_DB_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host 'Local MySQL migration password' -AsSecureString)).Password
mvn spring-boot:run
```

Plain `mvn spring-boot:run` uses the `local` profile and connects to
`127.0.0.1:3306/divine_laundry` as `laundry_app`. Passwords are supplied
externally and are never stored in the repository. To use the isolated H2 demo
database instead, run `mvn spring-boot:run "-Dspring-boot.run.profiles=demo"`.

For local development only, the defaults are:

```text
Username: admin
Password: ChangeMe123!
```

Set a new `ADMIN_PASSWORD` before sharing or deploying the system.

### 2. React website

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` and sign in. The website loads customers, the 76-service catalog, active orders, dashboard values and reports from the API.

If a different backend address is used, copy `.env.example` to `.env` and change `VITE_API_URL`.

### Optional sample-data mode

To review the React design without saving real data, set:

```text
VITE_USE_MOCKS=true
```

The dependency-free `prototype/` remains the simplest offline test option. It is not the production database: its records stay only in the browser used for testing.

## Connected in this checkpoint

- Authenticated admin login against Spring Security
- Live customer list and customer creation
- Live legacy service catalog and previous-site starting prices
- Open-order warning when a customer already has active bills
- Order creation with a stable idempotency key
- One-time invoice finalisation
- Idempotent Cash, UPI, Card and bank-transfer payment recording
- Partial-payment balance and `UNPAID`/`PARTIAL`/`PAID` status updates
- Printable invoice and one stable garment tag per physical piece
- Downloadable PNG invoice image for customer sharing
- Backend-generated invoice/payment PNG with a dynamic balance-specific UPI QR
- Official Meta Cloud API media upload and utility-template delivery
- Automatic WhatsApp send after invoice finalisation and each new payment
- Deduplicated WhatsApp retries with visible waiting, sent and failed states
- Dashboard counts and totals from saved data
- Today, this-week and this-month sales reports
- Product/service-wise sales totals, quantities and physical-piece counts

The browser keeps the development login only in memory; it is cleared on logout or when the tab closes.

## Backend environment example

See `backend/.env.example` for the MySQL, web-origin, timezone, admin, UPI and WhatsApp settings required for deployment. The default development database is H2 in MySQL compatibility mode. Follow `docs/whatsapp-automatic-setup.md` before enabling automatic sending.

### Database privilege separation

Use a least-privileged runtime account for the application itself and a separate migration account for Flyway.

- `DB_USERNAME` / `DB_PASSWORD`: runtime datasource credentials that the app uses for normal reads/writes.
- `FLYWAY_ENABLED`: set to `true` when connecting to a MySQL-backed environment that should run Flyway migrations.
- `FLYWAY_DB_USERNAME` / `FLYWAY_DB_PASSWORD`: migration-only credentials used by Spring Flyway to apply schema changes such as `V8__payment_request_url_and_qr_columns.sql`.
- In production, the runtime user should not be granted permanent `CREATE`, `ALTER`, `INDEX`, `REFERENCES`, or `DROP` privileges. Those rights belong to the migration account only.
- Local H2 tests and local development default to Flyway off unless you explicitly enable it, so the app does not try to use MySQL credentials during in-memory test startup.

## Legacy prices

The database and React sample catalog contain the priced items visible in the previous Fabklean website: 76 active services in total, including 58 Dry Clean services. Items that were visible without a price were not guessed and remain inactive until the client confirms a value.

## Current checkpoint

This is a functional website checkpoint, not the production launch. Customer creation, order billing, duplicate-request protection, invoice numbering, payments, invoice/tag printing, sales reports, backend invoice/QR image generation and Meta Cloud API delivery are connected. It intentionally reports `WAITING_FOR_PROVIDER` until the client supplies a verified UPI ID, approved utility template and official WhatsApp Business credentials. Production role management, refund handling, printer calibration, delivery callbacks and deployment still require implementation and testing with the client's accounts and devices.

## Production deployment (Linux / AWS EC2)

The production Spring profile is `prod`. It requires externalized credentials and does not provide database or admin-password defaults. Do not enable Razorpay live mode as part of deployment; use Razorpay Test Mode credentials until a separate, deliberate live-mode review is completed.

### Required runtime environment

Set these values through the EC2 service manager, Docker secrets, or another secret manager. Do not commit a `.env` file or real values.

```text
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
DB_URL=jdbc:mysql://mysql-host:3306/divine_laundry?connectionTimeZone=UTC
DB_USERNAME=<least-privileged-runtime-user>
DB_PASSWORD=<runtime-password>
FLYWAY_DB_URL=jdbc:mysql://mysql-host:3306/divine_laundry?connectionTimeZone=UTC
FLYWAY_DB_USERNAME=<migration-user>
FLYWAY_DB_PASSWORD=<migration-password>
ADMIN_USERNAME=<admin-username>
ADMIN_PASSWORD=<12-character-or-longer-password>
```

The application runtime account should have only the privileges required by the application. Keep schema creation, alteration, indexing and drop privileges with the separate Flyway migration account. The application does not create production users at startup.

Optional integrations use the existing property names:

```text
RAZORPAY_ENABLED=false
RAZORPAY_BASE_URL=https://api.razorpay.com
RAZORPAY_KEY_ID=<Razorpay Test Mode key id>
RAZORPAY_KEY_SECRET=<Razorpay Test Mode key secret>
RAZORPAY_WEBHOOK_SECRET=<Razorpay Test Mode webhook secret>
WHATSAPP_ENABLED=false
WHATSAPP_GRAPH_BASE_URL=https://graph.facebook.com
WHATSAPP_GRAPH_API_VERSION=<supported Graph API version>
WHATSAPP_PHONE_NUMBER_ID=<Meta phone number id>
WHATSAPP_ACCESS_TOKEN=<Meta system-user token>
WHATSAPP_TEMPLATE_NAME=laundry_invoice_payment
WHATSAPP_TEMPLATE_LANGUAGE=en
WHATSAPP_DOCUMENT_TEMPLATE_NAME=<approved document template>
WHATSAPP_DOCUMENT_TEMPLATE_LANGUAGE=en
```

`RAZORPAY_ENABLED=false` keeps the safe provider behavior. Set it to `true` only for the existing Razorpay Test Mode flow with Test Mode credentials. WhatsApp remains disabled until its official Cloud API configuration and approved templates are ready. The current code does not consume `WHATSAPP_PROVIDER`, `WHATSAPP_API_URL`, or `WHATSAPP_BUSINESS_ACCOUNT_ID`; use `WHATSAPP_GRAPH_BASE_URL` and the variables above.

### MySQL and Flyway

Create the database and two accounts before starting the application. Grant the runtime account normal application privileges only, and grant the migration account the schema migration privileges needed by Flyway. Do not use MySQL root as either application credential.

Production-safe privilege model:

```sql
-- runtime account: application writes/reads only
GRANT SELECT, INSERT, UPDATE, DELETE ON divine_laundry.* TO 'laundry_app'@'%';

-- migration account: DDL privileges for Flyway schema changes
GRANT ALTER, CREATE, CREATE VIEW, CREATE ROUTINE, DELETE, DROP, INDEX, REFERENCES, TRIGGER, UPDATE, INSERT, SELECT ON divine_laundry.* TO 'laundry_migrator'@'%';
```

Back up the database before Flyway migrations or schema changes, and never let the application itself create or alter production schema privileges at runtime.

### Docker

The backend image is defined in `backend/Dockerfile` and runs as a non-root `appuser` on Java 25. Build it from the project root:

```bash
docker build -t divine-laundry-admin:prod ./backend
```

For a private app/MySQL network with persistent MySQL storage, copy the environment placeholders into a deployment-only environment file and run:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

The Compose file exposes only the application port. MySQL has no public port mapping and the app connects to the internal `mysql` service hostname. The migration credentials must already exist in MySQL; Compose creates only the runtime account through the official MySQL image initialization variables.

### Webhook and HTTPS

Expose the application through an HTTPS reverse proxy or load balancer. Configure the Razorpay webhook URL as:

```text
https://<public-host>/api/webhooks/razorpay
```

Keep `RAZORPAY_WEBHOOK_SECRET` in the deployment secret store. Signature validation remains mandatory, and only this exact POST endpoint is CSRF-exempt. Normal admin POST requests continue to require authentication and CSRF protection. The public health endpoint is `GET /api/health`.

Use the nginx profile in `docs/nginx-production.conf` as a starting point. Replace `YOUR_DOMAIN` with the real public host, keep the backend behind port 8080, and use `proxy_set_header X-Forwarded-Proto https;` for correct generation of absolute links and payment callbacks.

Do not log authorization headers, database passwords, Razorpay secrets, webhook secrets or WhatsApp access tokens. Use EC2 instance roles, Docker secrets, or a managed secret store instead of putting credentials in images, Compose files or Git.

### EC2 / AWS deployment steps

1. Create an EC2 instance with Ubuntu 22.04 or 24.04 LTS, a public IPv4 or Elastic IP, and a security group that allows `80/tcp`, `443/tcp`, and `22/tcp` only as needed.
2. Connect as a non-root user and install the required packages:
   ```bash
   sudo apt-get update
   sudo apt-get install -y ca-certificates curl git docker.io docker-compose-plugin nginx certbot python3-certbot-nginx
   sudo systemctl enable --now docker nginx
   ```
3. Add your user to the `docker` group if needed:
   ```bash
   sudo usermod -aG docker $USER
   newgrp docker
   ```
4. Clone the repository:
   ```bash
   cd ~
   git clone https://github.com/barani90251/divine-laundry-admin.git
   cd divine-laundry-admin
   ```
5. Create a production environment file with only the variables your deployment needs, for example `.env.prod`:
   ```bash
   cp backend/.env.example .env.prod
   chmod 600 .env.prod
   ```
   Fill in `DB_*`, `FLYWAY_*`, `RAZORPAY_*`, `WHATSAPP_*`, `ADMIN_*`, `APP_BASE_URL`, and `WEB_ORIGIN` values via environment variables or a secrets manager. Never commit `.env.prod`.
6. Prepare persistent MySQL storage. Use a dedicated volume or an external managed database if available. The application stack in `docker-compose.prod.yml` keeps MySQL off the public network and uses a named volume for persistence.
7. Start the application stack:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
   ```
8. Verify the application health endpoint through the reverse proxy or directly on the app port:
   ```bash
   curl -fsS http://localhost:8080/api/health
   ```
9. Configure nginx with `docs/nginx-production.conf`, then enable the site and reload nginx.
10. Point a domain to the EC2 instance via DNS A/AAAA records, then request a certificate:
   ```bash
   sudo certbot --nginx -d YOUR_DOMAIN
   ```
11. Configure the Razorpay webhook in the Razorpay dashboard to call `https://YOUR_DOMAIN/api/webhooks/razorpay` using the same `RAZORPAY_WEBHOOK_SECRET` value configured in the environment.
12. Configure WhatsApp Cloud API in Meta and keep its secrets only in the deployment environment, never in Git or Docker image layers.
13. Review logs:
   ```bash
   docker compose -f docker-compose.prod.yml logs -f app
   docker compose -f docker-compose.prod.yml logs -f mysql
   ```
14. Create backups before Flyway migrations or major schema changes; for MySQL, prefer `mysqldump` or a managed snapshot approach.
15. For updates, pull the latest code, rebuild the app image, and restart the stack:
   ```bash
   git pull --ff-only
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
   ```
16. To roll back, stop the new stack, restore the previous source revision, and restore the MySQL snapshot or dump if needed:
   ```bash
   docker compose -f docker-compose.prod.yml --env-file .env.prod down
   git checkout <previous-tag-or-commit>
   docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
   ```
17. Keep `APP_BASE_URL` aligned with the public HTTPS domain so callback and payment links are generated correctly in production.

### Validation

From `backend/`, with Java 25 selected:

```powershell
$env:JAVA_HOME='C:\Users\acer\.jdks\jdk-25.0.2'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q clean test
mvn -q package -DskipTests
```

The default development/test profiles remain independent of `prod`; they continue to use the existing H2/test provider and mock WhatsApp behavior without production secrets.
