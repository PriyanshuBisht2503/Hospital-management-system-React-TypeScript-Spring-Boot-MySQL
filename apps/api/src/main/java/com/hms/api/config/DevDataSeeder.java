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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Dev-only seeder. Strictly restricted to the 'dev' profile.
 * Seeds fictional demo data: branches, departments, specialties, doctors with weekly
 * schedules, sample patients and appointments in mixed states. Idempotent.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final ZoneId HOSPITAL_ZONE = ZoneId.of("Asia/Kolkata");

    public static final String DEMO_ADMIN_EMAIL = "admin123@hms.local";
    public static final String DEMO_DOCTOR_EMAIL = "doctor123@hms.local";
    public static final String DEMO_RECEPTIONIST_EMAIL = "receptionist123@hms.local";
    public static final String DEMO_PATIENT_EMAIL = "patient123@hms.local";

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(
        UserRepository userRepository,
        DoctorRepository doctorRepository,
        BranchRepository branchRepository,
        DepartmentRepository departmentRepository,
        SpecialtyRepository specialtyRepository,
        DoctorScheduleRepository scheduleRepository,
        PatientRepository patientRepository,
        AppointmentRepository appointmentRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
        this.specialtyRepository = specialtyRepository;
        this.scheduleRepository = scheduleRepository;
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedMasterData();
        seedDemoAccounts();
        seedDemoPatients();
        seedDoctorProfiles();
        seedSchedules();
        seedAppointments();
        logDemoWarningIfPresent();
    }

    private void seedMasterData() {
        if (branchRepository.count() == 0) {
            branchRepository.save(new Branch("Central Branch", "BRANCH-CENTRAL", "100 Medical City Way", "Dubai", "+971 4 000 0001", "central.branch@example.com"));
            branchRepository.save(new Branch("North Clinic", "BRANCH-NORTH", "200 Health Plaza", "Dubai", "+971 4 000 0002", "north.branch@example.com"));
        }

        if (departmentRepository.count() == 0) {
            departmentRepository.save(new Department("Cardiology", "CARDIO", "Cardiovascular Medicine and Interventional Sciences"));
            departmentRepository.save(new Department("Orthopaedics", "ORTHO", "Bone, Joint and Musculoskeletal Care"));
            departmentRepository.save(new Department("Internal Medicine", "INTERNAL", "Comprehensive Outpatient and Inpatient Adult Medicine"));
        }

        if (specialtyRepository.count() == 0) {
            specialtyRepository.save(new Specialty("Interventional Cardiology", "SPEC-INT-CARD", "Catheter-based treatment of structural heart diseases"));
            specialtyRepository.save(new Specialty("Joint Replacement Surgery", "SPEC-JOINT", "Total knee and hip arthroplasty"));
            specialtyRepository.save(new Specialty("General Internal Medicine", "SPEC-GEN-MED", "Diagnosis and treatment of acute and chronic adult illnesses"));
            specialtyRepository.save(new Specialty("Dermatology", "SPEC-DERM", "Medical and surgical skin care"));
            specialtyRepository.save(new Specialty("Paediatrics", "SPEC-PAED", "Child and adolescent health"));
            specialtyRepository.save(new Specialty("Sports Medicine", "SPEC-SPORTS", "Injury prevention and rehabilitation"));
        }
    }

    private void seedDemoAccounts() {
        if (userRepository.findByEmail(DEMO_ADMIN_EMAIL).isEmpty()) {
            userRepository.save(new User(DEMO_ADMIN_EMAIL, "+971 4 000 1001", passwordEncoder.encode("admin@123"), Role.ADMIN, "System Administrator (Demo)"));
        }
        if (userRepository.findByEmail(DEMO_RECEPTIONIST_EMAIL).isEmpty()) {
            userRepository.save(new User(DEMO_RECEPTIONIST_EMAIL, "+971 4 000 1003", passwordEncoder.encode("receptionist@123"), Role.RECEPTIONIST, "Reception Desk Staff (Demo)"));
        }
        if (userRepository.findByEmail(DEMO_PATIENT_EMAIL).isEmpty()) {
            userRepository.save(new User(DEMO_PATIENT_EMAIL, "+971 4 000 1004", null, Role.PATIENT, "Demo Patient"));
        }
    }

    private void seedDemoPatients() {
        User patientUser = userRepository.findByEmail(DEMO_PATIENT_EMAIL).orElse(null);
        if (patientUser != null && patientRepository.findByUserId(patientUser.getId()).isEmpty()) {
            Patient patient = new Patient("Demo Patient", "+971 4 000 1004", DEMO_PATIENT_EMAIL, LocalDate.of(1992, 6, 15), Gender.FEMALE);
            patient.setUser(patientUser);
            patientRepository.save(patient);
        }

        ensureUnlinkedPatient("Aisha Rahman", "+971 4 000 3001", "aisha.rahman@hms.local", LocalDate.of(1988, 3, 12), Gender.FEMALE);
        ensureUnlinkedPatient("Daniel Okafor", "+971 4 000 3002", "daniel.okafor@hms.local", LocalDate.of(1979, 11, 4), Gender.MALE);
        ensureUnlinkedPatient("Mei Lin Chen", "+971 4 000 3003", "mei.chen@hms.local", LocalDate.of(1995, 8, 21), Gender.FEMALE);
    }

    private void ensureUnlinkedPatient(String name, String phone, String email, LocalDate dob, Gender gender) {
        if (patientRepository.findByEmail(email).isEmpty() && patientRepository.findByPhone(phone).isEmpty()) {
            patientRepository.save(new Patient(name, phone, email, dob, gender));
        }
    }

    private void seedDoctorProfiles() {
        ensureDoctor(DEMO_DOCTOR_EMAIL, "+971 4 000 1002",
                "Senior Consultant Interventional Cardiologist with extensive clinical expertise in cardiovascular medicine and interventional care.",
                "MBBS, MD, DM (Cardiology), FACC", "English, Arabic, Hindi", 15, "350.00", "SPEC-INT-CARD", "BRANCH-CENTRAL");
        ensureDoctor("dr.ayesha@hms.local", "+971 4 000 2001",
                "Consultant dermatologist focused on medical dermatology and minor skin surgery.",
                "MBBS, MD (Dermatology)", "English, Urdu", 9, "250.00", "SPEC-DERM", "BRANCH-NORTH");
        ensureDoctor("dr.omar@hms.local", "+971 4 000 2002",
                "Orthopaedic surgeon specialising in hip and knee replacement.",
                "MBBS, MS (Orthopaedics)", "English, Arabic", 12, "300.00", "SPEC-JOINT", "BRANCH-CENTRAL");
        ensureDoctor("dr.rina@hms.local", "+971 4 000 2003",
                "Paediatrician with a special interest in childhood asthma and allergies.",
                "MBBS, MD (Paediatrics)", "English, Hindi", 7, "200.00", "SPEC-PAED", "BRANCH-NORTH");
        ensureDoctor("dr.karim@hms.local", "+971 4 000 2004",
                "Internal medicine physician managing chronic adult conditions.",
                "MBBS, MD (Internal Medicine)", "English, Arabic", 11, "220.00", "SPEC-GEN-MED", "BRANCH-CENTRAL");
        ensureDoctor("dr.lina@hms.local", "+971 4 000 2005",
                "Sports medicine physician working with amateur and professional athletes.",
                "MBBS, MSc (Sports Medicine)", "English, French", 6, "230.00", "SPEC-SPORTS", "BRANCH-NORTH");
    }

    private void ensureDoctor(String email, String phone, String bio, String qualifications, String languages,
                              int years, String fee, String specialtyCode, String branchCode) {
        User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.save(
                new User(email, phone, passwordEncoder.encode("doctor@123"), Role.DOCTOR, email.substring(0, email.indexOf('@')))));

        if (doctorRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        Doctor doctor = new Doctor(user, bio, qualifications, languages, years, new BigDecimal(fee));
        doctor.setActive(true);
        specialtyRepository.findByCode(specialtyCode).ifPresent(s -> doctor.getSpecialties().add(s));
        branchRepository.findByCode(branchCode).ifPresent(b -> doctor.getBranches().add(b));
        doctorRepository.save(doctor);
    }

    private void seedSchedules() {
        if (scheduleRepository.count() > 0) {
            return;
        }
        LocalDate validFrom = LocalDate.now(HOSPITAL_ZONE).minusDays(1);
        List<DayOfWeek> weekdays = List.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);

        for (Doctor doctor : doctorRepository.findAll()) {
            Branch branch = doctor.getBranches().stream().findFirst().orElse(null);
            if (branch == null) continue;
            for (DayOfWeek day : weekdays) {
                // Morning + afternoon blocks model the 13:00-14:00 break.
                scheduleRepository.save(new DoctorSchedule(doctor, branch, day, LocalTime.of(9, 0), LocalTime.of(13, 0), 30, validFrom));
                scheduleRepository.save(new DoctorSchedule(doctor, branch, day, LocalTime.of(14, 0), LocalTime.of(17, 0), 30, validFrom));
            }
        }
    }

    private void seedAppointments() {
        if (appointmentRepository.count() > 0) {
            return;
        }

        User demoDoctorUser = userRepository.findByEmail(DEMO_DOCTOR_EMAIL).orElse(null);
        User demoPatientUser = userRepository.findByEmail(DEMO_PATIENT_EMAIL).orElse(null);
        if (demoDoctorUser == null || demoPatientUser == null) {
            return;
        }

        Doctor doctor = doctorRepository.findByUserId(demoDoctorUser.getId()).orElse(null);
        Patient demoPatient = patientRepository.findByUserId(demoPatientUser.getId()).orElse(null);
        Branch branch = branchRepository.findByCode("BRANCH-CENTRAL").orElse(null);
        if (doctor == null || demoPatient == null || branch == null) {
            return;
        }

        // Demo patient: one past COMPLETED, one upcoming CONFIRMED, one upcoming BOOKED, one CANCELLED.
        Appointment completed = baseAppointment(demoPatient, doctor, branch, ist(-7, 10, 0), AppointmentStatus.COMPLETED, "Annual cardiac review");
        completed.setConfirmedAt(ist(-9, 9, 0));
        completed.setCheckedInAt(ist(-7, 9, 50));
        completed.setStartedAt(ist(-7, 10, 5));
        completed.setCompletedAt(ist(-7, 10, 35));
        appointmentRepository.save(completed);

        Appointment confirmed = baseAppointment(demoPatient, doctor, branch, ist(3, 10, 0), AppointmentStatus.CONFIRMED, "Follow-up consultation");
        confirmed.setConfirmedAt(ist(0, 8, 0));
        appointmentRepository.save(confirmed);

        appointmentRepository.save(baseAppointment(demoPatient, doctor, branch, ist(4, 11, 0), AppointmentStatus.BOOKED, "Blood pressure review"));

        Appointment cancelled = baseAppointment(demoPatient, doctor, branch, ist(5, 12, 0), AppointmentStatus.CANCELLED, "Routine check");
        cancelled.setCancelReason("Patient travelling abroad");
        cancelled.setCancelledBy(DEMO_PATIENT_EMAIL);
        appointmentRepository.save(cancelled);

        // Other sample patients, so Day Agenda / My Day look populated.
        patientRepository.findByEmail("aisha.rahman@hms.local").ifPresent(p -> {
            appointmentRepository.save(baseAppointment(p, doctor, branch, ist(3, 12, 0), AppointmentStatus.CONFIRMED, "Dermatology consult"));
            appointmentRepository.save(baseAppointment(p, doctor, branch, ist(6, 10, 0), AppointmentStatus.BOOKED, "Skin check"));
        });
        patientRepository.findByEmail("daniel.okafor@hms.local").ifPresent(p ->
            appointmentRepository.save(baseAppointment(p, doctor, branch, ist(3, 14, 30), AppointmentStatus.CONFIRMED, "Cardiology review")));
        patientRepository.findByEmail("mei.chen@hms.local").ifPresent(p ->
            appointmentRepository.save(baseAppointment(p, doctor, branch, ist(7, 11, 0), AppointmentStatus.BOOKED, "General consultation")));
    }

    private Appointment baseAppointment(Patient patient, Doctor doctor, Branch branch, Instant start,
                                        AppointmentStatus status, String reason) {
        Appointment appointment = new Appointment(patient, doctor, branch, start, start.plusSeconds(30 * 60),
                AppointmentType.IN_PERSON, status);
        appointment.setReasonForVisit(reason);
        appointment.setCreatedBy("system");
        return appointment;
    }

    private Instant ist(int daysFromToday, int hour, int minute) {
        LocalDate date = LocalDate.now(HOSPITAL_ZONE).plusDays(daysFromToday);
        return LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(HOSPITAL_ZONE).toInstant();
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
