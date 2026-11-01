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

1. **Database Setup (Non-root user):**
   Docker Compose automatically initializes the limited `hms` user. If configuring a local MySQL instance manually:
   ```sql
   CREATE DATABASE IF NOT EXISTS hms_db;
   CREATE USER IF NOT EXISTS 'hms'@'localhost' IDENTIFIED BY 'hms_password_123';
   GRANT ALL PRIVILEGES ON hms_db.* TO 'hms'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Start Infrastructure:**
   ```bash
   npm run infra:up
   ```

4. **Run Typecheck & Build:**
   ```bash
   npm run typecheck
   npm run build
   ```

5. **Run API Tests:**
   ```bash
   npm run test:api
   ```

6. **Start Development Servers:**
   - Backend API (port 4000):
     ```bash
     npm run dev:api
     ```
   - All Frontend Apps:
     ```bash
     npm run dev:web
     ```

## REST API Endpoints (Phase 1)

All endpoints return uniform JSON responses. Protected endpoints require a Bearer token in the `Authorization` header.

### Authentication (`/api/auth`)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/auth/login` | Public | Staff email/password login (rate limited, lockout after 5 failed attempts) |
| `POST` | `/api/auth/refresh` | Public (Cookie) | Rotate refresh token and issue fresh access token |
| `POST` | `/api/auth/logout` | Public (Cookie) | Revoke refresh token and clear auth cookie |
| `GET` | `/api/auth/me` | Authenticated | Retrieve current user profile and role |
| `POST` | `/api/auth/otp/send` | Public | Request email/phone OTP for patient login or password reset |
| `POST` | `/api/auth/otp/verify` | Public | Verify OTP code and obtain authentication tokens |
| `POST` | `/api/auth/register-patient`| Public | Self-registration for patients with single-use OTP verification |
| `POST` | `/api/auth/password-reset/request` | Public | Request password reset email |
| `POST` | `/api/auth/password-reset/confirm` | Public | Confirm password reset with single-use token |

### Master Data (`/api/branches`, `/api/departments`, `/api/specialties`, `/api/rooms`)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `GET` | `/api/branches` | Public | Paginated list (unauthenticated callers see active branches only) |
| `GET` | `/api/branches/{id}` | Public | Branch details by ID (active only for unauthenticated callers) |
| `POST` | `/api/branches` | `ADMIN` | Create new hospital branch (@Audited) |
| `PUT` | `/api/branches/{id}` | `ADMIN` | Update branch details (@Audited) |
| `DELETE`| `/api/branches/{id}` | `ADMIN` | Soft delete branch (409 if referenced by active doctors or rooms) (@Audited) |
| `GET` | `/api/departments` | Public | Paginated list (unauthenticated callers see active only) |
| `GET` | `/api/departments/{id}` | Public | Department details by ID |
| `POST` | `/api/departments` | `ADMIN` | Create new department (@Audited) |
| `PUT` | `/api/departments/{id}` | `ADMIN` | Update department details (@Audited) |
| `DELETE`| `/api/departments/{id}` | `ADMIN` | Soft delete department (409 if referenced by active rooms) (@Audited) |
| `GET` | `/api/specialties` | Public | Paginated list (unauthenticated callers see active only) |
| `GET` | `/api/specialties/{id}` | Public | Specialty details by ID |
| `POST` | `/api/specialties` | `ADMIN` | Create medical specialty (@Audited) |
| `PUT` | `/api/specialties/{id}` | `ADMIN` | Update medical specialty (@Audited) |
| `DELETE`| `/api/specialties/{id}` | `ADMIN` | Soft delete specialty (409 if referenced by active doctors) (@Audited) |
| `GET` | `/api/rooms` | Authenticated | Paginated room search by branch, department, and room type |
| `GET` | `/api/rooms/{id}` | Authenticated | Room details by ID |
| `POST` | `/api/rooms` | `ADMIN` | Create room (409 if duplicate room number in branch) (@Audited) |
| `PUT` | `/api/rooms/{id}` | `ADMIN` | Update room (@Audited) |
| `DELETE`| `/api/rooms/{id}` | `ADMIN` | Soft delete room (@Audited) |

### Doctor Management (`/api/doctors`, `/api/admin/doctors`)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `GET` | `/api/doctors` | Public | Paginated list of active doctors. Returns public DTO with NO email, phone, or user ID |
| `GET` | `/api/doctors/{id}` | Public | Active doctor profile (returns public DTO; 404 for inactive doctors) |
| `GET` | `/api/admin/doctors` | `ADMIN` | Paginated list of all doctors (active & inactive) with full user details |
| `GET` | `/api/admin/doctors/{id}` | `ADMIN` | Full doctor details by ID |
| `POST` | `/api/admin/doctors` | `ADMIN` | Single transaction: creates DOCTOR user (min 8 char password) and profile (@Audited) |
| `POST` | `/api/admin/doctors/photo` | `ADMIN` | Upload doctor profile photo for creation with `is_public=true` (@Audited) |
| `POST` | `/api/admin/doctors/{id}/photo` | `ADMIN` | Upload/update doctor profile photo for existing doctor (@Audited) |
| `PUT` | `/api/admin/doctors/{id}` | `ADMIN` | Update doctor profile details, specialties, and branches (@Audited) |
| `DELETE`| `/api/admin/doctors/{id}` | `ADMIN` | Deactivate doctor profile, disable linked user, and revoke all refresh tokens (@Audited) |

### User & Staff Management (`/api/admin/users`)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `GET` | `/api/admin/users` | `ADMIN` | Paginated list with role and search filters (never returns password hashes) |
| `GET` | `/api/admin/users/{id}` | `ADMIN` | User details by ID |
| `POST` | `/api/admin/users` | `ADMIN` | Create staff (`ADMIN`, `DOCTOR`, `RECEPTIONIST` only; rejects `PATIENT` with 400) (@Audited) |
| `PATCH` | `/api/admin/users/{id}/role` | `ADMIN` | Change role (prevents self-demotion or demoting last active ADMIN; revokes refresh tokens) (@Audited) |
| `PATCH` | `/api/admin/users/{id}/status` | `ADMIN` | Enable/disable user (prevents disabling self or last active ADMIN; revokes refresh tokens) (@Audited) |

### Audit Log Viewer (`/api/admin/audit-logs`)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `GET` | `/api/admin/audit-logs` | `ADMIN` | Paginated audit logs with filters: userId, action, entityType, and date range |

### File Storage (`/api/files`)
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/files/upload` | Authenticated | Upload file (max 5 MB; verifies magic bytes for JPEG, PNG, WebP, PDF; random UUID stored name) (@Audited) |
| `GET` | `/api/files/{storedName}` | Public / Auth | Download file (public for doctor photos; requires owner or ADMIN for private files; traversal safe) |
| `DELETE`| `/api/files/{storedName}` | Authenticated | Delete file (owner or ADMIN only) (@Audited) |

> [!NOTE]
> **Known Item - Orphaned Photo Files:**
> When creating a new doctor, a profile photo may be pre-uploaded via `POST /api/admin/doctors/photo`. If the administrator abandons the creation form without submitting, the pre-uploaded photo remains stored on disk as an orphaned file. A background scheduled cleanup routine is planned for later phases to prune unreferenced temporary files.
