# CareHMS — Hospital Management System

> A full-stack, production-ready hospital management platform built to make hospital operations less of a headache — for both staff and developers.

---

## What is this?

Running a hospital involves a *lot* of moving parts — patients walking in, doctors being scheduled, appointments being booked, bills being generated, and a mountain of records to keep track of. CareHMS is an attempt to bring all of that under one roof, with a clean UI and a solid backend that won't fall over when things get busy.

This project is built as a **full-stack monorepo** — a React frontend talking to a Spring Boot backend, backed by MySQL for persistence and Redis for caching. Everything is containerised with Docker, so you can spin it up anywhere with a single command.

---

##   Features at a Glance

###   Patient Management
- Register and manage patient profiles with full demographic and contact details
- Track medical records, diagnoses, prescriptions, and lab orders
- View patient history in one place

###   Appointments
- Book, reschedule, and cancel appointments
- Real-time appointment status tracking
- Role-based visibility — doctors see their own schedule, receptionists manage the queue

###   Doctors & Departments
- Manage doctor profiles and their department assignments
- Track schedules, attendance, and leave requests
- Support for multiple specialisations

###  Wards & Beds
- Bed and room allocation with live availability tracking
- Admissions, discharges, and transfer management
- Room types (General, ICU, Private, etc.)

###  Lab & Diagnostics
- Create lab orders linked to patient visits
- Manage lab tests and attach reports to patient records
- Track order and report statuses end-to-end

###  Billing & Invoicing
- Auto-generate invoices from appointments, admissions, and lab orders
- Itemised billing with support for medicines, consultations, and procedures
- Payment tracking (Cash, Card, Insurance, etc.)
- Insurance management per patient

###  Authentication & Role-Based Access
- JWT-based secure authentication with refresh token support
- Redis-backed token session management
- Roles: **Admin**, **Doctor**, **Receptionist** — each with fine-grained access
- Protected routes on both frontend and backend

###  Dashboard
- At-a-glance metrics for admins — patient counts, appointment summaries, revenue snapshots
- Role-aware views so everyone sees what is relevant to them

###  Notifications
- Internal notification system for important events (appointments, results, etc.)

---

##  Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 (Java 21) |
| Security | Spring Security + JWT (JJWT 0.12) |
| Database ORM | Spring Data JPA + Hibernate |
| DB Migrations | Flyway |
| Caching | Redis (Spring Data Redis) |
| Validation | Spring Validation (Bean Validation) |
| Mapping | MapStruct + Lombok |
| Testing | JUnit 5, Spring Boot Test, Testcontainers |

### Frontend
| Layer | Technology |
|---|---|
| Framework | React 19 + TypeScript |
| Build Tool | Vite 8 |
| Routing | React Router v7 |
| Data Fetching | TanStack Query (React Query v5) |
| Forms | React Hook Form + Zod |
| HTTP Client | Axios |
| Icons | Lucide React |
| Linting | OXLint |
| Testing | Vitest + Testing Library + Playwright (E2E) |

### Infrastructure
| Layer | Technology |
|---|---|
| Database | MySQL 8.0 |
| Cache | Redis 7.0 |
| Containerisation | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| Monitoring | Prometheus |
| Web Server | Nginx (frontend serving + API proxy) |

---

##  Architecture Overview

```
+-----------------------------------------------------+
|                     Browser                         |
|              React 19 + TypeScript SPA              |
|      (Vite + React Router + TanStack Query)         |
+-------------------+---------------------------------+
                    |
                    | HTTP / REST (via Nginx proxy)
                    |
+-------------------v---------------------------------+
|              Spring Boot 3.2 API                    |
|   (Security + JPA + Redis + Flyway + MapStruct)     |
+--------+---------------------------+----------------+
         |                           |
+--------v---------+      +----------v-----------+
|   MySQL 8.0      |      |     Redis 7.0         |
|  (Persistence)   |      |  (Token Sessions      |
|                  |      |   and Caching)        |
+------------------+      +-----------------------+
```

The backend follows a clean layered architecture:
- **Controllers** → handle HTTP requests and delegate to services
- **Services / ServiceImpl** → business logic lives here
- **Repositories** → Spring Data JPA interfaces for database access
- **DTOs + MapStruct Mappers** → clean data transfer between layers
- **Entities** → 50+ domain models covering every hospital workflow
- **Security** → JWT filter chain, role guards, refresh token rotation
- **Events** → internal application events for decoupled side effects

The frontend uses a feature-first folder structure under `src/features/`, with shared pages, components, services, and utilities kept separate.

---

##  Getting Started

### Prerequisites
Make sure you have the following installed:
- [Docker](https://www.docker.com/) and Docker Compose
- That's it for the quick start 

### Quick Start (Docker)

This is the easiest way to run the whole stack locally:

```bash
# Clone the repo
git clone https://github.com/PriyanshuBisht2503/Hospital-Management-system.git
cd Hospital-Management-system

# Start everything — MySQL, Redis, Backend, Frontend
docker compose up --build
```

Once the containers are up:
- **Frontend** → http://localhost
- **Backend API** → http://localhost:8080

> The backend waits for MySQL and Redis to be healthy before starting, so the first boot might take 30–60 seconds. Grab a coffee.

---

### Running Locally (Without Docker)

If you prefer running services directly on your machine:

**Backend**

```bash
# Make sure MySQL is running on port 3306 with a database named 'hms'
# and Redis is running on port 6379

cd backend
./mvnw spring-boot:run
```

Or override environment variables:
```bash
DATABASE_HOST=localhost \
DATABASE_NAME=hms \
DATABASE_USERNAME=root \
DATABASE_PASSWORD=password \
REDIS_HOST=localhost \
./mvnw spring-boot:run
```

**Frontend**

```bash
cd frontend
npm install
npm run dev
```

The dev server starts at http://localhost:5173

---

##  Running Tests

**Backend tests** (uses Testcontainers — Docker required):
```bash
cd backend
./mvnw test
```

**Frontend unit tests:**
```bash
cd frontend
npm run test
```

**Frontend lint check:**
```bash
cd frontend
npm run lint
```

---

##  Environment Variables

The backend is fully configurable via environment variables. Here's a quick reference:

| Variable | Default | Description |
|---|---|---|
| `DATABASE_HOST` | `localhost` | MySQL host |
| `DATABASE_PORT` | `3306` | MySQL port |
| `DATABASE_NAME` | `hms` | Database name |
| `DATABASE_USERNAME` | `root` | MySQL user |
| `DATABASE_PASSWORD` | `password` | MySQL password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `ALLOWED_ORIGINS` | — | CORS allowed origins (comma-separated) |

> **For production**, please change the default passwords and generate a strong JWT secret key. Don't deploy with `password` as your database password — you know better than that!

---

##  CI/CD Pipeline

The project ships with a GitHub Actions workflow (`.github/workflows/ci-cd.yml`) that runs automatically on every push and pull request to `main` or `release`:

1. **Lint & Test** — Runs backend unit tests (Maven), frontend lint (OXLint), and frontend unit tests (Vitest)
2. **Build & Push** — On merge to `main`, builds Docker images for both the backend and frontend

This means any broken code gets caught before it reaches the main branch.

---

##  Project Structure

```
Hospital-Management-system/
├── backend/                    # Spring Boot application
│   ├── src/main/java/com/hospital/management/
│   │   ├── controller/         # REST controllers (Auth, Patient, Doctor, Appointment, Billing)
│   │   ├── service/            # Service interfaces
│   │   ├── serviceImpl/        # Business logic implementations
│   │   ├── entity/             # 50+ JPA domain entities
│   │   ├── dto/                # Request/Response DTOs
│   │   ├── mapper/             # MapStruct mappers
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── security/           # JWT filters, user details, token management
│   │   ├── config/             # Spring configs (Security, Redis, CORS)
│   │   ├── exception/          # Global exception handling
│   │   └── event/              # Application events
│   └── src/main/resources/
│       ├── application.yml     # App configuration
│       └── db/migration/       # Flyway SQL migration scripts
│
├── frontend/                   # React + TypeScript SPA
│   ├── src/
│   │   ├── features/           # Feature modules (auth, patients, appointments, billing, doctors, dashboard)
│   │   ├── pages/              # Page components (Dashboard, Patients, Appointments, Billing, Login)
│   │   ├── components/         # Shared UI components
│   │   ├── services/           # API service layer (Axios + React Query)
│   │   ├── context/            # Auth context and global state
│   │   ├── types/              # TypeScript type definitions
│   │   └── utils/              # Utility helpers
│   └── e2e/                    # Playwright end-to-end tests
│
├── prometheus/
│   └── prometheus.yml          # Prometheus scrape config
│
└── docker-compose.yml          # Full stack orchestration
```

---

##  Contributing

Contributions are welcome! If you find a bug, have a feature idea, or want to improve something:

1. Fork the repo
2. Create a branch: `git checkout -b feature/your-feature-name`
3. Make your changes and write tests if applicable
4. Push and open a Pull Request against `main`

Please keep PRs focused — one feature or fix per PR makes reviewing a lot easier.

---

##  License

This project is open source. Feel free to use it, learn from it, or build on top of it.

---
