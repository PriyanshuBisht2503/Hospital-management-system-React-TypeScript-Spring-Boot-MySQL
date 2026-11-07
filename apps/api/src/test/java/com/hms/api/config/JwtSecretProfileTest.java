package com.hms.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hermetic profile tests for the JWT secret: the production/default profile must refuse a
 * missing secret and the known dev default, while dev and mysql-test keep working.
 */
class JwtSecretProfileTest {

    @Configuration
    @PropertySource("classpath:application.properties")
    static class ProdConfig {
        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public JwtPropertiesValidator jwtPropertiesValidator(Environment environment) {
            return new JwtPropertiesValidator(environment);
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
        public JwtPropertiesValidator jwtPropertiesValidator(Environment environment) {
            return new JwtPropertiesValidator(environment);
        }
    }

    @Configuration
    @PropertySource("classpath:application.properties")
    @PropertySource("classpath:application-mysql-test.properties")
    static class MysqlTestConfig {
        @Bean
        public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        public JwtPropertiesValidator jwtPropertiesValidator(Environment environment) {
            return new JwtPropertiesValidator(environment);
        }
    }

    /**
     * Isolated environment: OS environment and JVM system properties are removed so
     * neither JWT_SECRET nor SPRING_PROFILES_ACTIVE can leak in from the host machine.
     */
    private ApplicationContextRunner isolatedRunner(Class<?> configClass, String activeProfile) {
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
    @DisplayName("prod profile without JWT_SECRET fails startup")
    void prodWithoutJwtSecretFails() {
        isolatedRunner(ProdConfig.class, "prod").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("Could not resolve placeholder 'JWT_SECRET'");
        });
    }

    @Test
    @DisplayName("prod profile with the known dev default secret fails startup")
    void prodWithDevDefaultSecretFails() {
        isolatedRunner(ProdConfig.class, "prod")
                .withPropertyValues("JWT_SECRET=" + JwtPropertiesValidator.DEV_DEFAULT_SECRET)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("known development default");
                });
    }

    @Test
    @DisplayName("prod profile with a strong secret starts")
    void prodWithStrongSecretStarts() {
        isolatedRunner(ProdConfig.class, "prod")
                .withPropertyValues("JWT_SECRET=thisisauniquestrongproductionsecretwithatleast32chars")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("dev profile still starts with the dev secret")
    void devProfileStarts() {
        isolatedRunner(DevConfig.class, "dev").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("mysql-test profile still starts with the test secret")
    void mysqlTestProfileStarts() {
        isolatedRunner(MysqlTestConfig.class, "mysql-test").run(context -> assertThat(context).hasNotFailed());
    }
}
