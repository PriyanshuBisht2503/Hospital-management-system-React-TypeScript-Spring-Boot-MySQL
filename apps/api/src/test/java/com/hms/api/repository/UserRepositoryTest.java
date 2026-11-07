package com.hms.api.repository;

import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should successfully persist user with email and password")
    void shouldPersistUserWithEmail() {
        User user = new User("doctor1@example.com", "+971501112233", "hashedPass", Role.DOCTOR, "Dr. John Doe");
        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("doctor1@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should allow patient with phone only and null email and null password (OTP login)")
    void shouldAllowPatientWithPhoneOnly() {
        User patient = new User(null, "+971509998877", null, Role.PATIENT, "Patient Without Email");
        User saved = userRepository.save(patient);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getPhone()).isEqualTo("+971509998877");
        assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
    }

    @Test
    @DisplayName("Should enforce unique email constraint")
    void shouldEnforceUniqueEmail() {
        User u1 = new User("unique@example.com", "+971501000001", "pass1", Role.RECEPTIONIST, "Staff One");
        userRepository.saveAndFlush(u1);

        User u2 = new User("unique@example.com", "+971501000002", "pass2", Role.RECEPTIONIST, "Staff Two");
        assertThatThrownBy(() -> userRepository.saveAndFlush(u2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should count users by role and search correctly")
    void shouldCountAndSearchUsers() {
        userRepository.save(new User("admin.test@example.com", "+971502000001", "pass", Role.ADMIN, "Admin Test"));
        userRepository.save(new User("nurse.test@example.com", "+971502000002", "pass", Role.RECEPTIONIST, "Nurse Test"));

        long adminCount = userRepository.countByRole(Role.ADMIN);
        assertThat(adminCount).isGreaterThanOrEqualTo(1);

        Page<User> searchResults = userRepository.searchUsers(Role.ADMIN, "admin", PageRequest.of(0, 10));
        assertThat(searchResults.getContent()).isNotEmpty();
        assertThat(searchResults.getContent().get(0).getRole()).isEqualTo(Role.ADMIN);
    }
}
