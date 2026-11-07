package com.hms.api.repository;

import com.hms.api.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class MasterDataRepositoryTest {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("Should persist and query active branches")
    void shouldPersistAndQueryActiveBranches() {
        Branch active = new Branch("Mankhool Branch", "TEST-BR-1", "Al Mankhool", "Dubai", "+97140001", "m@example.com");
        Branch inactive = new Branch("Old Clinic", "TEST-BR-2", "Deira", "Dubai", "+97140002", "d@example.com");
        inactive.setActive(false);

        branchRepository.save(active);
        branchRepository.save(inactive);

        Page<Branch> activeBranches = branchRepository.findByIsActiveTrue(PageRequest.of(0, 10));
        assertThat(activeBranches.getContent()).anyMatch(b -> b.getCode().equals("TEST-BR-1"));
        assertThat(activeBranches.getContent()).noneMatch(b -> b.getCode().equals("TEST-BR-2"));
    }

    @Test
    @DisplayName("Should enforce unique room number within the same branch")
    void shouldEnforceUniqueRoomNumberPerBranch() {
        Branch branch = branchRepository.save(new Branch("Room Branch", "RM-BR", "Addr", "Dubai", "+971400", "b@a.ae"));
        Department dept = departmentRepository.save(new Department("OPD Dept", "OPD-D", "OPD"));

        Room r1 = new Room("101", branch, dept, RoomType.CONSULTATION);
        roomRepository.saveAndFlush(r1);

        Room r2 = new Room("101", branch, dept, RoomType.CONSULTATION);
        assertThatThrownBy(() -> roomRepository.saveAndFlush(r2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Should allow same room number across different branches")
    void shouldAllowSameRoomNumberInDifferentBranches() {
        Branch b1 = branchRepository.save(new Branch("B1", "B1-CODE", "Addr1", "Dubai", null, null));
        Branch b2 = branchRepository.save(new Branch("B2", "B2-CODE", "Addr2", "Dubai", null, null));
        Department dept = departmentRepository.save(new Department("Gen Dept", "GEN-D", "General"));

        Room r1 = roomRepository.save(new Room("202", b1, dept, RoomType.PROCEDURE));
        Room r2 = roomRepository.save(new Room("202", b2, dept, RoomType.PROCEDURE));

        assertThat(r1.getId()).isNotNull();
        assertThat(r2.getId()).isNotNull();
        assertThat(r1.getId()).isNotEqualTo(r2.getId());
    }
}
