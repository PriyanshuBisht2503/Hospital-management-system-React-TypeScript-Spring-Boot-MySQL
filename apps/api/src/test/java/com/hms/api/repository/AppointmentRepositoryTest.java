package com.hms.api.repository;

import com.hms.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class AppointmentRepositoryTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Doctor doctor;
    private Patient patient1;
    private Patient patient2;
    private Branch branch;

    @BeforeEach
    void setUp() {
        User docUser = new User("dr.appointment@example.com", "+919555500003", "password", Role.DOCTOR, "Dr. Appt Test");
        userRepository.save(docUser);
        doctor = doctorRepository.save(new Doctor(docUser, "Cardiologist", "MD, MBBS", "English", 8, new BigDecimal("500.00")));

        branch = branchRepository.save(new Branch("City Clinic", "CITY01", "456 Market St", "Delhi", "+911122223333", "city@example.com"));

        patient1 = patientRepository.save(new Patient("Patient Alpha", "+919888800001", "alpha@example.com", LocalDate.of(1991, 1, 1), Gender.MALE));
        patient2 = patientRepository.save(new Patient("Patient Beta", "+919888800002", "beta@example.com", LocalDate.of(1992, 2, 2), Gender.FEMALE));
    }

    @Test
    @DisplayName("Should persist appointment with optimistic locking version and default flags")
    void shouldPersistAppointment() {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        Appointment appt = new Appointment(patient1, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.BOOKED);
        Appointment saved = appointmentRepository.save(appt);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);
        assertThat(saved.isNeedsRescheduling()).isFalse();
        assertThat(saved.isReminder24hSent()).isFalse();
        assertThat(saved.isReminder2hSent()).isFalse();
    }

    @Test
    @DisplayName("Should detect doctor overlapping appointment times")
    void shouldDetectDoctorOverlap() {
        Instant base = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Appointment existing = new Appointment(patient1, doctor, branch, base, base.plus(30, ChronoUnit.MINUTES),
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appointmentRepository.saveAndFlush(existing);

        // Overlapping range (15 minutes in)
        List<Appointment> overlaps = appointmentRepository.findDoctorOverlappingAppointments(
                doctor.getId(), base.plus(15, ChronoUnit.MINUTES), base.plus(45, ChronoUnit.MINUTES), null);
        assertThat(overlaps).hasSize(1);

        // Non-overlapping range
        List<Appointment> nonOverlaps = appointmentRepository.findDoctorOverlappingAppointments(
                doctor.getId(), base.plus(30, ChronoUnit.MINUTES), base.plus(60, ChronoUnit.MINUTES), null);
        assertThat(nonOverlaps).isEmpty();
    }

    @Test
    @DisplayName("Should detect patient overlapping active appointment times")
    void shouldDetectPatientOverlap() {
        Instant base = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Appointment appt1 = new Appointment(patient1, doctor, branch, base, base.plus(30, ChronoUnit.MINUTES),
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appointmentRepository.saveAndFlush(appt1);

        // Same patient overlapping
        List<Appointment> overlaps = appointmentRepository.findPatientOverlappingAppointments(
                patient1.getId(), base.plus(10, ChronoUnit.MINUTES), base.plus(40, ChronoUnit.MINUTES), null);
        assertThat(overlaps).hasSize(1);

        // Different patient at same time does not trigger patient overlap query
        List<Appointment> diffPatientOverlaps = appointmentRepository.findPatientOverlappingAppointments(
                patient2.getId(), base, base.plus(30, ChronoUnit.MINUTES), null);
        assertThat(diffPatientOverlaps).isEmpty();
    }

    @Test
    @DisplayName("Database unique slot constraint prevents two active appointments for same doctor and start time")
    void databaseUniqueConstraintPreventsDoubleBooking() {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        Appointment first = new Appointment(patient1, doctor, branch, start, end,
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appointmentRepository.saveAndFlush(first);

        Appointment second = new Appointment(patient2, doctor, branch, start, end,
                AppointmentType.IN_PERSON, AppointmentStatus.BOOKED);

        assertThatThrownBy(() -> {
            appointmentRepository.saveAndFlush(second);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Cancelling an appointment frees the slot for immediate rebooking")
    void cancellingFreesSlotForRebooking() {
        Instant start = Instant.now().plus(6, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);

        Appointment first = new Appointment(patient1, doctor, branch, start, end,
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        first = appointmentRepository.saveAndFlush(first);

        // Cancel first
        first.setStatus(AppointmentStatus.CANCELLED);
        first.setCancelledAt(Instant.now());
        appointmentRepository.saveAndFlush(first);

        // Rebook same slot by patient2 succeeds because CANCELLED evaluates active_booking_slot to NULL
        Appointment rebooked = new Appointment(patient2, doctor, branch, start, end,
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        Appointment savedRebooked = appointmentRepository.saveAndFlush(rebooked);

        assertThat(savedRebooked.getId()).isNotNull();
    }

    @Test
    @DisplayName("Atomic reminder claim returns 1 on first call and 0 on subsequent calls")
    void atomicReminderClaimPreventsDoubleSending() {
        Instant start = Instant.now().plus(25, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        Appointment appt = new Appointment(patient1, doctor, branch, start, start.plus(30, ChronoUnit.MINUTES),
                AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        Appointment saved = appointmentRepository.saveAndFlush(appt);

        int firstClaim = appointmentRepository.claimReminder24h(saved.getId());
        assertThat(firstClaim).isEqualTo(1);

        int secondClaim = appointmentRepository.claimReminder24h(saved.getId());
        assertThat(secondClaim).isEqualTo(0);
    }
}
