package com.hms.api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
@Import(GlobalErrorHandlingTest.TestErrorEndpointsConfig.class)
class GlobalErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    record SampleValidationRequest(@NotBlank(message = "Title cannot be blank") String title) {}

    @TestConfiguration
    static class TestErrorEndpointsConfig {
        @RestController
        static class TestErrorController {
            @PostMapping("/api/test-validation")
            public String triggerValidation(@Valid @RequestBody SampleValidationRequest req) {
                return "ok";
            }

            @GetMapping("/api/test-404")
            public String trigger404() {
                throw new ResourceNotFoundException("Branch not found with id: 9999");
            }

            @GetMapping("/api/test-429")
            public String trigger429() {
                throw new RateLimitExceededException("Too many requests from this IP");
            }

            @GetMapping("/api/test-500")
            public String trigger500() {
                throw new RuntimeException("Simulated unexpected crash inside service");
            }
        }
    }

    @Test
    @DisplayName("Validation error returns HTTP 400 with fieldErrors map")
    void validationErrorFormatContainsFieldErrors() throws Exception {
        SampleValidationRequest invalidReq = new SampleValidationRequest("");

        mockMvc.perform(post("/api/test-validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.fieldErrors.title").value("Title cannot be blank"))
                .andExpect(jsonPath("$.path").value("/api/test-validation"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("404 Not Found returns HTTP 404 in standard ErrorResponse format")
    void notFoundFormatReturnsStandardErrorResponse() throws Exception {
        mockMvc.perform(get("/api/test-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Branch not found with id: 9999"))
                .andExpect(jsonPath("$.path").value("/api/test-404"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("429 Too Many Requests returns HTTP 429 with Retry-After header")
    void rateLimitFormatReturns429WithRetryAfterHeader() throws Exception {
        mockMvc.perform(get("/api/test-429"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many requests from this IP"))
                .andExpect(jsonPath("$.path").value("/api/test-429"));
    }

    @Test
    @DisplayName("Error responses never expose stack traces or Java exception class names in body")
    void errorBodiesNeverContainStackTraceOrExceptionClasses() throws Exception {
        // Test across 400, 404, 429, and 500 responses
        String[] endpoints = { "/api/test-validation", "/api/test-404", "/api/test-429", "/api/test-500" };

        for (String endpoint : endpoints) {
            MvcResult result;
            if (endpoint.contains("validation")) {
                result = mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                        .andReturn();
            } else {
                result = mockMvc.perform(get(endpoint)).andReturn();
            }

            String body = result.getResponse().getContentAsString();

            assertThat(body).doesNotContain("java.lang.");
            assertThat(body).doesNotContain("Exception");
            assertThat(body).doesNotContain("at org.springframework");
            assertThat(body).doesNotContain("at com.hms.api");
            assertThat(body).doesNotContain("stackTrace");
            assertThat(body).doesNotContain("trace");
        }
    }
}
