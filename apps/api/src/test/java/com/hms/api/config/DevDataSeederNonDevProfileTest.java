package com.hms.api.config;

import com.hms.api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test") // Non-dev profile: proves seeder does NOT run
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:hms_nondev_test_db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@DirtiesContext
class DevDataSeederNonDevProfileTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("DevDataSeeder bean is NOT registered when active profile is not 'dev'")
    void seederBeanNotPresentUnderNonDevProfile() {
        assertThat(context.getBeansOfType(DevDataSeeder.class)).isEmpty();
    }

    @Test
    @DisplayName("No demo accounts exist in database under non-dev profile")
    void demoAccountsNotSeededUnderNonDevProfile() {
        assertThat(userRepository.findByEmail(DevDataSeeder.DEMO_ADMIN_EMAIL)).isEmpty();
        assertThat(userRepository.findByEmail(DevDataSeeder.DEMO_DOCTOR_EMAIL)).isEmpty();
        assertThat(userRepository.findByEmail(DevDataSeeder.DEMO_RECEPTIONIST_EMAIL)).isEmpty();
        assertThat(userRepository.findByEmail(DevDataSeeder.DEMO_PATIENT_EMAIL)).isEmpty();
    }
}
