# Hospital Management System - AI Agent Instructions

## Fixed Tech Stack (Never change or substitute)
- **Frontend:** React.js, TypeScript, HTML, CSS (Vite is the build tool)
- **Backend:** Java 21, Spring Boot 3.5 (Maven)
- **API Style:** REST APIs (JSON over HTTP)
- **Database:** MySQL
- **ORM / Data Access:** Spring Data JPA (Hibernate)
- **Authentication:** Spring Security with JWT (role-based access)
- **Tools:** VS Code, Git, GitHub

## Fixed Roles (Exactly 4, never add more)
1. `ADMIN`
2. `DOCTOR`
3. `RECEPTIONIST`
4. `PATIENT`
Roles are a `role` column on the user, enforced with `@PreAuthorize`.

## Architecture
- One shared Spring Boot backend (`apps/api`) serving three React apps:
  - `apps/web-public` (public website, Layer 1)
  - `apps/web-patient` (booking app and patient portal, Layers 2 & 3)
  - `apps/web-staff` (internal dashboard for ADMIN, DOCTOR, RECEPTIONIST, Layer 4)
- Shared UI package: `packages/ui`
- Local infra via `docker-compose.yml` (MySQL, Mailhog)

## Working Rules
1. Follow `hospital-management-system-workplan.md` phase by phase. Only work on the phase named by the user.
2. Before writing code for a phase, summarise your plan and wait for user OK.
3. Never invent features, libraries, or roles outside the plan. Ask if something is unclear.
4. Write tests for every backend feature. Run them and show results.
5. Database changes go through Flyway migrations only. Hibernate `ddl-auto` stays `"none"`.
6. Every access to patient data must be written to the audit log (built in Phase 1).
7. Never commit secrets. Use environment variables and keep `.env.example` updated.
8. After each task, report what changed and how to run or verify it.
