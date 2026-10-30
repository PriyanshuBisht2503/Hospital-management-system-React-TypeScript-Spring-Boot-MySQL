# Hospital Management System: Implementation & Work Plan

A phase-by-phase plan to build a full hospital platform modelled on the Aster Hospitals (UAE) ecosystem. Each phase ends with a working, demo-able increment, so finishing all phases gives you the final product.

---

## 1. Project Overview

### 1.1 The four layers

| # | Layer | Aster equivalent | Users |
|---|-------|------------------|-------|
| L1 | **Public Website** | asterhospitals.ae | Visitors, prospective patients |
| L2 | **Booking & Patient App** | myAster (web + mobile) | Patients |
| L3 | **Patient Portal** | ahosp.me Patient Portal | Registered patients |
| L4 | **Core HMS (internal)** | Hidden back-office systems | Doctors, receptionists, admins |

**Architecture principle:** build **one shared backend (L4 core)** exposing APIs. L1, L2 and L3 are clients of it. This avoids Aster's three-separate-systems split.

### 1.2 Tech stack (fixed)

| Layer | Technology |
|-------|-----------|
| Frontend | React.js, TypeScript, HTML, CSS (built with Vite) |
| Backend | Java, Spring Boot |
| API style | REST APIs (JSON over HTTP) |
| Database | MySQL |
| ORM / data access | Spring Data JPA (Hibernate) |
| Authentication | Spring Security with JWT (role-based access) |
| Tools | VS Code, Git, GitHub |

Supporting choices that stay inside this stack:

| Concern | Choice |
|---------|--------|
| Schema migrations | Flyway (SQL migrations for MySQL) |
| API docs | springdoc-openapi (Swagger UI) |
| Background jobs | Spring `@Scheduled` and `@Async` first; add a queue later only if needed |
| File storage | Local disk in dev, S3-compatible storage in production |
| Video consult, payments, SMS / email | Third-party services called from Spring Boot (e.g. Jitsi/Twilio, Razorpay, MSG91) |
| Testing | JUnit 5 + Spring Boot Test (backend), Vitest + React Testing Library (frontend) |
| CI/CD | GitHub Actions |
| Mobile app | Not in the fixed stack; the responsive React apps are used instead |

### 1.3 Compliance (decide early, it affects design)

- **India:** Digital Personal Data Protection Act (DPDP), ABDM / ABHA integration (optional), Clinical Establishments Act norms.
- **UAE (if targeting it):** federal health data law, DHA / DoH e-claim standards.
- Always: encryption in transit and at rest, audit trail on every record access, consent capture, role-based access, data retention policy.

### 1.4 Time estimates

Estimates assume **one full-time developer**. With a team of 3 to 4, expect roughly 40 to 50% of the total time.

| Phase | Name | Est. duration (solo) |
|-------|------|----------------------|
| 0 | Planning & Foundation | 1 to 2 weeks |
| 1 | Core Platform (Auth, RBAC, Master Data) | 2 to 3 weeks |
| 2 | Scheduling & Appointment Engine | 2 to 3 weeks |
| 3 | Public Website (L1) | 3 weeks |
| 4 | Booking & Patient App (L2) | 3 to 4 weeks |
| 5 | Front Desk, OPD & EMR (L4) | 4 to 5 weeks |
| 6 | Billing, Payments & Insurance | 3 to 4 weeks |
| 7 | Lab, Radiology & Pharmacy | 4 weeks |
| 8 | Inpatient, Emergency & OT | 4 to 5 weeks |
| 9 | Patient Portal & Telemedicine (L3) | 3 weeks |
| 10 | Admin, Analytics & Integrations | 3 weeks |
| 11 | Security, QA & Compliance Hardening | 3 weeks |
| 12 | Deployment, Launch & Maintenance | 2 weeks |
| | **Total** | **~37 to 47 weeks** |

---

## 2. Roles & Permissions Matrix

Aster's public site shows only the patient login; its staff roles are internal and not visible from outside. This project keeps the role list small and simple.

**The system has exactly 4 roles, for the whole project:**

| Role | Key permissions |
|------|-----------------|
| `ADMIN` | Manage users, branches, departments, doctors, pricing, reports, website content, pharmacy stock |
| `DOCTOR` | Own schedule, patient visits, EMR write, prescriptions, lab/radiology orders |
| `RECEPTIONIST` | Patient registration, appointments, check-in, queue, billing and payments, and the lab, pharmacy and ward desk work in Phases 7 and 8 |
| `PATIENT` | Own profile, bookings, records, bills (read-only on clinical data) |

No other roles are added in any phase. Where a later phase mentions lab, pharmacy, ward or billing work, it is done by one of these four roles, mainly `RECEPTIONIST` (general hospital staff) and `ADMIN`.

Implement with a `role` on the user and Spring Security's `@PreAuthorize("hasRole('DOCTOR')")` on endpoints. No separate permissions tables are needed.

---

## 3. Recommended Repository Structure

```
hms/
├── apps/
│   ├── api/                # Spring Boot backend (Maven), core HMS
│   ├── web-public/         # React + TypeScript: public website (L1)
│   ├── web-patient/        # React + TypeScript: booking app + portal (L2, L3)
│   └── web-staff/          # React + TypeScript: internal staff dashboard (L4)
├── packages/
│   └── ui/                 # Shared design tokens and React components
├── docs/                   # ADRs, API docs, runbooks
├── docker-compose.yml      # MySQL + Mailhog for local dev
└── README.md
```

Use a **monorepo** (npm workspaces for the React apps, Maven for the backend) so UI code is shared across the three frontends.

Backend package layout (inside `apps/api/src/main/java/com/hms/api`): `config`, `security`, `web` (controllers), `service`, `repository`, `domain` (JPA entities), `dto`.

---

## 4. Core Data Model (Overview)

Create these progressively as the phases call for them.

- **Identity:** `users` (with a `role` column: ADMIN, DOCTOR, RECEPTIONIST, PATIENT), `audit_logs`
- **Organisation:** `branches`, `departments`, `specialties`, `rooms`, `beds`, `wards`
- **People:** `patients`, `doctors`, `staff`, `doctor_branches`, `emergency_contacts`
- **Scheduling:** `doctor_schedules`, `slots`, `appointments`, `queue_tokens`, `leave_blocks`
- **Clinical:** `visits`, `vitals`, `diagnoses`, `clinical_notes`, `allergies`, `prescriptions`, `prescription_items`, `procedures`, `documents`
- **Orders & results:** `lab_tests`, `lab_orders`, `lab_results`, `imaging_orders`, `imaging_reports`
- **Pharmacy:** `drugs`, `drug_batches`, `stock_movements`, `dispensations`, `suppliers`, `purchase_orders`
- **Inpatient:** `admissions`, `bed_assignments`, `nursing_notes`, `medication_admin_records`, `discharge_summaries`, `ot_bookings`
- **Finance:** `services_catalog`, `price_lists`, `invoices`, `invoice_items`, `payments`, `refunds`, `insurance_payers`, `insurance_plans`, `claims`
- **Content:** `pages`, `news`, `campaigns`, `testimonials`, `faqs`
- **Comms:** `notifications`, `templates`, `callback_requests`

---

## 5. The Phases

> **Convention:** every phase has a Goal, Deliverables, Tasks, Acceptance Criteria and a **Working Increment** (what you can demo at the end).

---

### PHASE 0: Planning & Foundation (1 to 2 weeks)

**Goal:** lock scope, architecture and tooling so later phases move fast.

**Tasks**
- [ ] Write a one-page product brief: target hospital type (single / multi-branch), country, languages
- [ ] Decide MVP scope vs "later" features, and mark each feature in this plan as MVP / Later
- [ ] Choose the tech stack (section 1.2) and record decisions as ADRs in `/docs`
- [ ] Set up the monorepo, linting, formatting (ESLint, Prettier), commit hooks (Husky)
- [ ] Set up Git workflow (trunk-based or GitFlow) and PR template
- [ ] Docker Compose for local dev: MySQL, Mailhog
- [ ] CI pipeline: lint, type-check, unit tests on every PR
- [ ] Environments: `local`, `staging`, `production` with `.env` management
- [ ] Design foundations: brand colours, typography, logo, Figma file with design tokens
- [ ] Build a base UI kit (button, input, card, modal, table, form, toast) in `packages/ui`
- [ ] Create wireframes for the 10 most important screens (home, doctor search, booking, patient dashboard, front-desk queue, consultation screen, billing, lab results, bed board, admin dashboard)

**Acceptance criteria**
- `docker compose up` starts the whole dev environment
- A "hello world" API call from each frontend reaches the backend
- CI runs green on an empty PR

**Working Increment:** an empty but fully wired monorepo with CI and a design system.

---

### PHASE 1: Core Platform: Auth, RBAC & Master Data (2 to 3 weeks)

**Goal:** the secure backbone every other module depends on.

**Deliverables**
- Authentication and authorization service
- Organisation master data and admin screens

**Tasks**
- [ ] Database setup, JPA entities, Flyway migration workflow, seed scripts
- [ ] User model; password hashing (argon2); JWT access and refresh tokens
- [ ] Email and phone OTP login (needed later for patients)
- [ ] Password reset, account lockout, optional 2FA for staff
- [ ] Role-based access: `ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PATIENT` via Spring Security (`@PreAuthorize`), and an admin screen to assign roles
- [ ] **Audit log** middleware: who did what, to which record, when (build now, since retrofitting is painful)
- [ ] Global API standards: validation, error format, pagination, filtering, rate limiting, OpenAPI / Swagger docs
- [ ] Master data CRUD: branches, departments, specialties, rooms
- [ ] Doctor profiles: bio, qualifications, languages, experience, photo, specialties, branches, consultation fee
- [ ] Staff management screens in `web-staff`
- [ ] File upload service (images, documents) with type and size checks
- [ ] Unit tests for auth and permission logic (target 80%+ coverage here)

**Acceptance criteria**
- Admin can log in, create a branch, department and doctor, and assign roles
- Unauthorised access returns 403 and is logged
- Swagger docs list every endpoint

**Working Increment:** staff dashboard login with working admin screens for master data.

---

### PHASE 2: Scheduling & Appointment Engine (2 to 3 weeks)

**Goal:** the heart of both the website booking flow and the front desk.

**Tasks**
- [ ] Doctor weekly schedule templates (working days, hours, slot length, breaks, branch)
- [ ] Slot generation: on-demand or via a rolling job; handles leave, holidays, overrides
- [ ] Appointment lifecycle: `booked → confirmed → checked-in → in-consultation → completed / cancelled / no-show`
- [ ] **Concurrency-safe booking**: prevent double booking (DB unique constraint plus transaction / row lock)
- [ ] Appointment types: in-person, video, walk-in, follow-up
- [ ] Reschedule and cancellation rules (cutoff time, fees, reason capture)
- [ ] Doctor search API: filter by specialty, branch, language, gender, availability, fee; sort by next available
- [ ] Waitlist for fully booked doctors (optional)
- [ ] Notification service skeleton (templates, queue, SMS / email adapters) for confirmations and reminders
- [ ] Reminder jobs: 24 hours and 2 hours before an appointment
- [ ] Staff calendar UI (day / week view) for doctors and receptionists

**Acceptance criteria**
- Two simultaneous requests for the same slot: exactly one succeeds
- Cancelling frees the slot immediately
- Reminders are queued and sent at the right times

**Working Increment:** staff can create, move and cancel appointments on a calendar; booking API is ready for public clients.

---

### PHASE 3: Public Website, Layer 1 (3 weeks)

**Goal:** a fast, SEO-friendly, bilingual marketing site that matches the Aster experience.

**Pages and features**
- [ ] **Home:** hero slider, quick actions (Book Appointment, Find a Doctor, Insurance Cover, Locations, Request Callback), service shortcuts, specialty grid, facilities, testimonials, health library preview, campaigns, footer
- [ ] **Specialties:** list and detail pages with doctors, services, FAQs
- [ ] **Find a Doctor:** search, filters, doctor profile pages with "Book Appointment" CTA
- [ ] **Facilities:** emergency, ICU / NICU, OT, cath lab, pharmacy, health-check packages, special clinics
- [ ] **Locations:** branch pages with map, hours, directions, contact
- [ ] **Insurance:** provider dropdown, coverage by network (inpatient / outpatient)
- [ ] **Packages:** preventive health check, maternity, vaccination, home care
- [ ] **International patients:** country-specific pages and enquiry form
- [ ] **Health library, news, campaigns:** CMS-driven
- [ ] **Patient guide:** admission and discharge, rights, visiting rules, policies (separate pages for privacy, refund, cancellation)
- [ ] **Careers:** job listings and an application form
- [ ] **Request Callback** and **Contact** forms (spam protection, saved to DB, emailed to staff)
- [ ] Arabic / RTL support, or your target second language, via i18n
- [ ] Accessibility toolbar (text size, grayscale, contrast); aim for WCAG 2.1 AA
- [ ] Cookie consent banner with granular categories
- [ ] SEO: meta tags, sitemap, structured data (Hospital, Physician, FAQ schemas), clean URLs
- [ ] Performance: image optimisation, lazy loading, Lighthouse 90+
- [ ] Admin screen so `ADMIN` can update pages and news without developers

**Acceptance criteria**
- All pages render in both languages with correct direction
- Lighthouse scores of 90 or above for performance, accessibility and SEO
- Admin can publish a news article with no code change

**Working Increment:** a publicly viewable hospital website, with Book Appointment linking into Phase 4.

---

### PHASE 4: Booking & Patient App, Layer 2 (3 to 4 weeks)

**Goal:** a myAster equivalent where patients register, find doctors, book and pay.

**Tasks**
- [ ] Patient registration with OTP; profile with demographics, emergency contact, ID type (Emirates ID / Aadhaar / passport)
- [ ] Family accounts: book on behalf of dependants
- [ ] Doctor discovery: search, filters, specialty browse, availability preview
- [ ] Booking wizard: choose doctor → slot → patient → payment mode (self-pay / insurance) → confirm
- [ ] Insurance details capture (provider, policy number, card upload) with eligibility check hooks
- [ ] Payment gateway integration (webhooks, retries, idempotency keys, refunds)
- [ ] Booking confirmation by SMS, email and WhatsApp; add-to-calendar file
- [ ] "My Appointments": upcoming, past, reschedule, cancel
- [ ] Guest-to-registered flow so a visitor from the website can book smoothly
- [ ] Mobile-first responsive layout so the React app works well on phones (installable as a PWA)
- [ ] Push notifications (Firebase) for the mobile app
- [ ] Reviews and ratings after completed visits (moderated)

**Acceptance criteria**
- A new user can go from the home page to a paid, confirmed booking in under 3 minutes
- Failed or abandoned payments release the held slot after a timeout
- Booking appears instantly on the doctor's staff calendar

**Working Increment:** end-to-end online booking, from website to confirmed appointment on the staff calendar.

---

### PHASE 5: Front Desk, OPD & EMR (4 to 5 weeks)

**Goal:** run an outpatient clinic digitally from check-in to consultation.

**Front desk**
- [ ] Patient registration at the desk; **MRN (medical record number)** generation
- [ ] Duplicate patient detection (name, DOB, phone fuzzy match) and merge tool
- [ ] Check-in, token / queue management, live waiting-room display screen
- [ ] Walk-in appointments; consent form capture (e-signature)
- [ ] Patient photo and ID scan

**EMR (doctor workspace)**
- [ ] Patient summary: demographics, allergies, chronic conditions, past visits, active medications
- [ ] Triage and vitals entry (BP, pulse, temperature, SpO2, height, weight, BMI; trend charts)
- [ ] Consultation note: chief complaint, history, examination, assessment (SOAP format)
- [ ] Diagnosis with **ICD-10** search
- [ ] Prescription builder: drug search, dose, frequency, duration, instructions; **drug interaction and allergy warnings**
- [ ] Prescription templates and favourites per doctor
- [ ] Order lab and radiology tests (stubbed until Phase 7)
- [ ] Referrals to other doctors / departments
- [ ] Follow-up scheduling from within the visit
- [ ] Medical certificates, sick notes, referral letters (PDF generation with hospital letterhead)
- [ ] Document attachments (reports, scans)
- [ ] Visit locking and amendment trail (clinical notes must never be silently edited)
- [ ] Full-text patient search

**Acceptance criteria**
- Receptionist registers a patient, checks them in, and the doctor sees them in the queue
- Doctor completes a consultation and prints a prescription PDF
- Every view and edit of a clinical record appears in the audit log

**Working Increment:** a working outpatient department: appointment, check-in, consultation, prescription.

---

### PHASE 6: Billing, Payments & Insurance (3 to 4 weeks)

**Goal:** every service is billed accurately and insurance claims flow.

**Tasks**
- [ ] Services catalog: consultations, procedures, tests, room charges, packages
- [ ] Price lists by payer type (self-pay, insurance network, corporate)
- [ ] Auto-charge capture from consultations, orders and dispensing
- [ ] Invoice generation with taxes / VAT / GST, discounts (with approval), and PDF
- [ ] Payments: cash, card, UPI, online, split payments, deposits and advances
- [ ] Refunds and credit notes with approval workflow
- [ ] Packages (health check, maternity) with pre-paid balance tracking
- [ ] Insurance: payer and plan master, co-pay and deductible rules, pre-authorisation requests
- [ ] Claim creation, submission (manual first, e-claim / API later), tracking, rejection handling, resubmission
- [ ] Accounts receivable and outstanding dashboards
- [ ] Daily cash reconciliation and shift-close report
- [ ] Billing counter UI for the front desk (`RECEPTIONIST`) and `ADMIN`

**Acceptance criteria**
- A visit generates a correct invoice automatically
- Insurance patient: co-pay is collected, claim is generated for the remainder
- Totals reconcile between invoices, payments and the daily report

**Working Increment:** complete OPD revenue cycle, from service to invoice to payment to claim.

---

### PHASE 7: Lab, Radiology & Pharmacy (4 weeks)

**Goal:** close the loop between doctor orders and results/medicines.

**Laboratory (LIS)**
- [ ] Test catalog with reference ranges, units, sample types, TAT
- [ ] Order → sample collection (barcode labels) → processing → result entry → verification → release
- [ ] Abnormal / critical value flagging and doctor alerts
- [ ] Result PDF reports; trend view across visits
- [ ] Optional analyser integration (HL7 / ASTM) as a stretch goal

**Radiology (RIS)**
- [ ] Imaging catalog, scheduling, order tracking
- [ ] Report authoring by radiologist; image / PDF attachments
- [ ] Optional PACS / DICOM viewer integration as a stretch goal

**Pharmacy**
- [ ] Drug master (generic / brand, strength, form), suppliers
- [ ] Stock management: batches, expiry, reorder levels, low-stock and expiry alerts
- [ ] Purchase orders, goods receipt, stock adjustments and transfers between branches
- [ ] Dispensing against prescriptions (partial dispensing, substitutions)
- [ ] OTC / counter sales and returns
- [ ] Pharmacy billing tied into Phase 6

**Acceptance criteria**
- Doctor orders a test and sees the verified result in the patient chart
- Dispensing reduces stock by batch (FEFO: first expired, first out) and creates an invoice line
- Critical results trigger an alert

**Working Increment:** diagnostics and pharmacy fully integrated with the consultation.

---

### PHASE 8: Inpatient, Emergency & Operation Theatre (4 to 5 weeks)

**Goal:** support admitted patients and acute care.

**Emergency**
- [ ] ER registration (including unknown patient), triage scoring, status board
- [ ] Rapid orders and quick-discharge or admit decision

**Inpatient (IPD)**
- [ ] Admission workflow, with insurance pre-auth link
- [ ] Ward / room / bed master, **live bed board** (available, occupied, cleaning, reserved)
- [ ] Bed allocation, transfers, tariffs by room class
- [ ] Doctor rounds and progress notes; nursing notes and care plans
- [ ] Medication administration record (MAR) with staff sign-off
- [ ] Intake / output charts, vitals charts, early-warning scores
- [ ] Diet orders
- [ ] Discharge: summary, medications to take home, follow-up, final bill, clearance checklist
- [ ] ICU / NICU specific views (optional)

**Operation Theatre**
- [ ] OT scheduling by theatre, surgeon, anaesthetist
- [ ] Pre-op checklist, surgical safety checklist, consumables used
- [ ] Operative notes and anaesthesia records

**Acceptance criteria**
- Admit a patient, assign a bed, record rounds and medications, discharge with a summary and a final bill
- Bed board reflects reality in real time (WebSocket updates)
- OT schedule prevents double-booking theatre or surgeon

**Working Increment:** full inpatient journey, from ER to ward to OT to discharge.

---

### PHASE 9: Patient Portal & Telemedicine, Layer 3 (3 weeks)

**Goal:** give patients secure access to their own health data and remote care.

**Patient portal**
- [ ] Unified login with the Phase 4 account
- [ ] Medical history timeline: visits, diagnoses, vitals
- [ ] Lab and radiology reports (download, trend charts)
- [ ] Prescriptions and refill requests
- [ ] Invoices and payment history; pay outstanding bills
- [ ] Vaccination records, allergies, documents upload
- [ ] Consent and sharing: let patients share records with another provider
- [ ] Download my data / delete-account requests (DPDP / GDPR-style rights)
- [ ] Release rules: some results held until the doctor has reviewed them

**Telemedicine**
- [ ] Video consultation room (waiting room, doctor admit, in-call chat, file share)
- [ ] Connection test, fallback to audio
- [ ] E-prescription after the call; post-call summary
- [ ] Recording off by default (consent required if enabled)

**Optional extras:** home care booking, online pharmacy ordering, health-check package purchase.

**Acceptance criteria**
- Patient sees a lab result within minutes of release
- Video call works across desktop and mobile browsers; consult notes land in the EMR
- A patient can only ever see their own data (verified with automated tests)

**Working Increment:** patients self-serve their records, and remote consultations work.

---

### PHASE 10: Admin, Analytics & Integrations (3 weeks)

**Goal:** give management visibility and connect to the outside world.

**Dashboards and reports**
- [ ] Executive dashboard: footfall, revenue, bed occupancy, ALOS, no-show rate, doctor utilisation
- [ ] Department-wise and doctor-wise revenue
- [ ] Clinical quality indicators: readmission rate, infection rate, TAT of lab / radiology
- [ ] Inventory and expiry reports; insurance aging reports
- [ ] Scheduled reports by email (CSV / PDF exports)

**Operations**
- [ ] HR-lite: staff roster, shift scheduling, leave
- [ ] Asset and equipment register with maintenance schedule
- [ ] Housekeeping / bed-cleaning task flow
- [ ] Feedback and complaints management
- [ ] Notification template manager and broadcast tool (campaigns)

**Integrations**
- [ ] Standards: expose / consume **HL7 FHIR** resources (Patient, Appointment, Observation, MedicationRequest)
- [ ] ABDM / ABHA linkage (India) or national health exchange (UAE: Malaffi / Riayati), if applicable
- [ ] Accounting export (Tally / Zoho Books / QuickBooks)
- [ ] Public API with API keys and webhooks for third parties
- [ ] Marketing integrations: analytics, tag manager, CRM

**Acceptance criteria**
- Dashboards load in under 3 seconds on realistic data volumes
- A FHIR Patient and Appointment can be fetched via API with valid credentials

**Working Increment:** management-ready analytics and an integration layer.

---

### PHASE 11: Security, QA & Compliance Hardening (3 weeks)

**Goal:** make it safe and reliable enough for real patient data.

**Security**
- [ ] OWASP Top 10 review; dependency scanning (Dependabot / Snyk); secret scanning
- [ ] Encryption: TLS everywhere, database and storage encryption at rest, field-level encryption for sensitive IDs
- [ ] Break-the-glass access for emergencies, always audited and reviewed
- [ ] Session security: short-lived tokens, device management, idle timeout
- [ ] Rate limiting, bot protection (CAPTCHA), WAF
- [ ] Penetration test (external, if budget allows) and fix findings
- [ ] Backup, restore drills, and a disaster recovery plan (define RPO / RTO)

**Quality**
- [ ] Unit test coverage of 70% or more overall; 90% or more on billing, pharmacy and clinical-safety logic
- [ ] Integration tests for key APIs; end-to-end tests (Playwright, as a test-only tool) for the 10 critical journeys
- [ ] Load testing (k6): 500+ concurrent users on booking; verify no double booking under load
- [ ] Cross-browser and device testing; accessibility audit
- [ ] User acceptance testing with real clinicians, front-desk staff and patients
- [ ] Clinical safety review of drug-interaction, dosing and allergy logic

**Compliance**
- [ ] Data protection impact assessment; consent flows; privacy policy and terms
- [ ] Data retention and deletion policy implemented in code
- [ ] Documentation: user manuals per role, admin guide, API docs

**Acceptance criteria**
- No critical or high vulnerabilities open
- A restore from backup is demonstrated successfully
- UAT sign-off from each role representative

**Working Increment:** a hardened, tested release candidate.

---

### PHASE 12: Deployment, Launch & Maintenance (2 weeks, then ongoing)

**Goal:** go live safely and keep it healthy.

**Tasks**
- [ ] Production infrastructure as code (Terraform / Pulumi); separate staging and production
- [ ] Managed MySQL with automated backups and point-in-time recovery
- [ ] CDN for the website and media; autoscaling for the API
- [ ] Observability: logs (Loki / ELK), metrics (Prometheus / Grafana), error tracking (Sentry), uptime checks and alerts
- [ ] Zero-downtime deploys (blue-green or rolling); database migration runbook
- [ ] Data migration tooling to import existing patients, doctors and price lists (CSV importers)
- [ ] Staff training sessions and quick-reference guides
- [ ] **Pilot launch:** one department or branch for 2 to 4 weeks; gather feedback; fix; then expand
- [ ] Go-live checklist, rollback plan, 24/7 support rota for the first month
- [ ] Post-launch roadmap: mobile app store releases, AI features (see below), more integrations

**Acceptance criteria**
- Successful pilot with no severity-1 incidents for two consecutive weeks
- Monitoring alerts reach the on-call person within 1 minute of an outage

**Working Increment:** the live, final product.

---

## 6. Optional Future Enhancements (Post-Launch)

- AI-assisted triage and symptom checker (with clinician oversight)
- Voice-to-text clinical note dictation
- Smart scheduling to reduce no-shows (prediction)
- Wearable and home-device data ingestion (remote patient monitoring)
- Navigation robots / kiosks and self check-in
- Loyalty and privilege programme (like Aster's partner offers)
- Multi-hospital group management and a central master patient index

---

## 7. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Scope creep | Delays | Mark MVP vs Later in Phase 0; freeze scope per phase |
| Double booking / data integrity bugs | Patient harm, trust loss | DB-level constraints, transactions, concurrency tests (Phase 2) |
| Clinical-safety errors (drug logic) | Patient harm | Use a licensed drug database, clinician review, heavy testing |
| Regulatory non-compliance | Legal exposure | Confirm rules for your country in Phase 0; compliance review in Phase 11 |
| Solo-developer burnout / bus factor | Delays | Keep ADRs and docs current; automate CI; ship small increments |
| Third-party outages (SMS, payment) | Degraded UX | Queue and retry, fallback providers, status page |
| Performance at scale | Slow UX | Index early, cache hot reads, load test in Phase 11 |

---

## 8. Definition of Done (applies to every task)

1. Code reviewed and merged via PR with CI green
2. Unit and integration tests written and passing
3. API documented in OpenAPI; UI matches design tokens
4. Audit logging and permission checks in place for any data access
5. Accessibility and responsive checks done for UI work
6. Migration scripts reversible; seed data updated
7. Demonstrated on staging against the phase's acceptance criteria

---

## 9. Suggested Milestone Map

```
Phase 0-2   →  M1: "Foundation"      Secure backend, scheduling engine, staff calendar
Phase 3-4   →  M2: "Online Presence" Public website + online booking & payment
Phase 5-6   →  M3: "Clinic Ready"    OPD, EMR, billing, insurance: usable by a small clinic
Phase 7-8   →  M4: "Hospital Ready"  Lab, radiology, pharmacy, IPD, ER, OT
Phase 9-10  →  M5: "Complete Ecosystem" Patient portal, telemedicine, analytics, integrations
Phase 11-12 →  M6: "Production"      Hardened, deployed, launched
```

**Note:** at M3 you already have a sellable product for a small clinic. Treat M3 as your first real-world pilot opportunity to get feedback before building the heavier hospital modules.

---

## 10. How to Use This Plan

1. Complete Phase 0 fully before writing feature code.
2. Convert each phase's checklist into tickets (GitHub Projects / Jira / Linear).
3. Work phase by phase; do not start a phase until the previous phase's acceptance criteria pass.
4. At the end of each phase, demo the Working Increment and update this file with what changed.
5. Revisit estimates after Phase 2 and adjust the rest of the plan using your real velocity.
