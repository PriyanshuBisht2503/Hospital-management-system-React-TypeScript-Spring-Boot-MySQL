package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.FileRecord;
import com.hms.api.domain.Role;
import com.hms.api.domain.User;
import com.hms.api.repository.FileRecordRepository;
import com.hms.api.repository.UserRepository;
import com.hms.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FileStorageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileRecordRepository fileRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User admin;
    private User doctorUser;
    private User receptionistUser;
    private User patientUser;

    private String adminToken;
    private String doctorToken;
    private String receptionistToken;
    private String patientToken;

    // Standard Magic Bytes for Valid Formats
    private static final byte[] VALID_JPEG_BYTES = new byte[] {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    private static final byte[] VALID_PNG_BYTES = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private static final byte[] VALID_PDF_BYTES = new byte[] {
        0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x25, (byte) 0xD0, (byte) 0xD4
    };

    private static final byte[] VALID_WEBP_BYTES = new byte[] {
        0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };

    @BeforeEach
    void setUp() {
        fileRecordRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(new User("admin@aster.ae", "+971501111111", "hash", Role.ADMIN, "Admin User"));
        doctorUser = userRepository.save(new User("doc@aster.ae", "+971502222222", "hash", Role.DOCTOR, "Dr. Alice"));
        receptionistUser = userRepository.save(new User("rec@aster.ae", "+971503333334", "hash", Role.RECEPTIONIST, "Receptionist User"));
        patientUser = userRepository.save(new User("pat@aster.ae", "+971503333333", "hash", Role.PATIENT, "Patient User"));

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin);
        doctorToken = "Bearer " + jwtTokenProvider.generateAccessToken(doctorUser);
        receptionistToken = "Bearer " + jwtTokenProvider.generateAccessToken(receptionistUser);
        patientToken = "Bearer " + jwtTokenProvider.generateAccessToken(patientUser);
    }

    @Test
    @DisplayName("Upload security: unauthenticated upload returns 401")
    void unauthenticatedUploadReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", VALID_JPEG_BYTES
        );

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Fake file with .jpg name but invalid content (magic bytes) is rejected with 400")
    void fakeJpgFileWithInvalidContentIsRejected() throws Exception {
        // Plain text content disguised as a .jpg
        byte[] fakeContent = "This is a plain text file pretending to be a JPG".getBytes();

        MockMultipartFile fakeFile = new MockMultipartFile(
            "file", "avatar.jpg", "image/jpeg", fakeContent
        );

        mockMvc.perform(multipart("/api/files/upload")
                .file(fakeFile)
                .header("Authorization", doctorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid file type: only JPEG, PNG, WebP, and PDF files are allowed"));
    }

    @Test
    @DisplayName("Standard file upload is strictly private: client cannot set isPublic=true via request flag")
    void standardUploadIsAlwaysPrivateAndIgnoresClientFlag() throws Exception {
        MockMultipartFile jpegFile = new MockMultipartFile(
            "file", "../../sensitive_profile.jpg", "image/jpeg", VALID_JPEG_BYTES
        );

        // Attempting to send isPublic=true must be ignored; always private
        MvcResult result = mockMvc.perform(multipart("/api/files/upload")
                .file(jpegFile)
                .param("isPublic", "true")
                .header("Authorization", doctorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalName").value("sensitive_profile.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.isPublic").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String storedName = objectMapper.readTree(body).get("storedName").asText();

        // Stored name must be a randomized UUID ending with .jpg, NOT containing client path
        assertThat(storedName).doesNotContain("sensitive");
        assertThat(storedName).doesNotContain("..");
        assertThat(storedName).endsWith(".jpg");

        FileRecord record = fileRecordRepository.findByStoredName(storedName).orElseThrow();
        assertThat(record.isPublic()).isFalse();
    }

    @Test
    @DisplayName("Only ADMIN doctor-photo path can publish a file; DOCTOR, RECEPTIONIST, PATIENT are rejected with 403")
    void onlyAdminCanPublishDoctorPhoto() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
            "file", "dr_photo.jpg", "image/jpeg", VALID_JPEG_BYTES
        );

        // 1. Unauthenticated -> 401
        mockMvc.perform(multipart("/api/admin/doctors/photo")
                .file(photo))
                .andExpect(status().isUnauthorized());

        // 2. DOCTOR, RECEPTIONIST, PATIENT -> 403 Forbidden
        String[] forbiddenTokens = { doctorToken, receptionistToken, patientToken };
        for (String token : forbiddenTokens) {
            mockMvc.perform(multipart("/api/admin/doctors/photo")
                    .file(photo)
                    .header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        // 3. ADMIN -> 201 Created and isPublic = true
        MvcResult adminResult = mockMvc.perform(multipart("/api/admin/doctors/photo")
                .file(photo)
                .header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPublic").value(true))
                .andReturn();

        String storedName = objectMapper.readTree(adminResult.getResponse().getContentAsString()).get("storedName").asText();
        FileRecord record = fileRecordRepository.findByStoredName(storedName).orElseThrow();
        assertThat(record.isPublic()).isTrue();

        // 4. Public doctor photo can be downloaded unauthenticated
        mockMvc.perform(get("/api/files/" + storedName))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(VALID_JPEG_BYTES));
    }

    @Test
    @DisplayName("Private file: 401 unauthenticated, 403 wrong user, 200 owner, 200 ADMIN")
    void privateFileAccessControl() throws Exception {
        MockMultipartFile report = new MockMultipartFile(
            "file", "medical_report.pdf", "application/pdf", VALID_PDF_BYTES
        );

        // Doctor uploads private medical report (isPublic=false)
        MvcResult uploadResult = mockMvc.perform(multipart("/api/files/upload")
                .file(report)
                .param("isPublic", "false")
                .header("Authorization", doctorToken))
                .andExpect(status().isCreated())
                .andReturn();

        String storedName = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("storedName").asText();

        // 1. Unauthenticated download fails with 401
        mockMvc.perform(get("/api/files/" + storedName))
                .andExpect(status().isUnauthorized());

        // 2. Patient (different user, not owner, not admin) download fails with 403
        mockMvc.perform(get("/api/files/" + storedName)
                .header("Authorization", patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: insufficient permissions to access this resource"));

        // 3. Owner (Doctor) download succeeds with 200
        mockMvc.perform(get("/api/files/" + storedName)
                .header("Authorization", doctorToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));

        // 4. ADMIN download succeeds with 200
        mockMvc.perform(get("/api/files/" + storedName)
                .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    @DisplayName("Path traversal attack on download filename is rejected")
    void pathTraversalAttemptRejected() throws Exception {
        mockMvc.perform(get("/api/files/..%2F..%2Fetc%2Fpasswd")
                .header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }
}
