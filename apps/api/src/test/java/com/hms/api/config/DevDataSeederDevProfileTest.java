package com.hms.api.config;

import com.hms.api.domain.Doctor;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.repository.DoctorRepository;
import com.hms.api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "dev"}) // Dev profile active
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:hms_dev_test_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@DirtiesContext
class DevDataSeederDevProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DevDataSeeder devDataSeeder;

    @Test
    @DisplayName("DevDataSeeder bean is registered under 'dev' profile")
    void seederBeanPresentUnderDevProfile() {
        assertThat(context.getBeansOfType(DevDataSeeder.class)).isNotEmpty();
    }

    @Test
    @DisplayName("All 4 demo accounts are seeded with proper roles and BCrypt hashes under 'dev' profile")
    void allDemoAccountsSeeded() {
        // 1. ADMIN
        User admin = userRepository.findByEmail(DevDataSeeder.DEMO_ADMIN_EMAIL).orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("admin@123", admin.getPasswordHash())).isTrue();

        // 2. DOCTOR
        User doctorUser = userRepository.findByEmail(DevDataSeeder.DEMO_DOCTOR_EMAIL).orElseThrow();
        assertThat(doctorUser.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(doctorUser.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("doctor@123", doctorUser.getPasswordHash())).isTrue();

        // DOCTOR Profile
        Optional<Doctor> doctorProfileOpt = doctorRepository.findByUserId(doctorUser.getId());
        assertThat(doctorProfileOpt).isPresent();
        Doctor doctorProfile = doctorProfileOpt.get();
        assertThat(doctorProfile.isActive()).isTrue();
        assertThat(doctorProfile.getBio()).isNotBlank();
        assertThat(doctorProfile.getQualifications()).isNotBlank();
        assertThat(doctorProfile.getConsultationFee()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(doctorProfile.getSpecialties()).isNotEmpty();
        assertThat(doctorProfile.getBranches()).isNotEmpty();

        // 3. RECEPTIONIST
        User receptionist = userRepository.findByEmail(DevDataSeeder.DEMO_RECEPTIONIST_EMAIL).orElseThrow();
        assertThat(receptionist.getRole()).isEqualTo(Role.RECEPTIONIST);
        assertThat(receptionist.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("receptionist@123", receptionist.getPasswordHash())).isTrue();

        // 4. PATIENT (no password: OTP only)
        User patient = userRepository.findByEmail(DevDataSeeder.DEMO_PATIENT_EMAIL).orElseThrow();
        assertThat(patient.getRole()).isEqualTo(Role.PATIENT);
        assertThat(patient.isEnabled()).isTrue();
        assertThat(patient.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("DevDataSeeder is idempotent when executed multiple times")
    void seederIsIdempotent() {
        long initialUserCount = userRepository.count();
        long initialDoctorCount = doctorRepository.count();

        // Run seeder again
        devDataSeeder.run();

        assertThat(userRepository.count()).isEqualTo(initialUserCount);
        assertThat(doctorRepository.count()).isEqualTo(initialDoctorCount);
    }
}
