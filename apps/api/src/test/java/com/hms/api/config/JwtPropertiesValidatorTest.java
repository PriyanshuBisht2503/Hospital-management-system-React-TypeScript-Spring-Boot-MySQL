package com.hms.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesValidatorTest {

    @Test
    @DisplayName("Should fail startup if JWT_SECRET is less than 32 characters")
    void shouldFailIfJwtSecretTooShort() {
        JwtPropertiesValidator validator = new JwtPropertiesValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", "shortsecret123");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be at least 32 characters");
    }

    @Test
    @DisplayName("Should pass validation if JWT_SECRET is 32 characters or longer")
    void shouldPassIfJwtSecretValid() {
        JwtPropertiesValidator validator = new JwtPropertiesValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", "thisisavalidsecretkeythatislongerthan32characters!");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
