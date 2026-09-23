# Divine Laundry — Spring Boot + Thymeleaf, milestone 1

This is the new server-rendered website. The older `prototype/` and `frontend/`
folders are retained as reference only. They do not run this milestone.
Do not double-click an HTML template. Run Spring Boot and use localhost:8080.

## What was added

- Admin form login with a server session, CSRF protection and POST logout.
- Customer creation/search backed by the configured database. Indian mobile
  numbers are normalized, so `+91 98765 43210` and `9876543210` are one profile.
- Order form with multiple lines, catalogue prices, delivery date/time,
  independent weight and garment counts, discount and manually entered tax.
- One atomic save creates the order and invoice. Repeated submissions with the
  same request ID reuse that order; a new form permits another order for the same
  customer. The web flow serializes creation for one customer across app instances.
- Saved-order page, partial/full payments, remaining balance and payment history.
- Printable invoice (browser Print / Save as PDF) and server-generated PNG.
- Optional balance-specific UPI QR in the PNG. Without UPI configuration the PNG
  explicitly says no QR is configured; it never uses a fake payment code.
- All supported invoice lines appear in the PNG (up to 50). The old renderer
  stopped at 12. Historical line-item rates remain unchanged.
- Existing reference catalogue retained. The sofa-seat price is corrected to
  INR 199 based on the supplied screenshot; unconfirmed zero-price items cannot
  be selected for a new web order.

The admin account is configured through environment variables, not a staff
management page. This milestone is not a production launch.

## Quick demo test on Windows — no MySQL required

1. Extract this ZIP into a NEW folder. Keep your previous project unchanged.
2. Install Java JDK 25+ and Apache Maven. Check `java -version` and `mvn -version`
   in a newly opened terminal. The first build requires internet access.
3. Double-click `backend/start-demo.cmd`, or open a terminal in `backend` and run:

   ```powershell
   mvn spring-boot:run "-Dspring-boot.run.profiles=demo"
   ```

4. Wait for the Spring `Started` message. Open http://localhost:8080.
5. Sign in with `admin` / `DemoLaundry123!`.
6. Add a test customer using a number you control. Add an ironing shirt line:
   quantity 2, physical pieces 2. With zero discount/tax the total should be INR 28.
7. Save, print the invoice, download the PNG, and record a test cash payment.
8. Restart the server and confirm the customer/order/payment still exist.

Demo mode uses a local H2 **file database** in `backend/data/`, not MySQL or
browser localStorage. It binds only to 127.0.0.1 and disables WhatsApp sending.
Its published login is for local testing only. Keep the data folder to preserve
demo records. Browser prototype records are NOT imported automatically.

### IntelliJ alternative

Open `backend/pom.xml` as a Maven project and let dependencies load. Select JDK
17 or later. In the run configuration for `LaundryAdminApplication`, set the
active Spring profile to `demo` (or program argument `--spring.profiles.active=demo`).
Run the application, then visit localhost:8080. IntelliJ can use its bundled Maven.

## MySQL development mode

Plain `mvn spring-boot:run` uses the `local` profile and connects to MySQL at
`127.0.0.1:3306`. Set the runtime and migration passwords externally before
starting it; do not commit them. Use the demo command above when MySQL is not
available.

Use a fresh MySQL database for the first test. Do not point an untested migration
at the client's live data. Create a database named `divine_laundry` and a dedicated
database user that can run Flyway's schema migrations on that database.

In PowerShell, from `backend`, configure your own values:

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/divine_laundry?connectionTimeZone=UTC'
$env:DB_USERNAME='laundry'
# Enter passwords locally; do not send them in chat or commit them.
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host 'Database password' -AsSecureString)).Password
$env:ADMIN_USERNAME='admin'
$env:ADMIN_PASSWORD=[System.Net.NetworkCredential]::new('', (Read-Host 'Admin password (12+ characters)' -AsSecureString)).Password
$env:WHATSAPP_ENABLED='false'
mvn spring-boot:run
```

Do NOT activate the demo profile when testing MySQL. Flyway creates the schema
and imports the reference catalogue. Hibernate validates the schema; it does
not recreate the database at startup. Existing migration files V1–V3 are unchanged.
Spring Boot does not automatically load the provided `.env.example`; use real
environment variables or IntelliJ's run configuration.

## Invoice payment QR and WhatsApp

Set `UPI_ID` and `UPI_PAYEE_NAME` locally to the shop's verified values before
starting the server. Confirm the payee name and amount using the shop's payment
app. A QR prepares a payment request; it does NOT confirm bank settlement or
guarantee the payer cannot change an amount. Record payments only after checking.

The printable HTML invoice shows billing totals. The downloadable PNG is the
combined invoice/payment image with optional QR. Automatic PDF-file generation
is not added; use the browser's Save as PDF action.

Meta sending code is retained but has NOT been tested against a live account in
this milestone. WhatsApp is disabled by default in local/demo mode; set
`WHATSAPP_ENABLED=true` to enable it. The existing Meta configuration variables
(`WHATSAPP_GRAPH_API_VERSION`, `WHATSAPP_PHONE_NUMBER_ID`,
`WHATSAPP_ACCESS_TOKEN`, and the approved template variables) are still required
for the action to be fully configured. The token is read only from the
environment and is never rendered or logged. The new UI does not open `wa.me`
or ask you to attach a PNG manually. Use `docs/whatsapp-automatic-setup.md` only
after the database/web flow is verified. Configure approved templates and customer
opt-in before enabling sending. No customer-specific opt-in recording screen is
included yet; this remains a prerequisite for unattended production messaging.

`WAITING_FOR_PROVIDER` means no message was sent. `SENT` means accepted by Meta,
not delivered to the phone. Delivery webhooks, durable background retry jobs,
unknown-outcome duplicate-send handling and payment settlement verification still
need production work. The existing synchronous sender may delay a request when
enabled. Provider failures must not be presented as a successful delivery.

Local and demo profiles use the in-process mock payment provider, so payment
requests can be exercised without Razorpay credentials. Production remains
fail-closed unless `RAZORPAY_ENABLED=true` and valid Razorpay credentials are
supplied.

## Tests and release gate

From `backend` run:

```powershell
mvn test
mvn package
```

`AdminWebFlowTest` exercises real Spring MVC views with a separate in-memory test
database: login, CSRF, customer creation/duplicate detection, invoice creation,
duplicate order submission, PNG download, overpayment rejection and payment replay.
It does not touch your demo or MySQL database and disables WhatsApp.

Java parse and JavaScript syntax checks passed. The browser-fixture check could
not run because the browser executable was unavailable. There is no successful
Maven build yet; Maven was unavailable in the authoring environment.
See `THYMELEAF-VERIFICATION.md` for the exact checks. Run the tests and verify a
fresh MySQL startup before real use. Share error text with credentials removed
if dependency resolution, compilation, schema validation or startup fails.

Before client release: finish remaining screens (work statuses, manual pickup
slots, reports, price editing, staff roles and tag-printer calibration); review
invoice/tax requirements with the business; add consent records, audit history,
backups and restore tests; deploy behind HTTPS with `COOKIE_SECURE=true`; use
strong private credentials and database network restrictions; test simultaneous
orders/payments and printers. Do not publish the demo login/database.

## Project entry points

- `backend/src/main/java/com/divinelaundry/web/`: controllers, form DTOs and web services.
- `backend/src/main/resources/templates/`: Thymeleaf pages.
- `backend/src/main/resources/static/assets/`: local CSS/JavaScript, no CDN required.
- `backend/src/main/resources/application-demo.yml`: localhost-only test configuration.
- `backend/src/test/java/com/divinelaundry/web/AdminWebFlowTest.java`: integration tests.

The old React frontend's Basic-auth client is not compatible with the new
session/CSRF defaults. Its code is kept only as a reference; no React or Node
server is needed to use this website. Future Flutter APIs need their own reviewed
mobile authentication flow while reusing the service layer and database.
