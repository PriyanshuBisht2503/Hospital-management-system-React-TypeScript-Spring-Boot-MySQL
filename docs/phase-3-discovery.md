# Phase 3 — Patient-Facing MVP Discovery

Read-only findings from the current backend (`apps/api`). Short on purpose.

## 1. PATIENT authentication (OTP)

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/otp/send` | `{ target }` (email/phone) | `{ message }` (uniform) |
| POST | `/api/auth/otp/verify` | `{ target, code: "123456" }` | `AuthResponse { accessToken, tokenType, expiresIn, user }` + `hms_refresh_token` cookie |
| POST | `/api/auth/register-patient` | `{ fullName, email, phone, password }` | `{ message }` (201, sends OTP) |
| GET | `/api/auth/me` | — | `UserSummaryDto` |

- First verified OTP **auto-provisions** a `PATIENT` `User` (`AuthService.verifyOtp`), then **creates or links a `Patient` row** (rule 10) by matching email/phone.
- JWT claims: `sub` = user id, `email`, `role`, `fullName`, `iat`, `exp` (30 min). **No patientId / doctorId.**
- `GET /api/auth/me` returns `{ id, email, phone, role, fullName, enabled, doctorId }` — **no patientId**.
- OTP rate limits: send 5/min IP + 3/10 min target; verify 5/min IP + max attempts.

## 2. Public (permitAll) endpoints

`SecurityConfig`: `GET /api/doctors`, `/api/doctors/**`, `/api/branches`, `/api/branches/**`, `/api/departments`, `/api/departments/**`, `/api/specialties`, `/api/specialties/**`, `/api/files/**`.

- `GET /api/branches|/{id}` → `BranchDto { id, name, code, address, city, phone, email, isActive }`
- `GET /api/departments|/{id}` → `DepartmentDto { id, name, code, description, isActive }`
- `GET /api/specialties|/{id}` → `SpecialtyDto { id, name, code, description, isActive }`
- `GET /api/doctors` → `PageResponse<DoctorPublicDto>` (`specialtyId`, `branchId`, `search`, page/size/sort, default size 10)
- `GET /api/doctors/search` → `PageResponse<DoctorSearchPublicDto>` (same + `nextAvailableSlot`, default size 6)
- `GET /api/doctors/{id}` → `DoctorPublicDto`
- `GET /api/doctors/{id}/slots` → `List<AvailableSlotDto>` (`branchId`, `date` or `from`/`to`)
- `DoctorPublicDto = { id, fullName, bio, qualifications, languages, experienceYears, photoUrl, consultationFee, specialties, branches, isActive }`
- `AvailableSlotDto = { doctorId, branchId, startTime, endTime, slotMinutes }` (UTC instants)
- **No department on a doctor** (Doctor has only specialties + branches; Department is a standalone entity).

## 3. Patient booking

- `POST /api/appointments` — `ADMIN`, `RECEPTIONIST`, `PATIENT` (**not DOCTOR**). Body `{ patientId, doctorId, branchId, startTime, endTime, type, reasonForVisit? }`.
- `patientId` comes from the **body**, then the controller verifies it belongs to the caller (else 404). It is **not** taken from the token.
- Patient bookings start `BOOKED`; staff `CONFIRMED`.
- **No booking rate limit** exists (only auth limits).

## 4. Existing patient self-service

- `GET /api/appointments` — scoped to the caller's `patientId` (server-derived); returns `PageResponse<AppointmentResponseDto>`, sorted by `startTime` ASC.
- `GET /api/appointments/{id}` — 404 cloaking via `verifyPatientMatch`.
- `PATCH /api/appointments/{id}/cancel` — body `{ reason }`; 404 cloaking; patients cannot cancel after start.
- `POST /api/appointments/{id}/reschedule` — **no patient ownership check** (grep found no `verifyPatientMatch` in `AppointmentBookingService`) → IDOR.

## 5. Backend gaps to close (Stage 1)

1. `patientId` missing from `UserSummaryDto` → patient cannot book (body needs it).
2. `GET /api/appointments/me` — newest-first, paged, `status` + `upcoming` filters.
3. `GET /api/patients/me` + `PUT /api/patients/me` — editable name/DOB/gender/phone (email + ids immutable).
4. Reschedule IDOR — enforce patient ownership.
5. Booking rate limit.
6. Restrict patient cancel to `BOOKED`/`CONFIRMED` (state machine currently allows more).
7. Doctor card `/api/doctors` is missing `department` — **not modelled**; requires a schema change, so left out of this phase.

## 6. Frontend reality

- `apps/web-public` and `apps/web-patient` are single static mock pages (no router, auth, or API layer; only `@hms/ui` + React).
- Both Vite dev servers proxy `/api` → `localhost:4000` (ports 3000 and 3001).
- Reusable from `web-staff`: `api/client.ts`, `api/tokenStore.ts`, `types/*`, `lib/time.ts`, `lib/appointmentStatus.ts`, `StatusBadge`, `SlotPicker`.

## 7. Dev data

`DevDataSeeder` seeds 2 branches, 3 departments, 3 specialties, 4 demo users and **1 doctor** — but **0 schedules, 0 patients, 0 appointments**. Slots therefore never appear in dev until Stage 4.
