-- V5: Scheduling and Appointment Engine Schema (MySQL)

CREATE TABLE patients (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNIQUE NULL,
    full_name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NULL,
    email VARCHAR(150) NULL,
    date_of_birth DATE NULL,
    gender VARCHAR(20) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_patients_contact CHECK (phone IS NOT NULL OR email IS NOT NULL),
    CONSTRAINT chk_patients_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'))
);

CREATE INDEX idx_patients_phone ON patients(phone);
CREATE INDEX idx_patients_email ON patients(email);
CREATE INDEX idx_patients_full_name ON patients(full_name);

CREATE TABLE doctor_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    day_of_week VARCHAR(10) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_minutes INT NOT NULL,
    break_start TIME NULL,
    break_end TIME NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_schedules_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT fk_schedules_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT chk_schedules_day CHECK (day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    CONSTRAINT chk_schedules_slot_minutes CHECK (slot_minutes BETWEEN 5 AND 120),
    CONSTRAINT chk_schedules_time CHECK (end_time > start_time)
);

CREATE INDEX idx_schedules_doctor_day ON doctor_schedules(doctor_id, day_of_week, is_active);

CREATE TABLE schedule_exceptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    start_time TIME NULL,
    end_time TIME NULL,
    reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exceptions_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT chk_exceptions_type CHECK (type IN ('LEAVE', 'HOLIDAY', 'EXTRA_HOURS'))
);

CREATE INDEX idx_exceptions_doctor_date ON schedule_exceptions(doctor_id, date);

CREATE TABLE appointments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason_for_visit VARCHAR(500) NULL,
    cancel_reason VARCHAR(500) NULL,
    cancelled_by VARCHAR(50) NULL,
    created_by VARCHAR(50) NULL,
    rescheduled_from_id BIGINT NULL,
    needs_rescheduling BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_24h_sent BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_2h_sent BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    confirmed_at TIMESTAMP NULL,
    checked_in_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    active_booking_slot VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status NOT IN ('CANCELLED', 'RESCHEDULED')
             THEN CONCAT(doctor_id, '_', DATE_FORMAT(start_time, '%Y%m%d%H%i%s'))
             ELSE NULL END
    ) STORED,
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT fk_appointments_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id),
    CONSTRAINT fk_appointments_branch FOREIGN KEY (branch_id) REFERENCES branches(id),
    CONSTRAINT fk_appointments_rescheduled_from FOREIGN KEY (rescheduled_from_id) REFERENCES appointments(id),
    CONSTRAINT chk_appointments_time CHECK (end_time > start_time),
    CONSTRAINT chk_appointments_type CHECK (type IN ('IN_PERSON', 'VIDEO', 'WALK_IN', 'FOLLOW_UP')),
    CONSTRAINT chk_appointments_status CHECK (status IN ('BOOKED', 'CONFIRMED', 'CHECKED_IN', 'IN_CONSULTATION', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'RESCHEDULED')),
    CONSTRAINT uk_active_doctor_slot UNIQUE (active_booking_slot)
);

CREATE INDEX idx_appointments_doctor_start ON appointments(doctor_id, start_time);
CREATE INDEX idx_appointments_patient_start ON appointments(patient_id, start_time);
CREATE INDEX idx_appointments_branch_start ON appointments(branch_id, start_time);
CREATE INDEX idx_appointments_status ON appointments(status);

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    patient_id BIGINT NULL,
    channel VARCHAR(20) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_patient FOREIGN KEY (patient_id) REFERENCES patients(id),
    CONSTRAINT chk_notifications_recipient CHECK (user_id IS NOT NULL OR patient_id IS NOT NULL),
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_notifications_status_created ON notifications(status, created_at);
