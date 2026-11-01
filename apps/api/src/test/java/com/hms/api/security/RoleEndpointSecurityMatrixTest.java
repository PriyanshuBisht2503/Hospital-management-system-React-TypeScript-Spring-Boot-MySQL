package com.hms.api.security;

import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Master Table-Driven Security Matrix Test.
 * Verifies every endpoint across all roles (anonymous, PATIENT, RECEPTIONIST, DOCTOR, ADMIN).
 * Future phases will append new endpoint rows to this matrix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class RoleEndpointSecurityMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final Map<Role, String> roleTokens = new EnumMap<>(Role.class);

    public record MatrixRow(
        HttpMethod method,
        String path,
        Role role,
        int expectedStatus,
        String requestBody,
        boolean isMultipart
    ) {
        @Override
        public String toString() {
            String roleName = role != null ? role.name() : "ANONYMOUS";
            return String.format("[%s %s] as %s -> %d", method, path, roleName, expectedStatus);
        }
    }

    @BeforeAll
    void initTokens() {
        for (Role role : Role.values()) {
            String email = "matrix." + role.name().toLowerCase() + "@example.com";
            User user = userRepository.findByEmail(email).orElseGet(() ->
                userRepository.save(new User(email, "+97150000" + role.ordinal(), "hash", role, "Matrix " + role.name()))
            );
            roleTokens.put(role, "Bearer " + jwtTokenProvider.generateAccessToken(user));
        }
    }

    static Stream<MatrixRow> endpointSecurityMatrix() {
        List<MatrixRow> rows = new ArrayList<>();

        // Helper to add all 5 roles for an endpoint
        // anonymous, PATIENT, RECEPTIONIST, DOCTOR, ADMIN
        addEndpoint(rows, HttpMethod.GET, "/api/health",
                200, 200, 200, 200, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/auth/login",
                400, 400, 400, 400, 400, "{}", false);

        addEndpoint(rows, HttpMethod.POST, "/api/auth/otp/send",
                400, 400, 400, 400, 400, "{}", false);

        addEndpoint(rows, HttpMethod.POST, "/api/auth/register-patient",
                400, 400, 400, 400, 400, "{}", false);

        addEndpoint(rows, HttpMethod.GET, "/api/auth/me",
                401, 200, 200, 200, 200, null, false);

        // Master Data: Branches
        addEndpoint(rows, HttpMethod.GET, "/api/branches",
                200, 200, 200, 200, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/branches",
                401, 403, 403, 403, 400, "{}", false);

        addEndpoint(rows, HttpMethod.PUT, "/api/branches/1",
                401, 403, 403, 403, 400, "{}", false);

        // Master Data: Departments
        addEndpoint(rows, HttpMethod.GET, "/api/departments",
                200, 200, 200, 200, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/departments",
                401, 403, 403, 403, 400, "{}", false);

        // Master Data: Specialties
        addEndpoint(rows, HttpMethod.GET, "/api/specialties",
                200, 200, 200, 200, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/specialties",
                401, 403, 403, 403, 400, "{}", false);

        // Master Data: Rooms (Authenticated only)
        addEndpoint(rows, HttpMethod.GET, "/api/rooms",
                401, 200, 200, 200, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/rooms",
                401, 403, 403, 403, 400, "{}", false);

        // Doctors (Public vs Admin)
        addEndpoint(rows, HttpMethod.GET, "/api/doctors",
                200, 200, 200, 200, 200, null, false);

        addEndpoint(rows, HttpMethod.GET, "/api/admin/doctors",
                401, 403, 403, 403, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/admin/doctors",
                401, 403, 403, 403, 400, "{}", false);

        addEndpoint(rows, HttpMethod.POST, "/api/admin/doctors/photo",
                401, 403, 403, 403, 400, null, true);

        // User Management (ADMIN only)
        addEndpoint(rows, HttpMethod.GET, "/api/admin/users",
                401, 403, 403, 403, 200, null, false);

        addEndpoint(rows, HttpMethod.POST, "/api/admin/users",
                401, 403, 403, 403, 400, "{}", false);

        addEndpoint(rows, HttpMethod.PATCH, "/api/admin/users/1/role",
                401, 403, 403, 403, 400, "{}", false);

        addEndpoint(rows, HttpMethod.PATCH, "/api/admin/users/1/status",
                401, 403, 403, 403, 400, "{}", false);

        // Audit Logs (ADMIN only)
        addEndpoint(rows, HttpMethod.GET, "/api/admin/audit-logs",
                401, 403, 403, 403, 200, null, false);

        // File upload (authenticated only)
        addEndpoint(rows, HttpMethod.POST, "/api/files/upload",
                401, 400, 400, 400, 400, null, true);

        return rows.stream();
    }

    private static void addEndpoint(
            List<MatrixRow> rows,
            HttpMethod method,
            String path,
            int anonExpected,
            int patientExpected,
            int receptionistExpected,
            int doctorExpected,
            int adminExpected,
            String body,
            boolean isMultipart
    ) {
        rows.add(new MatrixRow(method, path, null, anonExpected, body, isMultipart));
        rows.add(new MatrixRow(method, path, Role.PATIENT, patientExpected, body, isMultipart));
        rows.add(new MatrixRow(method, path, Role.RECEPTIONIST, receptionistExpected, body, isMultipart));
        rows.add(new MatrixRow(method, path, Role.DOCTOR, doctorExpected, body, isMultipart));
        rows.add(new MatrixRow(method, path, Role.ADMIN, adminExpected, body, isMultipart));
    }

    @ParameterizedTest(name = "{index} => {0}")
    @MethodSource("endpointSecurityMatrix")
    @DisplayName("Endpoint Role Security Matrix Validation")
    void testSecurityMatrix(MatrixRow row) throws Exception {
        MockHttpServletRequestBuilder builder;

        if (row.isMultipart()) {
            builder = multipart(row.path())
                    .file(new MockMultipartFile("file", "test.bin", "text/plain", new byte[0]));
        } else {
            builder = request(row.method(), row.path());
            if (row.requestBody() != null) {
                builder.contentType(MediaType.APPLICATION_JSON).content(row.requestBody());
            }
        }

        if (row.role() != null) {
            String token = roleTokens.get(row.role());
            builder.header("Authorization", token);
        }

        mockMvc.perform(builder)
                .andExpect(status().is(row.expectedStatus()));
    }
}
