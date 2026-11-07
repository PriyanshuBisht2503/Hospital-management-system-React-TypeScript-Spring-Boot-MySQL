# CareHMS — Hospital Management System

**CareHMS** is a fictional, modular hospital management system built as a **learning project**. It models the day-to-day work of a hospital — staff authentication and roles, master data, doctor schedules and availability, appointment booking and lifecycle, and a staff operations UI.

> This is a portfolio/learning project. It is **not** a production medical system, it has no real patients, and it must not be used with real personal or health data.

## Architecture

A single Spring Boot backend serves three independent React apps.

| App | Port | Purpose |
|---|---|---|
| `apps/api` | `4000` | One shared REST backend (auth, RBAC, master data, scheduling, appointments) |
| `apps/web-public` | `3000` | Public website (marketing/browse) — scaffold only |
| `apps/web-patient` | `3001` | Patient booking & portal — scaffold only |
| `apps/web-staff` | `3002` | Internal staff dashboard for `ADMIN`, `DOCTOR`, `RECEPTIONIST` |

```
apps/
  api/            Spring Boot 3 backend (Java 21)
  web-public/     React + Vite (public)
  web-patient/    React + Vite (patient)
  web-staff/      React + Vite (staff dashboard)
packages/
  ui/             Shared design system (React components, tokens)
docs/
  adr/            Architecture Decision Records
scripts/          Local dev helpers
```

Local infrastructure (`docker-compose.yml`): MySQL 8.0 + Mailhog.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.4, Spring Security + JWT, Spring Data JPA/Hibernate, Flyway, MySQL 8.0, Bucket4j rate limiting, Springdoc OpenAPI.
- **Frontend:** React 19, TypeScript, Vite, React Router, TanStack Query, React Hook Form + Zod.
- **Testing:** JUnit 5 + MockMvc + H2 (and a real-MySQL concurrency suite), Vitest + Testing Library.
- **Roles (exactly four):** `ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PATIENT`.

## Features done so far (through Phase 2)

- **Auth & RBAC (4 roles):** JWT access + rotating refresh tokens, BCrypt hashing, account lockout, rate limiting, OTP login/reset for patients, `@PreAuthorize` role checks.
- **Master data:** branches, departments, specialties, rooms — CRUD with soft delete and audit logging.
- **Doctors:** profiles, user linking, photos, public search API.
- **Doctor schedules & exceptions:** weekly templates (breaks, slot length), leave/extra-hours/holiday exceptions, activation rules.
- **Slot calculation:** open slots derived from templates + exceptions + existing bookings, in hospital-local (IST) time.
- **Double-booking-proof appointment engine:** transactional booking, `READ_COMMITTED`, per-doctor locking and a database unique-key backstop.
- **Appointment state machine:** `BOOKED → CONFIRMED → CHECKED_IN → IN_CONSULTATION → COMPLETED`, plus cancel / no-show / reschedule rules.
- **Notifications & reminders:** async email/SMS notifications on book/cancel/reschedule; 24h and 2h reminder jobs.
- **Patients:** staff-managed patient records + medical history; doctor-facing contact masking.
- **Staff UI (`apps/web-staff`):** patients directory/detail, doctor schedules, appointment list/detail with actions + rescheduling, booking wizard, doctor "My Day" and staff "Day Agenda".

## Roadmap (NOT built yet)

- Public marketing website (content pages).
- Online self-booking and online payment.
- Patient portal (their own appointments and records).
- Billing & invoicing.
- Medical records / clinical notes beyond appointment history.

## Run locally (Windows)

### Prerequisites
- Node.js 20+ and npm 10+
- Java 21+
- Docker Desktop (for MySQL + Mailhog), or a local MySQL 8.0

### 1. Start infrastructure
```powershell
npm run infra:up
```
This starts MySQL (`hms_db`, user `hms`) and Mailhog (SMTP `1025`, UI `8025`).

### 2. Configure environment
```powershell
Copy-Item .env.example .env
```
Edit `.env` if your MySQL credentials differ. `.env` is git-ignored.

### 3. Install dependencies
```powershell
npm install
```

### 4. Run the backend (port 4000)
```powershell
npm run dev:api
```
Starts with the `dev` profile (Flyway migrations + fictional dev seed data).

### 5. Run the staff UI (port 3002)
```powershell
npm run dev:web-staff
```
Or run all frontends with `npm run dev:web`.

## Tests

### Backend (from the repo root)
```powershell
npm run test:api
```
Runs the JUnit suite, including a real-MySQL concurrency test (`AppointmentConcurrencyTest`) under the `mysql-test` profile.

### Frontend
```powershell
cd "C:\Users\priya\OneDrive\Documents\HOSPITAL AI\apps\web-staff"
npm test
```

### Typecheck and build (from the repo root)
```powershell
npm run typecheck
npm run build
```

## Demo accounts (dev profile only)

> These accounts are seeded **only** when `SPRING_PROFILES_ACTIVE=dev`. They must never exist in production.

| Role | Email | Password |
|---|---|---|
| `ADMIN` | `admin123@hms.local` | `admin@123` |
| `DOCTOR` | `doctor123@hms.local` | `doctor@123` |
| `RECEPTIONIST` | `receptionist123@hms.local` | `receptionist@123` |
| `PATIENT` | `patient123@hms.local` | OTP (`/api/auth/otp/send`) |

## Security design summary

- **Passwords:** BCrypt hashing; never returned by any API.
- **Tokens:** short-lived JWT access tokens + rotating, revocable refresh tokens (HTTP-only cookie); logout/role change/user disable revokes refresh tokens.
- **Lockout & rate limiting:** account lockout after 5 failed logins; Bucket4j throttling on auth and booking endpoints.
- **RBAC:** exactly four roles, enforced with `@PreAuthorize`; patient data is scoped server-side and written to the audit log.
- **Input validation:** Bean Validation + a global error handler returning a consistent `ErrorResponse`; 404s avoid leaking existence of inaccessible records.
- **Secrets:** the database password and JWT secret come from environment variables. Dev-only defaults live only in the `dev` profile and `.env.example`; production config requires `DB_PASSWORD` to be set explicitly.
- **Double booking:** prevented by a transactional check, a per-doctor lock, and a unique database key as the final backstop (see `docs/adr/0003-*.md`).

## Test counts

- **Backend:** 342 documented JUnit tests (see [`docs/phase-2-backend-summary.md`](docs/phase-2-backend-summary.md)), plus the `AppointmentNeedsReschedulingTest` cases added for the `needsRescheduling` filter. The count here is the number of test methods; parameterized tests expand to more executions.
- **Frontend:** 32 Vitest test files under `apps/web-staff/src/__tests__`.

Run the suites above for the current, authoritative counts.

## Docs

- [`docs/phase-2-backend-summary.md`](docs/phase-2-backend-summary.md) — Phase 2 backend verification.
- [`docs/adr/`](docs/adr/) — architecture decisions (tech stack, notification executor policy, booking concurrency).
