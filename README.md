# Hospital Management System (HMS)

A modular, enterprise-grade Hospital Management System modelled on the Aster Hospitals (UAE) ecosystem.

## Architecture

- **Backend:** One shared Spring Boot backend (`apps/api`) exposing REST APIs.
- **Frontend Clients:**
  - `apps/web-public`: Public website (L1) for visitors and prospective patients.
  - `apps/web-patient`: Booking and patient portal (L2, L3) for registered patients.
  - `apps/web-staff`: Internal hospital dashboard (L4) for Admins, Doctors, and Receptionists.
- **Shared Packages:**
  - `packages/ui`: Shared design system, tokens, and React components.
- **Infrastructure:**
  - `docker-compose.yml`: MySQL database and Mailhog for local development.

## Fixed Tech Stack

- **Frontend:** React.js, TypeScript, HTML5, CSS3 (Vite build tool)
- **Backend:** Java 21, Spring Boot 3.5.x / 3.4.x (Maven)
- **API Style:** REST APIs (JSON over HTTP)
- **Database:** MySQL 8.0
- **ORM / Data Access:** Spring Data JPA (Hibernate, `ddl-auto: none`)
- **Schema Migrations:** Flyway
- **Authentication:** Spring Security with JWT & BCrypt (role-based access)
- **Fixed Roles (4 only):** `ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PATIENT`
- **Rate Limiting:** Bucket4j
- **API Docs:** Springdoc OpenAPI (Swagger UI at `/swagger-ui.html`)

## Demo accounts (dev only)

> [!WARNING]
> **CRITICAL SECURITY NOTICE:**
> The demo accounts listed below are seeded **strictly** under the `dev` Spring profile (`SPRING_PROFILES_ACTIVE=dev`) for local development and testing. They **must never exist in production**. The seeder is guarded to run only when the `dev` profile is active, and the application logs a high-visibility warning at startup whenever demo accounts exist in the database.

| Role | Email | Password | Auth Method | Notes |
|------|-------|----------|-------------|-------|
| `ADMIN` | `admin123@hms.local` | `admin@123` | Email & Password | Full system administration |
| `DOCTOR` | `doctor123@hms.local` | `doctor@123` | Email & Password | Linked to doctor profile (Cardiology, Mankhool branch) |
| `RECEPTIONIST` | `receptionist123@hms.local` | `receptionist@123` | Email & Password | Front desk operations & check-in |
| `PATIENT` | `patient123@hms.local` | _None_ | OTP verification (`/api/auth/otp/send`) | Patient portal & appointment booking |

*Normal security rules apply to demo accounts: BCrypt password hashing, account lockout (after 5 failed attempts), Bucket4j rate limiting, and audit logging.*

## Getting Started

### Prerequisites
- Node.js 20+ & npm 10+
- Java 21+
- Docker & Docker Compose (or local MySQL 8.0)

### Quick Start

1. **Install dependencies:**
   ```bash
   npm install
   ```

2. **Start Infrastructure:**
   ```bash
   npm run infra:up
   ```

3. **Run Typecheck & Build:**
   ```bash
   npm run typecheck
   npm run build
   ```

4. **Run API Tests:**
   ```bash
   npm run test:api
   ```

5. **Start Development Servers:**
   - Backend API (port 4000):
     ```bash
     npm run dev:api
     ```
   - All Frontend Apps:
     ```bash
     npm run dev:web
     ```
