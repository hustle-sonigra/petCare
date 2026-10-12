# Pet Vaccination Tracker

A full-stack starter for a pet vaccination schedule and record tracker. Product scope, user flows, business rules, and architecture decisions are documented in [context.md](context.md).

## Current State

The workspace contains a responsive Angular dashboard shell, a typed frontend API client, a Spring Boot REST API foundation, PostgreSQL persistence configuration, and an initial Flyway schema for pets and vaccination records.

The dashboard currently uses placeholder counts. The only HTTP endpoint implemented is `GET /api/health`. Authentication, owner-scoped pet/vaccination endpoints, dashboard data, and reminder delivery are not implemented yet. Do not use this starter to store real personal or veterinary records until authentication and server-side ownership checks are in place.

## Structure

```text
.
├── context.md
├── docker-compose.yml
├── frontend/
│   ├── src/app/core/api/            # Typed REST client
│   ├── src/app/features/dashboard/  # Initial dashboard shell
│   └── proxy.conf.json              # Local /api proxy to Spring Boot
└── backend/
    ├── src/main/java/com/pettracker/
    │   └── features/
    │       ├── health/              # Health endpoint
    │       ├── pets/                # Pet domain and persistence
    │       └── vaccinations/        # Vaccination domain and persistence
    └── src/main/resources/
        ├── application.properties
        └── db/migration/            # Versioned PostgreSQL schema
```

## Requirements

- Node.js 22 and npm
- Java 21
- Maven 3.9 or later
- Docker Desktop or Docker Engine with Compose

## Local Development

1. Create local database configuration. The example values are for development only:

   ```powershell
   Copy-Item .env.example .env
   docker compose up -d db
   ```

2. Start the backend in a PowerShell terminal:

   ```powershell
   $env:DB_URL = "jdbc:postgresql://localhost:5433/pet_tracker"
   $env:DB_USERNAME = "pet_tracker"
   $env:DB_PASSWORD = "local-dev-only"
   cd backend
   mvn spring-boot:run
   ```

   Flyway applies migrations on startup. Check the API health endpoint at `http://localhost:8081/api/health` and Spring Actuator at `http://localhost:8081/actuator/health`.

3. In a separate terminal, install and start Angular:

   ```powershell
   cd frontend
   npm ci --legacy-peer-deps
   npm start
   ```

   Open `http://localhost:4200`. The Angular development server forwards `/api` and `/actuator` requests to the backend on port 8081.

The `.env` file is ignored by Git. The committed `.env.example` contains local-only placeholder values; replace them for any shared or deployed environment. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the backend process environment rather than putting database credentials in source-controlled application configuration.

## Certificate extraction and review

The vaccination entry page starts with PDF/JPG/PNG ingestion. It calls the authenticated
`POST /api/certificates/extract` multipart endpoint (field name `file`) before creating any record.
This endpoint returns `clinicName`, `vaccineName`, `administeredOn`, `nextDueOn`, plus field warnings
and supporting text. It does not persist the upload or the suggestions.

Content signatures route PDFs to PDFBox and images to Tess4J. Each PDF page is inspected separately:
usable digital text is retained; sparse/unreadable text or substantial raster layers trigger
300 DPI rendering and OCR. Mixed pages retain both digital and OCR text. Processing accepts up to
5 MB, 10 PDF pages, 20 million pixels per rendered/image page, 200,000 PDF text characters and two
simultaneous extraction requests. These limits bound normal processing; native OCR has no hard
execution deadline and should run in a separate worker process if strict time limits are required.

The interpretation service uses line-local clinic labels, vaccine keywords, and contextual date
labels. It supports ISO dates and numeric slash/dash dates. Ambiguous day/month ordering, invalid
dates, multiple candidates and reversed due dates are left unresolved. Bare dates are never assigned
by their position. This is a rule-based suggestion system for printed certificates, not a general
medical document understanding model; handwriting and complex tables need manual entry.

Angular maps clinic name to the existing `provider` field and requires four explicit verification
checkboxes before saving, including confirmation that optional values are unavailable. Editing a
field invalidates its verification. Only after confirmation does the existing flow create a
vaccination, upload the original certificate and navigate to the pet profile. If certificate storage
fails, the saved record ID is reused on retry. Leaving/reloading that page loses the in-memory retry
state. The verification gate is an interface requirement, not proof of review enforced by the API.

For local Windows OCR, resolve Maven dependencies, then prepare the English model:

```powershell
mvn -f backend/pom.xml dependency:resolve
powershell -ExecutionPolicy Bypass -File backend/setup-ocr.ps1
cd backend
mvn spring-boot:run
```

`setup-ocr.ps1` copies the English model bundled in Tess4J into the ignored `backend/tessdata/`
directory. Set `TESSDATA_DIR` to an absolute directory containing `eng.traineddata` if starting from
a different working directory. Tess4J provides a Windows 64-bit native binary and requires the
[Microsoft Visual C++ v14 runtime](https://github.com/nguyenq/tess4j). Linux deployment needs the
Tesseract/Leptonica native libraries and English language data installed in the runtime image.
Digital-only PDFs do not need native OCR. Missing OCR data/native libraries return HTTP 503, with
retry/manual-entry options in the interface.

Extraction tests cover contextual interpretation, PDF/image routing, authentication, malformed
documents, and actual native OCR of a generated printed certificate. Angular tests cover upload
states, response mapping, manual review, verification invalidation, and retry without duplicate records.

## Daily vaccination reminder emails

Every day at **09:00 Asia/Kolkata**, the job calculates the current local date plus seven days,
queries `vaccination_records.next_due_on` for exact equality, and joins pets to `owner` to retrieve
names and emails. Archived pets and previously emailed records are excluded. Each owner gets one
personalized message listing matching pets/vaccines and a link to log into the app.

Reminders are enabled by default. Without a configured SMTP host, sender, or (when SMTP authentication
is enabled) username/password, the entire prepared message is printed to the backend terminal.
SMTP delivery errors also fall back to printing; they do not stop the batch. Console previews are
not recorded as sent, so repeating a run prints them again and configuring SMTP later allows a
same-day email retry. If there are no matching records, only the batch summary is printed.
Console output includes owner email addresses and reminder content; treat these logs as private.

For the external timer, set a long random secret in the backend process environment and restart:

```powershell
$env:REMINDER_TRIGGER_KEY = 'replace-with-a-long-random-secret'
$env:REMINDER_CRON = '-' # Optional: disable the internal timer when using an external one.
cd backend
mvn spring-boot:run
```

Trigger the same job through the authenticated URL:

```powershell
curl.exe -X POST 'http://localhost:8081/api/reminders/vaccinations/run' -H "X-Reminder-Key: $env:REMINDER_TRIGGER_KEY"
```

Configure an external scheduler to make this POST request daily at 09:00 Asia/Kolkata, using the
public HTTPS backend URL and the `X-Reminder-Key` secret header. A remote timer cannot reach your
machine's `localhost`. GET is intentionally unsupported to prevent browser previews or crawlers
from sending reminders. The endpoint does not use owner login tokens: its secret authorizes the
whole batch. A missing server-side key returns 503; a missing/wrong supplied key returns 401.
Keep the secret in a header instead of a URL/query string and store it in the timer's secret store.
A successful response contains `dueOn`, `sent`, `printed`, `skipped`, and `failed`; batch failures
return 503 with the counts so the external timer can retry. Printed SMTP fallbacks return 200.

To turn on actual email delivery, supply the following environment variables in the terminal that
starts Spring Boot (Compose's root `.env` does not automatically configure that process):

```powershell
$env:REMINDER_FROM = 'your-verified-sender@example.com'
$env:SMTP_HOST = 'your-provider-smtp-host'
$env:SMTP_PORT = '587'
$env:SMTP_USERNAME = 'your-provider-username'
$env:SMTP_PASSWORD = 'your-provider-smtp-password'
$env:APP_URL = 'https://your-public-app-domain'
```

Restart the backend after configuration changes. Defaults use authenticated SMTP with required
STARTTLS on port 587 and five-second connection/read/write timeouts. `SMTP_AUTH` and `SMTP_STARTTLS`
can be disabled for a local SMTP capture server. The mail health indicator is disabled so absent SMTP
or its console fallback does not mark the whole application unhealthy.

`REMINDER_CRON` defaults to `0 0 9 * * *` (Spring's six-field cron); `REMINDER_ZONE` defaults to
`Asia/Kolkata`. Both scheduled and HTTP runs calculate today's date in that same timezone. The owner
timezone field does not control this batch. Set `REMINDERS_ENABLED=false` to disable the job and URL.
The app must be running when triggered. There is no catch-up: selection is always exactly seven days
ahead, so a missed date or yesterday's failed reminder is not retried on the following day.

Flyway V4 adds a due-date index and a delivery ledger keyed by vaccination ID/due date. An owner-scoped
PostgreSQL transaction lock prevents simultaneous instances from emailing the same pending digest.
Only successful SMTP sends are recorded. SMTP and PostgreSQL cannot commit atomically: a crash after
SMTP acceptance but before the database commit can still cause a duplicate. SMTP acceptance does not
guarantee inbox delivery. Existing successful deliveries remain deduplicated for scheduled/HTTP reruns.

Tests mock outgoing email and verify console fallback, protected HTTP triggering, scheduling,
timezone selection, and delivery tracking. To additionally test the query against real PostgreSQL
using temporary tables and rollback:

```powershell
$env:REMINDER_DB_TEST = 'true'
mvn -f backend/pom.xml test
```

The scheduler and mail integration use Spring's
[scheduling support](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
and [mail sender](https://docs.spring.io/spring-boot/4.0/reference/io/email.html).

## Running validation

Run the frontend production build and unit tests:

```powershell
cd frontend
npm run build
ng test --watch=false
```

Run the backend test suite:

```powershell
cd backend
mvn test
```

The backend health test is an MVC slice and does not require PostgreSQL. A full application startup does require the local database and the environment variables above.

## Architecture Notes

- Angular and Spring Boot are separate deployable applications joined by versionable REST/JSON endpoints.
- The backend is a modular monolith with feature-oriented packages and DTO boundaries to be added as APIs are implemented.
- PostgreSQL is the relational source of truth. Flyway migrations own schema changes; Hibernate validates the mapped schema rather than creating it.
- Administration and due dates use `LocalDate`/SQL `DATE`. Reminder delivery is a later feature and must apply the account's time zone.
- The initial schema stores an owner UUID but deliberately has no pet endpoints until authentication and object-level authorization are implemented.
- Future appointments, medications, uploads, and notifications should be separate feature modules linked to pets rather than fields added to vaccination records.
