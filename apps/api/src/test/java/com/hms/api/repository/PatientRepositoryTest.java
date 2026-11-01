package com.hms.api.repository;

import com.hms.api.domain.Gender;
import com.hms.api.domain.Patient;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class PatientRepositoryTest {

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should persist patient with valid phone and email")
    void shouldPersistPatientWithPhoneAndEmail() {
        Patient patient = new Patient("Aarav Sharma", "+919876543210", "aarav@example.com",
                LocalDate.of(1990, 5, 15), Gender.MALE);
        Patient saved = patientRepository.save(patient);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFullName()).isEqualTo("Aarav Sharma");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should persist patient with phone only and null email")
    void shouldPersistPatientWithPhoneOnly() {
        Patient patient = new Patient("Priya Patel", "+919876543211", null,
                LocalDate.of(1995, 8, 20), Gender.FEMALE);
        Patient saved = patientRepository.save(patient);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getPhone()).isEqualTo("+919876543211");
    }

    @Test
    @DisplayName("Should reject patient with both phone and email null")
    void shouldRejectPatientWithoutPhoneOrEmail() {
        Patient patient = new Patient("Invalid Patient", null, null,
                LocalDate.of(2000, 1, 1), Gender.OTHER);

        assertThatThrownBy(() -> {
            patientRepository.saveAndFlush(patient);
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should search patients by name, phone or email")
    void shouldSearchPatients() {
        patientRepository.save(new Patient("Sunita Rao", "+919111122222", "sunita@example.com", LocalDate.of(1985, 3, 10), Gender.FEMALE));
        patientRepository.save(new Patient("Amit Kumar", "+919333344444", "amit@example.com", LocalDate.of(1982, 11, 25), Gender.MALE));

        Page<Patient> byName = patientRepository.searchPatients("Sunita", PageRequest.of(0, 10));
        assertThat(byName.getContent()).hasSize(1);
        assertThat(byName.getContent().get(0).getFullName()).isEqualTo("Sunita Rao");

        Page<Patient> byPhone = patientRepository.searchPatients("9333344444", PageRequest.of(0, 10));
        assertThat(byPhone.getContent()).hasSize(1);
        assertThat(byPhone.getContent().get(0).getFullName()).isEqualTo("Amit Kumar");
    }

    @Test
    @DisplayName("Should link patient to user")
    void shouldLinkPatientToUser() {
        User user = new User("patient.user@example.com", "+919888877777", null, Role.PATIENT, "Patient User");
        User savedUser = userRepository.save(user);

        Patient patient = new Patient("Patient User", "+919888877777", "patient.user@example.com", LocalDate.of(1992, 7, 4), Gender.MALE);
        patient.setUser(savedUser);
        Patient savedPatient = patientRepository.save(patient);

        assertThat(savedPatient.getUser()).isNotNull();
        assertThat(savedPatient.getUser().getId()).isEqualTo(savedUser.getId());
        assertThat(patientRepository.findByUserId(savedUser.getId())).isPresent();
    }
}
