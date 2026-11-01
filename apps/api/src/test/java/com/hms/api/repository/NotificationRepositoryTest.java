package com.hms.api.repository;

import com.hms.api.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    private User user;
    private Patient patient;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("notif.user@example.com", "+919777700001", "password", Role.PATIENT, "Notif User"));
        patient = patientRepository.save(new Patient("Notif Patient", "+919777700002", "notif.patient@example.com", LocalDate.of(1995, 6, 12), Gender.FEMALE));
    }

    @Test
    @DisplayName("Should persist notification for user recipient")
    void shouldPersistNotificationForUser() {
        Notification notification = new Notification(user, null, NotificationChannel.EMAIL,
                "Appointment Confirmation", "Your appointment is confirmed.");
        Notification saved = notificationRepository.save(notification);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should persist notification for patient recipient")
    void shouldPersistNotificationForPatient() {
        Notification notification = new Notification(null, patient, NotificationChannel.SMS,
                "Appointment Reminder", "Your consultation is in 2 hours.");
        Notification saved = notificationRepository.save(notification);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPatient().getId()).isEqualTo(patient.getId());
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    @DisplayName("Should reject notification when both user and patient are null")
    void shouldRejectNotificationWithoutRecipient() {
        Notification notification = new Notification(null, null, NotificationChannel.EMAIL,
                "Orphan Notification", "This notification has no recipient.");

        assertThatThrownBy(() -> {
            notificationRepository.saveAndFlush(notification);
        }).isInstanceOf(Exception.class);
    }
}

