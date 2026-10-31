package com.hms.api.config;

import com.hms.api.domain.*;
import com.hms.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Dev-only seeder. Strictly restricted to the 'dev' profile.
 * Seeds initial master data (branches, departments, specialties) and demo accounts
 * for each of the 4 system roles (ADMIN, DOCTOR, RECEPTIONIST, PATIENT).
 * Idempotent: checks for existence before creating any records.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    public static final String DEMO_ADMIN_EMAIL = "admin123@hms.local";
    public static final String DEMO_DOCTOR_EMAIL = "doctor123@hms.local";
    public static final String DEMO_RECEPTIONIST_EMAIL = "receptionist123@hms.local";
    public static final String DEMO_PATIENT_EMAIL = "patient123@hms.local";

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(
        UserRepository userRepository,
        DoctorRepository doctorRepository,
        BranchRepository branchRepository,
        DepartmentRepository departmentRepository,
        SpecialtyRepository specialtyRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedMasterData();
        seedDemoAccounts();
        logDemoWarningIfPresent();
    }

    private void seedMasterData() {
        if (branchRepository.count() == 0) {
            log.info("[DevDataSeeder] Seeding initial hospital branches...");
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
            log.info("[DevDataSeeder] Seeding initial departments...");
            departmentRepository.save(new Department("Cardiology", "CARDIO", "Cardiovascular Medicine and Interventional Sciences"));
            departmentRepository.save(new Department("Orthopaedics", "ORTHO", "Bone, Joint and Musculoskeletal Care"));
            departmentRepository.save(new Department("Internal Medicine", "INTERNAL", "Comprehensive Outpatient and Inpatient Adult Medicine"));
        }

        if (specialtyRepository.count() == 0) {
            log.info("[DevDataSeeder] Seeding initial specialties...");
            specialtyRepository.save(new Specialty("Interventional Cardiology", "SPEC-INT-CARD", "Catheter-based treatment of structural heart diseases"));
            specialtyRepository.save(new Specialty("Joint Replacement Surgery", "SPEC-JOINT", "Total knee and hip arthroplasty"));
            specialtyRepository.save(new Specialty("General Internal Medicine", "SPEC-GEN-MED", "Diagnosis and treatment of acute and chronic adult illnesses"));
        }
    }

    private void seedDemoAccounts() {
        seedAdminDemoAccount();
        seedDoctorDemoAccount();
        seedReceptionistDemoAccount();
        seedPatientDemoAccount();
    }

    private void seedAdminDemoAccount() {
        if (userRepository.findByEmail(DEMO_ADMIN_EMAIL).isEmpty()) {
            log.info("[DevDataSeeder] Seeding ADMIN demo account: {}", DEMO_ADMIN_EMAIL);
            User admin = new User(
                DEMO_ADMIN_EMAIL,
                "+971509990001",
                passwordEncoder.encode("admin@123"),
                Role.ADMIN,
                "System Administrator (Demo)"
            );
            userRepository.save(admin);
            log.info("[DevDataSeeder] ADMIN demo account seeded successfully.");
        }
    }

    private void seedDoctorDemoAccount() {
        Optional<User> doctorOpt = userRepository.findByEmail(DEMO_DOCTOR_EMAIL);
        User doctorUser;
        if (doctorOpt.isEmpty()) {
            log.info("[DevDataSeeder] Seeding DOCTOR demo account: {}", DEMO_DOCTOR_EMAIL);
            doctorUser = new User(
                DEMO_DOCTOR_EMAIL,
                "+971509990002",
                passwordEncoder.encode("doctor@123"),
                Role.DOCTOR,
                "Dr. Sarah Khan (Demo)"
            );
            doctorUser = userRepository.save(doctorUser);
            log.info("[DevDataSeeder] DOCTOR demo user seeded successfully.");
        } else {
            doctorUser = doctorOpt.get();
        }

        if (doctorRepository.findByUserId(doctorUser.getId()).isEmpty()) {
            log.info("[DevDataSeeder] Seeding DOCTOR profile for: {}", DEMO_DOCTOR_EMAIL);
            Doctor doctor = new Doctor(
                doctorUser,
                "Senior Consultant Interventional Cardiologist with extensive clinical expertise in cardiovascular medicine and interventional care.",
                "MBBS, MD, DM (Cardiology), FACC",
                "English, Arabic, Hindi",
                15,
                new BigDecimal("350.00")
            );
            doctor.setActive(true);

            specialtyRepository.findByCode("SPEC-INT-CARD").ifPresent(spec -> doctor.getSpecialties().add(spec));
            branchRepository.findByCode("DXB-MANKHOOL").ifPresent(branch -> doctor.getBranches().add(branch));

            doctorRepository.save(doctor);
            log.info("[DevDataSeeder] DOCTOR profile row seeded successfully.");
        }
    }

    private void seedReceptionistDemoAccount() {
        if (userRepository.findByEmail(DEMO_RECEPTIONIST_EMAIL).isEmpty()) {
            log.info("[DevDataSeeder] Seeding RECEPTIONIST demo account: {}", DEMO_RECEPTIONIST_EMAIL);
            User receptionist = new User(
                DEMO_RECEPTIONIST_EMAIL,
                "+971509990003",
                passwordEncoder.encode("receptionist@123"),
                Role.RECEPTIONIST,
                "Reception Desk Staff (Demo)"
            );
            userRepository.save(receptionist);
            log.info("[DevDataSeeder] RECEPTIONIST demo account seeded successfully.");
        }
    }

    private void seedPatientDemoAccount() {
        if (userRepository.findByEmail(DEMO_PATIENT_EMAIL).isEmpty()) {
            log.info("[DevDataSeeder] Seeding PATIENT demo account: {}", DEMO_PATIENT_EMAIL);
            User patient = new User(
                DEMO_PATIENT_EMAIL,
                "+971509990004",
                null, // No password: OTP login only
                Role.PATIENT,
                "Demo Patient"
            );
            userRepository.save(patient);
            log.info("[DevDataSeeder] PATIENT demo account seeded successfully.");
        }
    }

    private void logDemoWarningIfPresent() {
        boolean demoExists = userRepository.findByEmail(DEMO_ADMIN_EMAIL).isPresent()
            || userRepository.findByEmail(DEMO_DOCTOR_EMAIL).isPresent()
            || userRepository.findByEmail(DEMO_RECEPTIONIST_EMAIL).isPresent()
            || userRepository.findByEmail(DEMO_PATIENT_EMAIL).isPresent();

        if (demoExists) {
            log.warn("********************************************************************************");
            log.warn("** [SECURITY WARNING] DEMO ACCOUNTS DETECTED IN DATABASE!                     **");
            log.warn("** Demo accounts: {}, {},                     **", DEMO_ADMIN_EMAIL, DEMO_DOCTOR_EMAIL);
            log.warn("**                {}, {}              **", DEMO_RECEPTIONIST_EMAIL, DEMO_PATIENT_EMAIL);
            log.warn("** THESE ACCOUNTS ARE FOR LOCAL DEV ONLY AND MUST NEVER EXIST IN PRODUCTION!  **");
            log.warn("********************************************************************************");
        }
    }
}
