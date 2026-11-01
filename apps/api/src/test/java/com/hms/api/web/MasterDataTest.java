package com.hms.api.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.*;
import com.hms.api.dto.*;
import com.hms.api.repository.*;
import com.hms.api.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MasterDataTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String doctorToken;
    private String receptionistToken;
    private String patientToken;

    @BeforeEach
    void setUp() {
        roomRepository.deleteAll();
        doctorRepository.deleteAll();
        branchRepository.deleteAll();
        departmentRepository.deleteAll();
        specialtyRepository.deleteAll();
        userRepository.deleteAll();

        User admin = userRepository.save(new User("admin@aster.ae", "+971501111111", "hash", Role.ADMIN, "Admin User"));
        User doc = userRepository.save(new User("doc@aster.ae", "+971502222222", "hash", Role.DOCTOR, "Dr. Alice"));
        User rec = userRepository.save(new User("rec@aster.ae", "+971503333333", "hash", Role.RECEPTIONIST, "Receptionist User"));
        User pat = userRepository.save(new User("pat@aster.ae", "+971504444444", "hash", Role.PATIENT, "Patient User"));

        adminToken = "Bearer " + jwtTokenProvider.generateAccessToken(admin);
        doctorToken = "Bearer " + jwtTokenProvider.generateAccessToken(doc);
        receptionistToken = "Bearer " + jwtTokenProvider.generateAccessToken(rec);
        patientToken = "Bearer " + jwtTokenProvider.generateAccessToken(pat);
    }

    // ==========================================
    // BRANCH TESTS
    // ==========================================

    @Test
    @DisplayName("Public can list active branches unauthenticated; inactive branches hidden")
    void publicCanListActiveBranchesOnly() throws Exception {
        Branch b1 = branchRepository.save(new Branch("Mankhool Hospital", "MANK-01", "Al Mankhool", "Dubai", "043940000", "info@aster.ae"));
        Branch b2 = new Branch("Old Inactive Branch", "OLD-01", "Deira", "Dubai", "043940001", "old@aster.ae");
        b2.setActive(false);
        branchRepository.save(b2);

        // Unauthenticated list
        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("MANK-01"));

        // Unauthenticated get active branch
        mockMvc.perform(get("/api/branches/" + b1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MANK-01"));

        // Unauthenticated get inactive branch returns 404
        mockMvc.perform(get("/api/branches/" + b2.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Branch write: 401 unauthenticated, 403 for DOCTOR, RECEPTIONIST, PATIENT")
    void branchWriteSecurityChecks() throws Exception {
        BranchRequest req = new BranchRequest("Qusais Clinic", "QUS-01", "Al Qusais", "Dubai", "042940000", "qus@aster.ae");
        String json = objectMapper.writeValueAsString(req);

        // 401 unauthenticated
        mockMvc.perform(post("/api/branches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());

        // 403 for wrong roles
        String[] wrongTokens = { doctorToken, receptionistToken, patientToken };
        for (String token : wrongTokens) {
            mockMvc.perform(post("/api/branches")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Branch happy path: ADMIN create, update, soft delete")
    void branchHappyPath() throws Exception {
        BranchRequest createReq = new BranchRequest("Mankhool Hospital", "MANK-01", "Al Mankhool", "Dubai", "043940000", "info@aster.ae");

        // 1. Create
        String response = mockMvc.perform(post("/api/branches")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MANK-01"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn().getResponse().getContentAsString();

        Long branchId = objectMapper.readTree(response).get("id").asLong();

        // 2. Update
        BranchRequest updateReq = new BranchRequest("Aster Mankhool Hospital", "MANK-01", "Kuwait Street", "Dubai", "043940000", "mankhool@aster.ae");
        mockMvc.perform(put("/api/branches/" + branchId)
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aster Mankhool Hospital"));

        // 3. Soft Delete
        mockMvc.perform(delete("/api/branches/" + branchId)
                .header("Authorization", adminToken))
                .andExpect(status().isOk());

        Branch branchInDb = branchRepository.findById(branchId).orElseThrow();
        assertThat(branchInDb.isActive()).isFalse();
    }

    @Test
    @DisplayName("Branch 409 Conflict: duplicate code and deactivation rejected if referenced")
    void branchConflictRules() throws Exception {
        Branch b1 = branchRepository.save(new Branch("Branch 1", "B-01", "Add 1", "Dubai", "111", "b1@aster.ae"));
        Department dept = departmentRepository.save(new Department("Cardiology", "CARD-01", "Heart"));

        // 1. Duplicate code rejected with 409
        BranchRequest duplicateReq = new BranchRequest("Another Branch", "B-01", "Add 2", "Dubai", "222", "b2@aster.ae");
        mockMvc.perform(post("/api/branches")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Branch with code 'B-01' already exists"));

        // 2. Add an active room referencing b1
        roomRepository.save(new Room("101", b1, dept, RoomType.CONSULTATION));

        // Soft delete rejected with 409
        mockMvc.perform(delete("/api/branches/" + b1.getId())
                .header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot deactivate branch: referenced by active rooms"));

        // Also test doctor reference rejection
        roomRepository.deleteAll();
        User docUser = userRepository.save(new User("doc3@aster.ae", "+971509991122", "pass", Role.DOCTOR, "Dr. Ref"));
        Doctor doc = new Doctor(docUser, "Bio", "MBBS", "English", 5, BigDecimal.valueOf(300));
        doc.setBranches(Set.of(b1));
        doctorRepository.save(doc);

        mockMvc.perform(delete("/api/branches/" + b1.getId())
                .header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot deactivate branch: referenced by active doctors"));
    }

    // ==========================================
    // DEPARTMENT TESTS
    // ==========================================

    @Test
    @DisplayName("Department write: 401 unauthenticated, 403 wrong roles, 409 duplicate code and reference check")
    void departmentTests() throws Exception {
        DepartmentRequest createReq = new DepartmentRequest("Neurology", "NEURO-01", "Brain & nerves");
        String json = objectMapper.writeValueAsString(createReq);

        // 401
        mockMvc.perform(post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());

        // 403
        mockMvc.perform(post("/api/departments")
                .header("Authorization", doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden());

        // 201 Happy path create
        String res = mockMvc.perform(post("/api/departments")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("NEURO-01"))
                .andReturn().getResponse().getContentAsString();

        Long deptId = objectMapper.readTree(res).get("id").asLong();

        // 409 Duplicate code
        mockMvc.perform(post("/api/departments")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict());

        // Attach an active room
        Branch branch = branchRepository.save(new Branch("B1", "B-01", "Add", "Dubai", "111", "b@ae"));
        Department dept = departmentRepository.findById(deptId).orElseThrow();
        roomRepository.save(new Room("202", branch, dept, RoomType.PROCEDURE));

        // 409 Deactivation rejected when referenced by active rooms
        mockMvc.perform(delete("/api/departments/" + deptId)
                .header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot deactivate department: referenced by active rooms"));
    }

    // ==========================================
    // SPECIALTY TESTS
    // ==========================================

    @Test
    @DisplayName("Specialty write: 401 unauthenticated, 403 wrong roles, 409 duplicate code and doctor reference check")
    void specialtyTests() throws Exception {
        SpecialtyRequest createReq = new SpecialtyRequest("Cardiology", "CARD-01", "Heart care");
        String json = objectMapper.writeValueAsString(createReq);

        // 401
        mockMvc.perform(post("/api/specialties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isUnauthorized());

        // 403
        mockMvc.perform(post("/api/specialties")
                .header("Authorization", patientToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden());

        // 201 Happy path create
        String res = mockMvc.perform(post("/api/specialties")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long specId = objectMapper.readTree(res).get("id").asLong();

        // Attach an active doctor
        Specialty spec = specialtyRepository.findById(specId).orElseThrow();
        User docUser = userRepository.save(new User("doc4@aster.ae", "+971509993344", "pass", Role.DOCTOR, "Dr. Heart"));
        Doctor doc = new Doctor(docUser, "Bio", "MBBS", "English", 8, BigDecimal.valueOf(400));
        doc.setSpecialties(Set.of(spec));
        doctorRepository.save(doc);

        // 409 Deactivation rejected when referenced by active doctor
        mockMvc.perform(delete("/api/specialties/" + specId)
                .header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot deactivate specialty: referenced by active doctors"));
    }

    // ==========================================
    // ROOM TESTS
    // ==========================================

    @Test
    @DisplayName("Rooms: 401 unauthenticated on GET, authenticated roles can read, writes ADMIN only")
    void roomEndpointsSecurityAndCrud() throws Exception {
        Branch branch = branchRepository.save(new Branch("B1", "B-01", "Add", "Dubai", "111", "b@ae"));
        Department dept = departmentRepository.save(new Department("D1", "D-01", "Desc"));

        // 1. GET /api/rooms is authenticated (401 unauthenticated)
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());

        // 2. DOCTOR and PATIENT can read rooms
        mockMvc.perform(get("/api/rooms").header("Authorization", doctorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/rooms").header("Authorization", patientToken))
                .andExpect(status().isOk());

        // 3. Write: 403 for DOCTOR, RECEPTIONIST, PATIENT
        RoomRequest roomReq = new RoomRequest("301", branch.getId(), dept.getId(), RoomType.CONSULTATION);
        String json = objectMapper.writeValueAsString(roomReq);

        mockMvc.perform(post("/api/rooms")
                .header("Authorization", doctorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isForbidden());

        // 4. ADMIN happy path create
        String res = mockMvc.perform(post("/api/rooms")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value("301"))
                .andReturn().getResponse().getContentAsString();

        Long roomId = objectMapper.readTree(res).get("id").asLong();

        // 5. 409 Duplicate room in same branch
        mockMvc.perform(post("/api/rooms")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Room with number '301' already exists in branch 'B1'"));

        // 6. ADMIN soft delete
        mockMvc.perform(delete("/api/rooms/" + roomId)
                .header("Authorization", adminToken))
                .andExpect(status().isOk());

        Room roomInDb = roomRepository.findById(roomId).orElseThrow();
        assertThat(roomInDb.isActive()).isFalse();
    }
}
