package com.hms.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DatabasePasswordProfileTest {

    @Configuration
    @PropertySource("classpath:application.properties")
    static class ProdConfig {
        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public String dbPassword(@Value("${spring.datasource.password}") String password) {
            return password;
        }
    }

    @Configuration
    @PropertySource("classpath:application.properties")
    @PropertySource("classpath:application-dev.properties")
    static class DevConfig {
        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public String dbPassword(@Value("${spring.datasource.password}") String password) {
            return password;
        }
    }

    /**
     * Creates an ApplicationContextRunner with an isolated environment where OS environment
     * variables (System.getenv) and JVM system properties are completely removed.
     * This guarantees that neither DB_PASSWORD nor SPRING_PROFILES_ACTIVE can leak in from the host machine.
     */
    private ApplicationContextRunner createIsolatedRunner(Class<?> configClass, String activeProfile) {
        return new ApplicationContextRunner(() -> {
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            StandardEnvironment env = new StandardEnvironment();
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            env.setActiveProfiles(activeProfile);
            context.setEnvironment(env);
            return context;
        }).withUserConfiguration(configClass);
    }

    @Test
    @DisplayName("In non-dev profiles (e.g. prod), missing DB_PASSWORD fails application startup")
    void nonDevProfileFailsWhenDbPasswordIsMissing() {
        createIsolatedRunner(ProdConfig.class, "prod")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("Could not resolve placeholder 'DB_PASSWORD'");
            });
    }

    @Test
    @DisplayName("In dev profile, DB_PASSWORD defaults to hms_password_123 if not set in environment")
    void devProfilePermitsDefaultPassword() {
        createIsolatedRunner(DevConfig.class, "dev")
            .run(context -> {
                assertThat(context).hasNotFailed();
                String password = context.getBean("dbPassword", String.class);
                assertThat(password).isEqualTo("hms_password_123");
            });
    }
}
