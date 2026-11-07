# ADR 0003: Double-Booking Prevention (READ_COMMITTED + per-doctor lock + unique key)

## Status
Accepted

## Context
Two clients can attempt to book or reschedule the same doctor into the same time window
concurrently. A check-then-insert in application code alone is not safe under concurrency:

1. Both transactions read "no overlapping appointment" before either inserts.
2. Both then insert, and the doctor ends up double-booked.

The system also creates a **new** appointment row when rescheduling (the old one becomes
`RESCHEDULED`), so the same race applies to reschedules, including a reschedule onto a slot
that another request is booking at the same instant.

## Decision
Prevent double booking with three layers, in this order:

1. **Transaction isolation `READ_COMMITTED`.**
   Booking and reschedule entry points are annotated
   `@Transactional(isolation = Isolation.READ_COMMITTED)`, and `assertReadCommittedIsolation()`
   fails fast if an outer transaction has overridden the isolation level.

2. **Per-doctor pessimistic lock.**
   `DoctorRepository.findByIdForUpdate(doctorId)` uses `@Lock(PESSIMISTIC_WRITE)`, which issues
   `SELECT … FOR UPDATE` on the doctor row. All booking/reschedule attempts for the same doctor
   are therefore serialized: the second transaction blocks until the first commits, then re-runs
   its overlap checks against the committed state.

3. **Database unique-key backstop.**
   `appointments` has a generated column and a unique constraint:

   ```sql
   active_booking_slot VARCHAR(64) GENERATED ALWAYS AS (
       CASE WHEN status NOT IN ('CANCELLED', 'RESCHEDULED')
            THEN CONCAT(doctor_id, '_', DATE_FORMAT(start_time, '%Y%m%d%H%i%s'))
            ELSE NULL END
   ) STORED,
   CONSTRAINT uk_active_doctor_slot UNIQUE (active_booking_slot)
   ```

   One doctor can have at most one *active* appointment per start instant. Cancelled and
   rescheduled rows produce `NULL` (MySQL allows multiple `NULL`s in a unique index), so history
   rows never block new bookings.

Reschedule marks the existing appointment `RESCHEDULED` (which flips its
`active_booking_slot` to `NULL`) **before** inserting the new row, so the new row can take the
slot without colliding with the row it replaces.

## The real MySQL 1062 bug and its fix
Under concurrency on a real MySQL 8.0 (the H2 test profile cannot reproduce it), a racing
insert that slips past the application checks hits the unique constraint:

```
SQL Error: 1062, SQLState: 23000
Duplicate entry '246_20261114103000' for key 'appointments.uk_active_doctor_slot'
```

**Bug:** `DataIntegrityViolationException` was not translated, so the conflict surfaced to the
client as an **HTTP 500** instead of a conflict.

**Fix:** `GlobalExceptionHandler` handles `DataIntegrityViolationException` and returns
**409 Conflict** with a generic message (`"The requested operation conflicts with existing data
or state"`). The reschedule path also marks the old appointment `RESCHEDULED` before inserting
the new one, so the unique key only fires for genuine double-booking races.

`AppointmentConcurrencyTest` (profile `mysql-test`) proves the behaviour: concurrent booking and
reschedule scenarios must produce exactly one success and the rest `ConflictException` — with
zero 1062 errors across the worker threads — and Scenario 6 performs a direct duplicate insert
to prove `uk_active_doctor_slot` is the backstop.

## Consequences
- A double booking is impossible: the unique key rejects it even if the application checks race.
- A losing concurrent request receives a clean 409, never a 500.
- The per-doctor lock limits throughput per doctor (acceptable: booking is low-volume and
  intentionally serialized per doctor).
- The unique key is tied to `start_time`, not the full interval; overlap within the same doctor
  is still enforced by the application-level overlap check while the row lock is held.
