package com.hms.api.concurrency;

import com.hms.api.domain.*;
import com.hms.api.dto.AppointmentResponseDto;
import com.hms.api.exception.ConflictException;
import com.hms.api.repository.*;
import com.hms.api.service.AppointmentBookingService;
import com.hms.api.service.AppointmentLifecycleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("mysql-test")
class AppointmentConcurrencyTest {

    @Autowired
    private AppointmentBookingService bookingService;

    @Autowired
    private AppointmentLifecycleService lifecycleService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    private Doctor doctor;
    private Branch branch;

    @BeforeAll
    static void verifyDedicatedTestDatabase(@Autowired DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            if (!url.toLowerCase().contains("test")) {
                throw new IllegalStateException(
                        "CRITICAL: Concurrency test must run against a database containing 'test' in JDBC URL! Found: " + url
                );
            }
        }
    }

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        patientRepository.deleteAll();
        doctorRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();

        branch = branchRepository.save(new Branch("Apex Hospital", "APEX01", "Ring Rd", "Pune", "+912011112222", "apex@example.com"));

        User docUser = userRepository.save(new User("doc.conc@example.com", "+919700000001", "pass", Role.DOCTOR, "Dr. Concurrency"));
        doctor = doctorRepository.save(new Doctor(docUser, "Specialist", "MD", "English", 12, new BigDecimal("700.00")));

        // Set up doctor schedule for all days of week, 08:00 to 20:00, 30 min slots
        for (DayOfWeek dow : DayOfWeek.values()) {
            DoctorSchedule sched = new DoctorSchedule(
                    doctor, branch, dow,
                    LocalTime.of(8, 0), LocalTime.of(20, 0),
                    30, LocalDate.now().minusDays(10)
            );
            scheduleRepository.save(sched);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        // Allow in-flight async background notification tasks to complete before tearing down
        Thread.sleep(300);
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        scheduleRepository.deleteAll();
        patientRepository.deleteAll();
        doctorRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Patient createPatient(String name, String phone, String email) {
        return patientRepository.save(new Patient(name, phone, email, LocalDate.of(1990, 1, 1), Gender.MALE));
    }

    @Test
    @DisplayName("Scenario 1: 20 parallel bookings for identical slot -> exactly 1 success, 19 conflicts")
    void twentyParallelBookingsForIdenticalSlot() throws Exception {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Patient> patients = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            patients.add(createPatient("Patient " + i, "+9197000010" + String.format("%02d", i), "p" + i + "@example.com"));
        }

        LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5);
        Instant slotStart = LocalDateTime.of(targetDate, LocalTime.of(10, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant slotEnd = slotStart.plus(30, ChronoUnit.MINUTES);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedFailures = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final Patient p = patients.get(i);
            futures.add(executor.submit(() -> {
                try {
                    barrier.await();
                    bookingService.bookAppointment(
                            p.getId(), doctor.getId(), branch.getId(),
                            slotStart, slotEnd, AppointmentType.IN_PERSON,
                            "Concurrency test", Role.ADMIN, "admin"
                    );
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getCause() instanceof ConflictException) {
                        conflictCount.incrementAndGet();
                    } else {
                        unexpectedFailures.add(e);
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(unexpectedFailures).as("No unexpected database/integrity exceptions allowed in service concurrency").isEmpty();
        assertThat(successCount.get())
                .as("Exactly ONE booking must succeed for identical slot")
                .isEqualTo(1);

        assertThat(conflictCount.get())
                .as("Remaining 19 attempts must be rejected with ConflictException")
                .isEqualTo(19);

        long activeApptCount = appointmentRepository.count();
        assertThat(activeApptCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario 2: 2 parallel reschedules into one slot -> exactly 1 success, 1 conflict")
    void twoParallelReschedulesIntoOneSlot() throws Exception {
        Patient p1 = createPatient("Patient Resched 1", "+919700002001", "pr1@example.com");
        Patient p2 = createPatient("Patient Resched 2", "+919700002002", "pr2@example.com");

        LocalDate date1 = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(4);
        Instant oldStart1 = LocalDateTime.of(date1, LocalTime.of(9, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant oldEnd1 = oldStart1.plus(30, ChronoUnit.MINUTES);

        Instant oldStart2 = LocalDateTime.of(date1, LocalTime.of(10, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant oldEnd2 = oldStart2.plus(30, ChronoUnit.MINUTES);

        AppointmentResponseDto appt1 = bookingService.bookAppointment(
                p1.getId(), doctor.getId(), branch.getId(), oldStart1, oldEnd1, AppointmentType.IN_PERSON, "Visit 1", Role.ADMIN, "admin"
        );
        AppointmentResponseDto appt2 = bookingService.bookAppointment(
                p2.getId(), doctor.getId(), branch.getId(), oldStart2, oldEnd2, AppointmentType.IN_PERSON, "Visit 2", Role.ADMIN, "admin"
        );

        // Target new slot: date1 at 14:00
        Instant targetStart = LocalDateTime.of(date1, LocalTime.of(14, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant targetEnd = targetStart.plus(30, ChronoUnit.MINUTES);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedFailures = new CopyOnWriteArrayList<>();

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                bookingService.rescheduleAppointment(
                        appt1.id(), targetStart, targetEnd, branch.getId(), "Reschedule 1", Role.ADMIN, "admin"
                );
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                if (e.getCause() instanceof ConflictException) {
                    conflictCount.incrementAndGet();
                } else {
                    unexpectedFailures.add(e);
                }
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                bookingService.rescheduleAppointment(
                        appt2.id(), targetStart, targetEnd, branch.getId(), "Reschedule 2", Role.ADMIN, "admin"
                );
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                if (e.getCause() instanceof ConflictException) {
                    conflictCount.incrementAndGet();
                } else {
                    unexpectedFailures.add(e);
                }
            }
        });

        f1.get(15, TimeUnit.SECONDS);
        f2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(unexpectedFailures).as("No unexpected database/integrity exceptions allowed in reschedule concurrency").isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Scenario 3: Overlapping staggered appointments (10:00-10:30 vs 10:15-10:45) -> 409 Conflict")
    void overlappingStaggeredAppointmentsRejected() {
        Patient p1 = createPatient("Staggered 1", "+919700003001", "st1@example.com");
        Patient p2 = createPatient("Staggered 2", "+919700003002", "st2@example.com");

        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(6);
        Instant start1 = LocalDateTime.of(date, LocalTime.of(10, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end1 = start1.plus(30, ChronoUnit.MINUTES);

        // Book 10:00 to 10:30
        bookingService.bookAppointment(
                p1.getId(), doctor.getId(), branch.getId(), start1, end1, AppointmentType.IN_PERSON, "First", Role.ADMIN, "admin"
        );

        // Attempt staggered overlap: 10:15 to 10:45
        Instant start2 = start1.plus(15, ChronoUnit.MINUTES);
        Instant end2 = start2.plus(30, ChronoUnit.MINUTES);

        org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class, () ->
                bookingService.bookAppointment(
                        p2.getId(), doctor.getId(), branch.getId(), start2, end2, AppointmentType.WALK_IN, "Emergency overlap", Role.ADMIN, "admin"
                )
        );
    }

    @Test
    @DisplayName("Scenario 4: Cancel and immediate rebook -> success")
    void cancelAndImmediateRebookSucceeds() {
        Patient p1 = createPatient("Rebook 1", "+919700004001", "rb1@example.com");
        Patient p2 = createPatient("Rebook 2", "+919700004002", "rb2@example.com");

        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(7);
        Instant start = LocalDateTime.of(date, LocalTime.of(11, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        // Book
        AppointmentResponseDto appt = bookingService.bookAppointment(
                p1.getId(), doctor.getId(), branch.getId(), start, end, AppointmentType.IN_PERSON, "To cancel", Role.ADMIN, "admin"
        );

        // Cancel
        Appointment toCancel = appointmentRepository.findById(appt.id()).orElseThrow();
        toCancel.setStatus(AppointmentStatus.CANCELLED);
        toCancel.setCancelReason("Changed mind");
        appointmentRepository.saveAndFlush(toCancel);

        // Rebook immediately into the exact same slot by p2 -> SUCCESS!
        AppointmentResponseDto rebooked = bookingService.bookAppointment(
                p2.getId(), doctor.getId(), branch.getId(), start, end, AppointmentType.IN_PERSON, "Rebooked slot", Role.ADMIN, "admin"
        );

        assertThat(rebooked.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(rebooked.patient().id()).isEqualTo(p2.getId());
    }

    @Test
    @DisplayName("Scenario 5: Two different doctors booked at the same time slot in parallel -> both succeed (proves lock is per-doctor, not global)")
    void parallelBookingsDifferentDoctorsBothSucceed() throws Exception {
        User docUser2 = userRepository.save(new User("doc2.conc@example.com", "+919700000003", "pass", Role.DOCTOR, "Dr. Concurrency Two"));
        Doctor doc2Entity = new Doctor(docUser2, "Cardio 2", "MD", "English", 12, new BigDecimal("600.00"));
        doc2Entity.setBranches(Set.of(branch));
        final Doctor doctor2 = doctorRepository.save(doc2Entity);

        // Add 7-day schedule for doctor2 as well
        for (DayOfWeek dow : DayOfWeek.values()) {
            DoctorSchedule sched = new DoctorSchedule(
                    doctor2, branch, dow,
                    LocalTime.of(8, 0), LocalTime.of(18, 0),
                    30, LocalDate.now().minusDays(10)
            );
            scheduleRepository.save(sched);
        }

        Patient p1 = createPatient("Diff Doctor P1", "+919700005001", "ddp1@example.com");
        Patient p2 = createPatient("Diff Doctor P2", "+919700005002", "ddp2@example.com");

        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(8);
        Instant sameStart = LocalDateTime.of(date, LocalTime.of(15, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant sameEnd = sameStart.plus(30, ChronoUnit.MINUTES);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            try {
                barrier.await();
                bookingService.bookAppointment(
                        p1.getId(), doctor.getId(), branch.getId(), sameStart, sameEnd, AppointmentType.IN_PERSON, "Doc 1 Slot", Role.ADMIN, "admin"
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                // ignore
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try {
                barrier.await();
                bookingService.bookAppointment(
                        p2.getId(), doctor2.getId(), branch.getId(), sameStart, sameEnd, AppointmentType.IN_PERSON, "Doc 2 Slot", Role.ADMIN, "admin"
                );
                successCount.incrementAndGet();
            } catch (Exception e) {
                // ignore
            }
        });

        f1.get(15, TimeUnit.SECONDS);
        f2.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario 6: Direct repository duplicate active slot insert throws DataIntegrityViolationException (proves unique key backstop)")
    void duplicateActiveSlotDirectInsertThrowsDataIntegrityViolationException() {
        Patient p1 = createPatient("Direct Patient 1", "+919700006001", "dir1@example.com");
        Patient p2 = createPatient("Direct Patient 2", "+919700006002", "dir2@example.com");

        LocalDate date = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(12);
        Instant start = LocalDateTime.of(date, LocalTime.of(16, 0)).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        Appointment appt1 = new Appointment(p1, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appointmentRepository.saveAndFlush(appt1);

        Appointment duplicateSlotAppt = new Appointment(p2, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);

        org.junit.jupiter.api.Assertions.assertThrows(
                DataIntegrityViolationException.class,
                () -> appointmentRepository.saveAndFlush(duplicateSlotAppt)
        );
    }
}

