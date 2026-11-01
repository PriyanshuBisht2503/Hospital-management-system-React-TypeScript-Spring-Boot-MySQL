 # ADR 0001: Tech Stack & System Architecture

## Status
Accepted

## Context
We are designing and building a comprehensive Hospital Management System modeled after the Aster Hospitals (UAE) ecosystem. The platform consists of four layers:
1. Public Website (L1) - Visitors & prospective patients
2. Booking & Patient App (L2) - Patients scheduling & onboarding
3. Patient Portal (L3) - Registered patients accessing history, results, bills
4. Core HMS / Internal Staff System (L4) - Doctors, receptionists, hospital administrators

To avoid fragmented, disconnected systems, the architecture specifies **one shared backend (Core HMS)** serving all front-facing and internal client applications.

## Decision

### 1. Technology Choices (Fixed Stack)
- **Frontend Framework:** React.js with TypeScript and Vite.
  - Justification: Fast HMR, strong typing for healthcare models, lightweight bundle, reusable across the three frontends via a monorepo shared UI library.
- **Backend Framework:** Java 21 with Spring Boot 3.5.x (Maven).
  - Justification: Robust enterprise architecture, comprehensive ecosystem for security and data persistence, proven concurrency handling.
- **API Style:** RESTful JSON over HTTP with OpenAPI / Swagger documentation (`springdoc-openapi`).
- **Database:** MySQL 8.0 with Flyway SQL migrations. Hibernate `ddl-auto` is strictly set to `none` to maintain immutable, audit-safe schema evolution.
- **ORM / Persistence:** Spring Data JPA with Hibernate.
- **Security & Authorization:** Spring Security with JWT and role-based access control.
- **Roles:** Exactly four fixed roles throughout the system:
  - `ADMIN`
  - `DOCTOR`
  - `RECEPTIONIST`
  - `PATIENT`
  Roles are stored as a column on the user table and enforced using `@PreAuthorize("hasRole(...)")`.

### 2. Monorepo Structure
```
hms/
├── apps/
│   ├── api/             # Spring Boot Maven backend
│   ├── web-public/      # Vite + React (Public Website L1)
│   ├── web-patient/     # Vite + React (Booking & Patient Portal L2/L3)
│   └── web-staff/       # Vite + React (Staff & Admin Dashboard L4)
├── packages/
│   └── ui/              # Shared React UI components & design tokens
├── docs/                # Architecture Decision Records (ADRs) & documentation
├── docker-compose.yml   # Local MySQL & Mailhog infrastructure
└── README.md
```

### 3. Compliance & Audit
- Mandatory audit logging on all patient data access and mutations.
- Secrets kept in environment variables (`.env.example` checked into VCS; `.env` excluded).

## Consequences
- Single backend simplifies deployment, transaction handling, and prevents data inconsistencies across hospital channels.
- Enforcing exactly four roles eliminates over-engineering while covering the entire operational cycle.
- All database mutations must be committed as numbered Flyway migration files.
