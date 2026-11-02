package com.hms.api.service;

import com.hms.api.domain.*;
import com.hms.api.event.AppointmentBookedEvent;
import com.hms.api.event.AppointmentCancelledEvent;
import com.hms.api.event.AppointmentRescheduledEvent;
import com.hms.api.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentNotificationAndReminderTest {

    @Autowired
    private AppointmentNotificationListener notificationListener;

    @Autowired
    private AppointmentReminderService reminderService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private EmailService emailService;

    @Autowired
    private Clock clock;

    private Doctor doctor;
    private Branch branch;
    private Patient patient;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();

        User docUser = userRepository.save(new User("doc.notify@example.com", "+919600000001", "pass", Role.DOCTOR, "Dr. Notifier"));
        doctor = doctorRepository.save(new Doctor(docUser, "Bio", "MD", "English", 10, new BigDecimal("500.00")));
        branch = branchRepository.save(new Branch("West Hospital", "WEST01", "West Rd", "City", "+91900222333", "west@example.com"));

        patient = new Patient("Notify Patient", "+919600000002", "notify.patient@example.com", LocalDate.of(1990, 1, 1), Gender.MALE);
        patient = patientRepository.save(patient);

        doNothing().when(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        appointmentRepository.deleteAll();
        doctorRepository.deleteAll();
        patientRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Appointment booked event creates SENT notification and sends email")
    void appointmentBookedNotification() throws Exception {
        Instant start = clock.instant().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Appointment appt = new Appointment(patient, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appt = appointmentRepository.save(appt);

        notificationListener.onAppointmentBooked(new AppointmentBookedEvent(appt.getId()));

        for (int i = 0; i < 20 && notificationRepository.count() == 0; i++) {
            Thread.sleep(50);
        }

        List<Notification> notifications = notificationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).hasSize(1);
        Notification n = notifications.get(0);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getPatient().getEmail()).isEqualTo("notify.patient@example.com");
        assertThat(n.getSubject()).contains("Appointment Confirmation");

        verify(emailService, times(1)).sendEmail(eq("notify.patient@example.com"), contains("Appointment Confirmation"), anyString());
    }

    @Test
    @DisplayName("Appointment cancelled event creates cancellation notification")
    void appointmentCancelledNotification() throws Exception {
        Instant start = clock.instant().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Appointment appt = new Appointment(patient, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CANCELLED);
        appt.setCancelReason("Doctor emergency leave");
        appt = appointmentRepository.save(appt);

        notificationListener.onAppointmentCancelled(new AppointmentCancelledEvent(appt.getId()));

        for (int i = 0; i < 20 && notificationRepository.count() == 0; i++) {
            Thread.sleep(50);
        }

        List<Notification> notifications = notificationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).hasSize(1);
        Notification n = notifications.get(0);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getBody()).contains("Doctor emergency leave");

        verify(emailService, times(1)).sendEmail(eq("notify.patient@example.com"), contains("Appointment Cancelled"), anyString());
    }

    @Test
    @DisplayName("24-hour reminder process sends reminder and marks reminder24hSent=true atomically")
    void send24hReminderFlow() {
        // Scheduled 10 hours from now (within the 2h to 24h window)
        // Booked 3 days ago (> 24h in advance)
        Instant start = clock.instant().plus(10, ChronoUnit.HOURS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Appointment appt = new Appointment(patient, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appt.setCreatedAt(clock.instant().minus(3, ChronoUnit.DAYS));
        appt = appointmentRepository.save(appt);

        reminderService.process24hReminders();

        Appointment updated = appointmentRepository.findById(appt.getId()).orElseThrow();
        assertThat(updated.isReminder24hSent()).isTrue();

        List<Notification> notifications = notificationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getSubject()).contains("Reminder: Your appointment tomorrow");
    }

    @Test
    @DisplayName("24-hour reminder is SKIPPED if appointment was booked less than 24 hours in advance")
    void skip24hReminderIfBookedShortNotice() {
        // Scheduled 10 hours from now, but created just 1 hour ago (< 24h ahead)
        Instant start = clock.instant().plus(10, ChronoUnit.HOURS);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Appointment appt = new Appointment(patient, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appt.setCreatedAt(clock.instant().minus(1, ChronoUnit.HOURS));
        appt = appointmentRepository.save(appt);

        reminderService.process24hReminders();

        Appointment updated = appointmentRepository.findById(appt.getId()).orElseThrow();
        // reminder24hSent should be claimed/set to true to avoid reprocessing, but NO notification sent!
        assertThat(updated.isReminder24hSent()).isTrue();

        List<Notification> notifications = notificationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("2-hour reminder process sends reminder for appointments starting within 2 hours")
    void send2hReminderFlow() {
        // Scheduled 90 minutes from now (between 0 and 2h)
        Instant start = clock.instant().plus(90, ChronoUnit.MINUTES);
        Instant end = start.plus(30, ChronoUnit.MINUTES);
        Appointment appt = new Appointment(patient, doctor, branch, start, end, AppointmentType.IN_PERSON, AppointmentStatus.CONFIRMED);
        appt = appointmentRepository.save(appt);

        reminderService.process2hReminders();

        Appointment updated = appointmentRepository.findById(appt.getId()).orElseThrow();
        assertThat(updated.isReminder2hSent()).isTrue();

        List<Notification> notifications = notificationRepository.findByPatientIdOrderByCreatedAtDesc(patient.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getSubject()).contains("Reminder: Your appointment in 2 hours");
    }

    @Test
    @DisplayName("Failing mail server does not block or fail appointment notification handling")
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
}
