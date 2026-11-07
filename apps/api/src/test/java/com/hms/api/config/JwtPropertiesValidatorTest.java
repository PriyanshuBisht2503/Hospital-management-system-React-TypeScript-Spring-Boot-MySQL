package com.hms.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesValidatorTest {

    private static JwtPropertiesValidator validatorFor(String secret, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        if (activeProfiles.length > 0) {
            environment.setActiveProfiles(activeProfiles);
        }
        JwtPropertiesValidator validator = new JwtPropertiesValidator(environment);
        ReflectionTestUtils.setField(validator, "jwtSecret", secret);
        return validator;
    }

    @Test
    @DisplayName("Should fail startup if JWT_SECRET is less than 32 characters")
    void shouldFailIfJwtSecretTooShort() {
        assertThatThrownBy(() -> validatorFor("shortsecret123").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be at least 32 characters");
    }

    @Test
    @DisplayName("Should fail startup if JWT_SECRET is blank")
    void shouldFailIfJwtSecretBlank() {
        assertThatThrownBy(() -> validatorFor("   ").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be set");
    }

    @Test
    @DisplayName("Should pass validation if JWT_SECRET is 32 characters or longer")
    void shouldPassIfJwtSecretValid() {
        assertThatCode(() -> validatorFor("thisisavalidsecretkeythatislongerthan32characters!").validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject the known dev default secret outside dev/test profiles")
    void shouldRejectDevDefaultOutsideDevOrTest() {
        assertThatThrownBy(() -> validatorFor(JwtPropertiesValidator.DEV_DEFAULT_SECRET).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("known development default");
    }

    @Test
    @DisplayName("Should allow the dev default secret in the dev profile")
    void shouldAllowDevDefaultInDevProfile() {
        assertThatCode(() -> validatorFor(JwtPropertiesValidator.DEV_DEFAULT_SECRET, "dev").validate())
                .doesNotThrowAnyException();
    }
}
