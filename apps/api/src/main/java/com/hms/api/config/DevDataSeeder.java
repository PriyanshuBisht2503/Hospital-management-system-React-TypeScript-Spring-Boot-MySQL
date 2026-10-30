package com.hms.api.config;

import com.hms.api.domain.*;
import com.hms.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.dev-admin.email:admin@aster.ae}")
    private String adminEmail;

    @Value("${app.auth.dev-admin.password:Admin@Aster123}")
    private String adminPassword;

    @Value("${app.auth.dev-admin.full-name:System Administrator}")
    private String adminFullName;

    public DevDataSeeder(
        UserRepository userRepository,
        BranchRepository branchRepository,
        DepartmentRepository departmentRepository,
        SpecialtyRepository specialtyRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdminUser();
        seedMasterData();
    }

    private void seedAdminUser() {
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            log.info("No ADMIN user found. Seeding initial dev ADMIN user: {}", adminEmail);
            User admin = new User(
                adminEmail,
                "+971500000001",
                passwordEncoder.encode(adminPassword),
                Role.ADMIN,
                adminFullName
            );
            userRepository.save(admin);
            log.info("Dev ADMIN user seeded successfully.");
        }
    }

    private void seedMasterData() {
        if (branchRepository.count() == 0) {
            log.info("Seeding initial hospital branches...");
            Branch b1 = new Branch(
                "Aster Hospital Mankhool",
                "DXB-MANKHOOL",
                "Kuwait Street, Al Mankhool",
                "Dubai",
                "+97144400500",
                "mankhool@aster.ae"
            );
            Branch b2 = new Branch(
                "Aster Hospital Al Qusais",
                "DXB-QUSAIS",
                "9A Street, Al Qusais Industrial 2",
                "Dubai",
                "+97144400600",
                "qusais@aster.ae"
            );
            branchRepository.save(b1);
            branchRepository.save(b2);
        }

        if (departmentRepository.count() == 0) {
            log.info("Seeding initial departments...");
            departmentRepository.save(new Department("Cardiology", "CARDIO", "Cardiovascular Medicine and Interventional Sciences"));
            departmentRepository.save(new Department("Orthopaedics", "ORTHO", "Bone, Joint and Musculoskeletal Care"));
            departmentRepository.save(new Department("Internal Medicine", "INTERNAL", "Comprehensive Outpatient and Inpatient Adult Medicine"));
        }

        if (specialtyRepository.count() == 0) {
            log.info("Seeding initial specialties...");
            specialtyRepository.save(new Specialty("Interventional Cardiology", "SPEC-INT-CARD", "Catheter-based treatment of structural heart diseases"));
            specialtyRepository.save(new Specialty("Joint Replacement Surgery", "SPEC-JOINT", "Total knee and hip arthroplasty"));
            specialtyRepository.save(new Specialty("General Internal Medicine", "SPEC-GEN-MED", "Diagnosis and treatment of acute and chronic adult illnesses"));
        }
    }
}
