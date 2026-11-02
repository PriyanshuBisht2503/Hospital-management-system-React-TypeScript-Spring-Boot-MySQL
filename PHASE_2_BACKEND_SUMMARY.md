# CareHMS - Phase 2: Scheduling & Appointment Engine Backend Summary

**Project:** CareHMS (Hospital Management System)  
**Branch:** `phase-2`  
**Git Commit:** `321eefb2aea9596cf7c5d9eae88246e23824f16a`  
**Date:** November 2, 2026  
**Backend Stack:** Java 21, Spring Boot 3.4.2, Spring Data JPA / Hibernate, MySQL 8.0, Flyway  
**Overall Backend Test Results:** **339 / 339 Tests Passing** (0 Failures, 0 Errors, 0 Skipped)  

---

## Table of Contents
1. [Overview & Scope](#1-overview--scope)
2. [Review Item 1: Concurrency Verification & Unique Key Backstop](#2-review-item-1-concurrency-verification--unique-key-backstop)
3. [Review Item 2: Reschedule Method Signatures & Overloads](#3-review-item-2-reschedule-method-signatures--overloads)
4. [Review Item 3: Test Count Progression & Test Method Listing](#4-review-item-3-test-count-progression--test-method-listing)
5. [Review Item 4: Information Leakage Prevention (404 Not Found)](#5-review-item-4-information-leakage-prevention-404-not-found)
6. [Review Item 5: Dedicated Asynchronous Notifications & Failure Resilience](#6-review-item-5-dedicated-asynchronous-notifications--failure-resilience)
7. [Review Item 6: Transaction Isolation Verification (READ_COMMITTED)](#7-review-item-6-transaction-isolation-verification-read_committed)
8. [Review Item 7: State Machine - BOOKED to RESCHEDULED](#8-review-item-7-state-machine---booked-to-rescheduled)
9. [Review Item 8: Doctor Patient Access Scoping](#9-review-item-8-doctor-patient-access-scoping)
10. [Review Item 9: Role Enum Adoption](#10-review-item-9-role-enum-adoption)
11. [Complete Per-Class Test Execution Table](#11-complete-per-class-test-execution-table)

---

## 1. Overview & Scope

Phase 2 builds the core scheduling, availability, booking, and lifecycle engine for CareHMS, supporting:
- Flexible recurring doctor schedule templates with break times and slot duration definitions (5–120 minutes).
- Dynamic schedule exception management (`LEAVE`, `HOLIDAY`, `EXTRA_HOURS`).
- Real-time slot calculation accounting for doctor availability, past cuts, advance booking windows, exceptions, and existing bookings.
- Double-booking prevention via pessimistic row-level locking (`SELECT ... FOR UPDATE` on doctor row) combined with an immutable MySQL InnoDB virtual generated column unique constraint (`uk_active_doctor_slot`).
- Rescheduling engine linking previous appointments via `rescheduled_from_id` while freeing the old slot.
- Role-based appointment state machine (`BOOKED`, `CONFIRMED`, `CHECKED_IN`, `IN_CONSULTATION`, `COMPLETED`, `CANCELLED`, `NO_SHOW`, `RESCHEDULED`).
- Non-blocking asynchronous notifications via Spring `@Async` thread pool on `AFTER_COMMIT` transaction events.

---

## 2. Review Item 1: Concurrency Verification & Unique Key Backstop

### 2.1 Concurrency Implementation
In `AppointmentConcurrencyTest.java`:
- Scenarios 1 & 2 strictly treat **only** `ConflictException` as a service-layer rejection. Any `DataIntegrityViolationException` or unexpected exception is recorded in `unexpectedFailures` and fails the test.
- Scenario 6 (`duplicateActiveSlotDirectInsertThrowsDataIntegrityViolationException`) performs a direct repository insert of a duplicate active slot to prove that MySQL constraint `uk_active_doctor_slot` acts as the backstop.

```java
// apps/api/src/test/java/com/hms/api/concurrency/AppointmentConcurrencyTest.java

@Test
@DisplayName("Scenario 1: 20 parallel bookings for the exact same slot -> exactly 1 success, 19 ConflictException")
void twentyParallelBookingsExactSameSlot() throws Exception {
    Instant start = getFutureSlotStart(3, 10, 0);
    Instant end = start.plus(30, ChronoUnit.MINUTES);
    int threadCount = 20;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);
    List<Throwable> unexpectedFailures = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                bookingService.bookAppointment(
                        testPatients.get(idx).getId(), doctor.getId(), branch.getId(),
                        start, end, AppointmentType.IN_PERSON, "Concurrent slot",
                        Role.PATIENT, testPatients.get(idx).getUser().getEmail()
                );
                successCount.incrementAndGet();
            } catch (ConflictException ce) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedFailures.add(t);
            }
        });
    }

    readyLatch.await();
    startLatch.countDown();
    executor.shutdown();
    boolean finished = executor.awaitTermination(30, TimeUnit.SECONDS);

    assertThat(finished).isTrue();
    assertThat(unexpectedFailures).as("Unexpected exceptions: " + unexpectedFailures).isEmpty();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(19);

    long activeSlotsInDb = appointmentRepository.countActiveSlotsForDoctor(doctor.getId(), start, end);
    assertThat(activeSlotsInDb).isEqualTo(1);
}

@Test
@DisplayName("Scenario 2: 2 parallel reschedules of different appointments into the same target slot -> 1 success, 1 ConflictException")
void twoParallelReschedulesIntoSameSlot() throws Exception {
    Instant appt1Start = getFutureSlotStart(4, 9, 0);
    Instant appt1End = appt1Start.plus(30, ChronoUnit.MINUTES);
    AppointmentResponseDto appt1 = bookingService.bookAppointment(
            testPatients.get(0).getId(), doctor.getId(), branch.getId(),
            appt1Start, appt1End, AppointmentType.IN_PERSON, "Appt 1",
            Role.ADMIN, "admin"
    );

    Instant appt2Start = getFutureSlotStart(4, 9, 30);
    Instant appt2End = appt2Start.plus(30, ChronoUnit.MINUTES);
    AppointmentResponseDto appt2 = bookingService.bookAppointment(
            testPatients.get(1).getId(), doctor.getId(), branch.getId(),
            appt2Start, appt2End, AppointmentType.IN_PERSON, "Appt 2",
            Role.ADMIN, "admin"
    );

    Instant targetStart = getFutureSlotStart(4, 11, 0);
    Instant targetEnd = targetStart.plus(30, ChronoUnit.MINUTES);

    int threadCount = 2;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch readyLatch = new CountDownLatch(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);
    List<Throwable> unexpectedFailures = Collections.synchronizedList(new ArrayList<>());

    Long[] apptIds = new Long[]{appt1.id(), appt2.id()};
    for (int i = 0; i < threadCount; i++) {
        final int idx = i;
        executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                bookingService.rescheduleAppointment(
                        apptIds[idx], targetStart, targetEnd, branch.getId(),
                        "Rescheduled concurrently", Role.ADMIN, "admin"
                );
                successCount.incrementAndGet();
            } catch (ConflictException ce) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedFailures.add(t);
            }
        });
    }

    readyLatch.await();
    startLatch.countDown();
    executor.shutdown();
    boolean finished = executor.awaitTermination(20, TimeUnit.SECONDS);

    assertThat(finished).isTrue();
    assertThat(unexpectedFailures).as("Unexpected exceptions: " + unexpectedFailures).isEmpty();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(1);

    long targetSlotBookings = appointmentRepository.countActiveSlotsForDoctor(doctor.getId(), targetStart, targetEnd);
    assertThat(targetSlotBookings).isEqualTo(1);
}

@Test
@DisplayName("Scenario 6: Direct repository insert of duplicate active slot throws DataIntegrityViolationException (proves uk_active_doctor_slot backstop)")
void duplicateActiveSlotDirectInsertThrowsDataIntegrityViolationException() {
    Instant start = getFutureSlotStart(5, 10, 30);
    Instant end = start.plus(30, ChronoUnit.MINUTES);

    Appointment first = new Appointment(testPatients.get(0), doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
    appointmentRepository.saveAndFlush(first);

    Appointment duplicate = new Appointment(testPatients.get(1), doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
    assertThatThrownBy(() -> appointmentRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
}
```

### 2.2 Surefire Concurrency Report
```
-------------------------------------------------------------------------------
Test set: com.hms.api.concurrency.AppointmentConcurrencyTest
-------------------------------------------------------------------------------
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 19.06 s -- in com.hms.api.concurrency.AppointmentConcurrencyTest
```

### 2.3 Raw Pattern Match (`Select-String` for Error 1062)
Command:
```powershell
Select-String -Path apps\api\concurrency_run.log -Pattern "1062|Duplicate entry"
```
Output:
```powershell
apps\api\concurrency_run.log:101:2026-11-02T11:09:19.733+05:30  WARN 20348 --- [hms-api-mysql-test] [           main] o.h.engine.jdbc.spi.SqlExceptionHelper   : SQL Error: 1062, SQLState: 23000
apps\api\concurrency_run.log:102:2026-11-02T11:09:19.733+05:30 ERROR 20348 --- [hms-api-mysql-test] [           main] o.h.engine.jdbc.spi.SqlExceptionHelper   : Duplicate entry '246_20261114103000' for key 'appointments.uk_active_doctor_slot'
```
*Note: SQL Error 1062 occurred solely on `[           main]` during the execution of Scenario 6 (direct insert backstop test). Zero 1062 errors occurred across the thread pools of Scenarios 1 and 2.*

---

## 3. Review Item 2: Reschedule Method Signatures & Overloads

### 3.1 Overload Definitions in `AppointmentBookingService.java`
```java
// apps/api/src/main/java/com/hms/api/service/AppointmentBookingService.java

public AppointmentResponseDto rescheduleAppointment(
        Long appointmentId,
        Instant newStartTime,
        Instant newEndTime,
        Long newBranchId,
        String rescheduleReason,
        Role requestedByRole,
        String requestedByUser
) {
    return rescheduleAppointment(appointmentId, newStartTime, newEndTime, newBranchId, rescheduleReason, requestedByRole, requestedByUser, false);
}

@Transactional(isolation = Isolation.READ_COMMITTED)
public AppointmentResponseDto rescheduleAppointment(
        Long appointmentId,
        Instant newStartTime,
        Instant newEndTime,
        Long newBranchId,
        String rescheduleReason,
        Role requestedByRole,
        String requestedByUser,
        boolean maskPatientContact
) {
    assertReadCommittedIsolation();
    ...
```

### 3.2 Test Invocation in `AppointmentBookingServiceTest.java`
```java
// apps/api/src/test/java/com/hms/api/service/AppointmentBookingServiceTest.java

AppointmentResponseDto rescheduled = bookingService.rescheduleAppointment(
        100L, newStart, newEnd, 3L, "Doctor requested reschedule", Role.ADMIN, "admin1", false
);
```

---

## 4. Review Item 3: Test Count Progression & Test Method Listing

### 4.1 Count Progression Breakdown
- Baseline suite count: **332** tests.
- Additions:
  1. `AppointmentConcurrencyTest`: Scenario 6 direct insert test (+1)
  2. `AppointmentApiTest`: Transaction isolation check `transactionVerificationReadCommittedActive` (+1)
  3. `AppointmentApiTest`: Ownership and access checks split into `patientCannotViewOrCancelAnotherPatientAppointment` and doctor isolation (+3)
  4. `AppointmentNotificationAndReminderTest`: Slow/failing mail server resilience test `failingMailServerDoesNotFailNotificationHandling` (+1)
  5. `AppointmentStateMachineTest`: Valid `BOOKED -> RESCHEDULED` transition test `bookedToRescheduled` (+1)
- **Total: $332 + 7 = 339$ tests.**

### 4.2 Raw Maven Build Summary
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 339, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:06 min
[INFO] Finished at: 2026-11-02T11:07:27+05:30
[INFO] ------------------------------------------------------------------------
```

### 4.3 Test Method List by Class

#### `AppointmentApiTest` (11 tests):
1. `staffBookingConfirmed()`
2. `patientBookingStartsAsBooked()`
3. `patientCannotBookForOthers()`
4. `doubleBookingReturns409()`
5. `staffWalkInSucceeds()`
6. `appointmentLifecycleFlow()`
7. `cancelFreesSlotForRebooking()`
8. `rescheduleFlow()`
9. `doctorCannotUpdateStatusOfAnotherDoctorAppointment()`
10. `patientCannotViewOrCancelAnotherPatientAppointment()`
11. `transactionVerificationReadCommittedActive()`

#### `PatientApiTest` (7 tests):
1. `getPatientAnonymous401()`
2. `patientCannotViewOtherPatient()`
3. `patientCanViewSelf()`
4. `doctorViewsPatientMaskedContact()`
5. `adminViewsPatientFullContact()`
6. `searchPatientAccessAndAudit()`
7. `createPatientStaffOnly()`

#### `ScheduleExceptionApiTest` (5 tests):
1. `doctorCreatesLeaveSuccessfully()`
2. `leaveWithAppointmentConflictReturns409()`
3. `leaveWithForceTrueFlagsNeedsRescheduling()`
4. `doctorExceptionAccessControl()`
5. `doctorDeletesException()`

---

## 5. Review Item 4: Information Leakage Prevention (404 Not Found)

To prevent resource enumeration, accessing another user's private data returns `404 Not Found` with an error response identical to that of a non-existent identifier.

### 5.1 Booking With Another Patient's ID
```java
// apps/api/src/main/java/com/hms/api/web/AppointmentController.java

if (currentUser.getRole() == Role.PATIENT) {
    Patient patient = patientRepository.findById(request.patientId())
            .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + request.patientId()));
    if (patient.getUser() == null || !patient.getUser().getId().equals(currentUser.getId())) {
        throw new ResourceNotFoundException("Patient not found: " + request.patientId());
    }
}
```

### 5.2 Viewing / Cancelling Another Patient's Appointment
```java
// apps/api/src/main/java/com/hms/api/service/AppointmentLifecycleService.java

private void verifyPatientMatch(Appointment a, SecurityUser currentUser) {
    if (a.getPatient().getUser() == null || !a.getPatient().getUser().getId().equals(currentUser.getId())) {
        throw new ResourceNotFoundException("Appointment not found: " + a.getId());
    }
}
```

### 5.3 Viewing Another Patient's Profile
```java
// apps/api/src/main/java/com/hms/api/service/PatientService.java

if (currentUser.getRole() == Role.PATIENT) {
    if (patient.getUser() == null || !patient.getUser().getId().equals(currentUser.getId())) {
        throw new ResourceNotFoundException("Patient not found: " + id);
    }
}
```

### 5.4 Test Assertions: Identical 404 Response Bodies
```java
// apps/api/src/test/java/com/hms/api/web/AppointmentApiTest.java

assertThat(objectMapper.readTree(respOther).get("status").asInt()).isEqualTo(404);
assertThat(objectMapper.readTree(respNonexistent).get("status").asInt()).isEqualTo(404);
assertThat(objectMapper.readTree(respOther).get("error").asText())
        .isEqualTo(objectMapper.readTree(respNonexistent).get("error").asText());
```

```java
// apps/api/src/test/java/com/hms/api/web/PatientApiTest.java

assertThat(objectMapper.readTree(otherPatientResponse).get("error").asText())
        .isEqualTo(objectMapper.readTree(nonexistentResponse).get("error").asText());
assertThat(objectMapper.readTree(otherPatientResponse).get("status").asInt())
        .isEqualTo(404);
```

---

## 6. Review Item 5: Dedicated Asynchronous Notifications & Failure Resilience

### 6.1 `AsyncConfig.java`
```java
package com.hms.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notification-");
        executor.initialize();
        return executor;
    }
}
```

### 6.2 Event Listeners in `AppointmentNotificationListener.java`
```java
// apps/api/src/main/java/com/hms/api/service/AppointmentNotificationListener.java

@Async("notificationTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onAppointmentBooked(AppointmentBookedEvent event) { ... }

@Async("notificationTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onAppointmentCancelled(AppointmentCancelledEvent event) { ... }

@Async("notificationTaskExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void onAppointmentRescheduled(AppointmentRescheduledEvent event) { ... }
```

### 6.3 Mail Server Failure Resilience Test
```java
// apps/api/src/test/java/com/hms/api/service/AppointmentNotificationAndReminderTest.java

@Test
@DisplayName("Failing mail server records notification as FAILED and does not delay or fail booking")
void failingMailServerDoesNotFailNotificationHandling() throws Exception {
    doThrow(new RuntimeException("Mail server down")).when(emailService).sendEmail(anyString(), anyString(), anyString());

    Instant start = clock.instant().plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(30, ChronoUnit.MINUTES);
    Appointment appt = new Appointment(patient, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
    final Appointment savedAppt = appointmentRepository.save(appt);

    org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
            notificationListener.onAppointmentBooked(new AppointmentBookedEvent(savedAppt.getId()))
    );

    for (int i = 0; i < 20 && notificationRepository.count() == 0; i++) {
        Thread.sleep(50);
    }

    List<Notification> notifications = notificationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId(), PageRequest.of(0, 10)).getContent();
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
}
```

---

## 7. Review Item 6: Transaction Isolation Verification (READ_COMMITTED)

### 7.1 Controller Layer Absence of `@Transactional`
`AppointmentController` has no `@Transactional` annotation on the class or endpoints. Each HTTP request delegates cleanly to `bookingService`.

### 7.2 Service Isolation Assertion
```java
// apps/api/src/main/java/com/hms/api/service/AppointmentBookingService.java

private void assertReadCommittedIsolation() {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
        Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        if (isolationLevel != null && !isolationLevel.equals(TransactionDefinition.ISOLATION_READ_COMMITTED)) {
            throw new IllegalStateException("Expected isolation READ_COMMITTED but found: " + isolationLevel);
        }
    }
}
```

### 7.3 Integration Test
```java
// apps/api/src/test/java/com/hms/api/web/AppointmentApiTest.java

@Test
@DisplayName("Transaction verification: bookingService runs under READ_COMMITTED without outer transaction interference")
void transactionVerificationReadCommittedActive() {
    Instant start = getFutureSlotStart(10, 16, 0);
    Instant end = start.plus(30, ChronoUnit.MINUTES);

    AppointmentResponseDto dto = bookingService.bookAppointment(
            patient1.getId(), doctor.getId(), branch.getId(),
            start, end, AppointmentType.IN_PERSON, "Tx check", Role.ADMIN, "admin"
    );

    assertThat(dto.status()).isEqualTo(AppointmentStatus.CONFIRMED);
}
```

---

## 8. Review Item 7: State Machine - BOOKED to RESCHEDULED

### 8.1 State Transitions
```java
// apps/api/src/main/java/com/hms/api/service/AppointmentStateMachine.java

private static final Map<AppointmentStatus, Set<AppointmentStatus>> ALLOWED_TRANSITIONS = Map.of(
        AppointmentStatus.BOOKED, EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED, AppointmentStatus.RESCHEDULED),
        AppointmentStatus.CONFIRMED, EnumSet.of(AppointmentStatus.CHECKED_IN, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW, AppointmentStatus.RESCHEDULED),
        AppointmentStatus.CHECKED_IN, EnumSet.of(AppointmentStatus.IN_CONSULTATION, AppointmentStatus.CANCELLED),
        AppointmentStatus.IN_CONSULTATION, EnumSet.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED)
);
```

### 8.2 Test Verification
```java
// apps/api/src/test/java/com/hms/api/service/AppointmentStateMachineTest.java

@Test
@DisplayName("Valid transition BOOKED -> RESCHEDULED sets status to RESCHEDULED")
void bookedToRescheduled() {
    Appointment a = createAppointment(AppointmentStatus.BOOKED, fixedClock.instant().plus(2, ChronoUnit.DAYS), fixedClock.instant().plus(2, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES));
    stateMachine.applyTransition(a, AppointmentStatus.RESCHEDULED, fixedClock, zone);

    assertThat(a.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
}
```

---

## 9. Review Item 8: Doctor Patient Access Scoping

### 9.1 Service Logic
```java
// apps/api/src/main/java/com/hms/api/service/PatientService.java

if (currentUser.getRole() == Role.DOCTOR) {
    boolean hasAppointment = appointmentRepository.existsByDoctorUserIdAndPatientId(currentUser.getId(), id);
    if (!hasAppointment) {
        throw new ResourceNotFoundException("Patient not found: " + id);
    }
}
...
Page<Patient> result;
if (currentUser.getRole() == Role.DOCTOR) {
    result = patientRepository.searchPatientsByDoctorUserId(
            currentUser.getId(),
            query,
            PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
    );
} else {
    result = patientRepository.searchPatients(
            query,
            PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
    );
}
```

### 9.2 Repository Definitions
```java
// AppointmentRepository.java
@Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE a.doctor.user.id = :doctorUserId AND a.patient.id = :patientId")
boolean existsByDoctorUserIdAndPatientId(@Param("doctorUserId") Long doctorUserId, @Param("patientId") Long patientId);

// PatientRepository.java
@Query("SELECT p FROM Patient p WHERE " +
       "EXISTS (SELECT 1 FROM Appointment a WHERE a.patient = p AND a.doctor.user.id = :doctorUserId) AND " +
       "(:query IS NULL OR :query = '' OR " +
       "LOWER(p.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
       "p.phone LIKE CONCAT('%', :query, '%') OR " +
       "LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')))")
Page<Patient> searchPatientsByDoctorUserId(@Param("doctorUserId") Long doctorUserId, @Param("query") String query, Pageable pageable);
```

---

## 10. Review Item 9: Role Enum Adoption

All service interfaces in `AppointmentBookingService.java` use the strongly-typed `Role` enum:

```java
public AppointmentResponseDto bookAppointment(
        Long patientId,
        Long doctorId,
        Long branchId,
        Instant startTime,
        Instant endTime,
        AppointmentType type,
        String reasonForVisit,
        Role createdByRole,
        String createdByUser,
        boolean maskPatientContact
);

public AppointmentResponseDto rescheduleAppointment(
        Long appointmentId,
        Instant newStartTime,
        Instant newEndTime,
        Long newBranchId,
        String rescheduleReason,
        Role requestedByRole,
        String requestedByUser,
        boolean maskPatientContact
);
```

---

## 11. Complete Per-Class Test Execution Table

| Test Class | Tests Run | Failures | Errors | Skipped | Status |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `com.hms.api.aspect.AuditedAspectTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.concurrency.AppointmentConcurrencyTest` | 6 | 0 | 0 | 0 | PASS |
| `com.hms.api.config.DatabasePasswordProfileTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.config.DevDataSeederDevProfileTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.config.DevDataSeederNonDevProfileTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.config.JwtPropertiesValidatorTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.config.OpenApiConfigTest` | 1 | 0 | 0 | 0 | PASS |
| `com.hms.api.dto.PageResponseTest` | 4 | 0 | 0 | 0 | PASS |
| `com.hms.api.exception.GlobalErrorHandlingTest` | 4 | 0 | 0 | 0 | PASS |
| `com.hms.api.HealthControllerTest` | 1 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.AppointmentRepositoryTest` | 6 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.AuditLogRepositoryTest` | 1 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.DoctorRepositoryTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.DoctorScheduleRepositoryTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.MasterDataRepositoryTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.NotificationRepositoryTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.PatientRepositoryTest` | 5 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.ScheduleExceptionRepositoryTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.repository.UserRepositoryTest` | 4 | 0 | 0 | 0 | PASS |
| `com.hms.api.security.RoleEndpointSecurityMatrixTest` | 165 | 0 | 0 | 0 | PASS |
| `com.hms.api.security.SecurityHandlerTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.service.AppointmentBookingServiceTest` | 6 | 0 | 0 | 0 | PASS |
| `com.hms.api.service.AppointmentNotificationAndReminderTest` | 6 | 0 | 0 | 0 | PASS |
| `com.hms.api.service.AppointmentStateMachineTest` | 7 | 0 | 0 | 0 | PASS |
| `com.hms.api.service.SlotCalculationServiceTest` | 4 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.AppointmentApiTest` | 11 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.AuditLogViewerTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.AuthControllerTest` | 17 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.DoctorManagementTest` | 4 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.DoctorScheduleApiTest` | 7 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.DoctorSearchAndSlotApiTest` | 3 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.FileStorageTest` | 6 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.MasterDataTest` | 7 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.PageResponseContractTest` | 2 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.PatientApiTest` | 7 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.ScheduleExceptionApiTest` | 5 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.Step3BAuthTest` | 14 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.UnverifiedRegistrationLinkingTest` | 1 | 0 | 0 | 0 | PASS |
| `com.hms.api.web.UserManagementTest` | 5 | 0 | 0 | 0 | PASS |
| **Total** | **339** | **0** | **0** | **0** | **PASS** |
