package com.hms.api.repository;

import com.hms.api.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DoctorRepositoryTest {

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Test
    @DisplayName("Should save doctor with associated specialties and branches in one transaction")
    void shouldSaveDoctorWithSpecialtiesAndBranches() {
        User docUser = userRepository.save(new User("dr.ayman@example.com", "+971501234567", "pass", Role.DOCTOR, "Dr. Ayman Saeed"));
        Specialty cardio = specialtyRepository.save(new Specialty("Cardiology", "CARD-SPEC", "Heart"));
        Branch branch = branchRepository.save(new Branch("Central Branch", "CENTRAL-BR", "Dubai", "Dubai", null, null));

        Doctor doctor = new Doctor(docUser, "Senior Cardiologist", "MBBS, MD", "English, Arabic", 15, new BigDecimal("450.00"));
        doctor.getSpecialties().add(cardio);
        doctor.getBranches().add(branch);

        Doctor saved = doctorRepository.saveAndFlush(doctor);
        assertThat(saved.getId()).isNotNull();

        Optional<Doctor> fetched = doctorRepository.findByIdAndIsActiveTrue(saved.getId());
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getUser().getFullName()).isEqualTo("Dr. Ayman Saeed");
        assertThat(fetched.get().getSpecialties()).hasSize(1);
        assertThat(fetched.get().getBranches()).hasSize(1);
        assertThat(fetched.get().getConsultationFee()).isEqualByComparingTo("450.00");
    }

    @Test
    @DisplayName("Should exclude inactive (soft-deleted) doctors from public searches")
    void shouldExcludeInactiveDoctorsFromPublicSearches() {
        User u1 = userRepository.save(new User("active.doc@example.com", "+971500000010", "pass", Role.DOCTOR, "Dr. Active"));
        User u2 = userRepository.save(new User("inactive.doc@example.com", "+971500000020", "pass", Role.DOCTOR, "Dr. Inactive"));

        Doctor activeDoc = new Doctor(u1, "Active Bio", "MD", "English", 5, new BigDecimal("300.00"));
        Doctor inactiveDoc = new Doctor(u2, "Inactive Bio", "MD", "English", 10, new BigDecimal("300.00"));
        inactiveDoc.setActive(false);

        doctorRepository.save(activeDoc);
        doctorRepository.save(inactiveDoc);

        Page<Doctor> result = doctorRepository.searchActiveDoctors(null, null, "Active", PageRequest.of(0, 10));
        assertThat(result.getContent()).anyMatch(d -> d.getUser().getFullName().equals("Dr. Active"));
        assertThat(result.getContent()).noneMatch(d -> d.getUser().getFullName().equals("Dr. Inactive"));
    }
}
