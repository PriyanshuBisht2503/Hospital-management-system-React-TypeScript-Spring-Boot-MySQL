package com.hms.api.config;

import com.hms.api.ApiApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabasePasswordProfileTest {

    @Test
    @DisplayName("In non-dev profiles (e.g. prod), missing DB_PASSWORD fails application startup")
    void nonDevProfileFailsWhenDbPasswordIsMissing() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(ApiApplication.class)
                .profiles("prod")
                .properties(
                    "server.port=0",
                    "spring.flyway.enabled=false"
                );

        assertThatThrownBy(builder::run)
                .isNotNull();
    }

    @Test
    @DisplayName("In dev profile, DB_PASSWORD defaults to hms_password_123 if not set in environment")
    void devProfilePermitsDefaultPassword() {
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(ApiApplication.class)
                    .profiles("dev")
                    .properties(
                        "server.port=0",
                        "spring.flyway.enabled=false",
                        "spring.datasource.url=jdbc:h2:mem:dev_profile_test;MODE=MySQL;DB_CLOSE_DELAY=-1;USER=sa;PASSWORD=hms_password_123",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa"
                    )
                    .run();

            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo("hms_password_123");
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }
}
