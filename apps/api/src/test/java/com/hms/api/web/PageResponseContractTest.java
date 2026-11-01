package com.hms.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hms.api.domain.Branch;
import com.hms.api.dto.PageResponse;
import com.hms.api.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageResponseContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BranchRepository branchRepository;

    @BeforeEach
    void setUp() {
        branchRepository.deleteAll();
    }

    @Test
    @DisplayName("PageResponse JSON contract contains exactly content, pageNumber, pageSize, totalElements, totalPages, isFirst, isLast")
    void pageResponseSerializedContractMatchesExactKeys() throws Exception {
        PageResponse<String> response = new PageResponse<>(
            List.of("demo-a", "demo-b"),
            0,
            6,
            15,
            3,
            true,
            false
        );

        String json = objectMapper.writeValueAsString(response);
        JsonNode root = objectMapper.readTree(json);

        // Collect all top-level JSON field names
        List<String> fieldNames = new ArrayList<>();
        Iterator<String> it = root.fieldNames();
        while (it.hasNext()) {
            fieldNames.add(it.next());
        }

        Set<String> expectedExactKeys = Set.of(
            "content",
            "pageNumber",
            "pageSize",
            "totalElements",
            "totalPages",
            "isFirst",
            "isLast"
        );

        assertThat(fieldNames).containsExactlyInAnyOrderElementsOf(expectedExactKeys);
        assertThat(root.get("content").isArray()).isTrue();
        assertThat(root.get("pageNumber").asInt()).isEqualTo(0);
        assertThat(root.get("pageSize").asInt()).isEqualTo(6);
        assertThat(root.get("totalElements").asLong()).isEqualTo(15L);
        assertThat(root.get("totalPages").asInt()).isEqualTo(3);
        assertThat(root.get("isFirst").asBoolean()).isTrue();
        assertThat(root.get("isLast").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("GET /api/branches?page=1&size=1 returns the second item with exact pagination contract")
    void pageOneSizeOneReturnsSecondItem() throws Exception {
        // Create 2 branches
        Branch b1 = branchRepository.save(new Branch("First Hospital", "BR-01", "Street 1", "Dubai", "+971 4 000 0001", "b1@example.com"));
        Branch b2 = branchRepository.save(new Branch("Second Hospital", "BR-02", "Street 2", "Dubai", "+971 4 000 0002", "b2@example.com"));

        // Query page=1, size=1 (should return b2, as page 0 has b1)
        mockMvc.perform(get("/api/branches")
                .param("page", "1")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.isFirst").value(false))
                .andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value("BR-02"))
                .andExpect(jsonPath("$.content[0].name").value("Second Hospital"));
    }
}
