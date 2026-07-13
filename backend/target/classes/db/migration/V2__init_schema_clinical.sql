-- V2__init_schema_clinical.sql
-- Hospital Management System Relational Persistence Schemas (Clinical, Pharmacy, Lab, Rooms)

-- =========================================================================
-- 3. MEDICAL RECORDS & LABORATORY MODULES
-- =========================================================================

CREATE TABLE medical_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_id BIGINT,
    visit_date DATE NOT NULL,
    symptoms TEXT,
    notes TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_records_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT,
    CONSTRAINT fk_records_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE diagnoses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medical_record_id BIGINT NOT NULL,
    disease_code VARCHAR(10) NOT NULL,
    disease_name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    severity VARCHAR(20) DEFAULT 'MODERATE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_diagnoses_record FOREIGN KEY (medical_record_id) REFERENCES medical_records(id) ON DELETE CASCADE,
    CONSTRAINT chk_diagnosis_severity CHECK (severity IN ('MILD', 'MODERATE', 'SEVERE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prescriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medical_record_id BIGINT NOT NULL,
    instructions TEXT NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_prescriptions_record FOREIGN KEY (medical_record_id) REFERENCES medical_records(id) ON DELETE CASCADE,
    CONSTRAINT chk_prescription_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'DISCONTINUED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medicines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    brand VARCHAR(100) NOT NULL,
    generic_name VARCHAR(150),
    form VARCHAR(30) NOT NULL,
    strength VARCHAR(50) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_medicine_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medicine_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    medicine_id BIGINT NOT NULL,
    batch_number VARCHAR(50) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    expiry_date DATE NOT NULL,
    safety_threshold INT NOT NULL DEFAULT 10,
    last_restocked_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE,
    CONSTRAINT chk_inventory_qty CHECK (stock_quantity >= 0),
    CONSTRAINT chk_inventory_threshold CHECK (safety_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_tests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    code VARCHAR(10) NOT NULL UNIQUE,
    cost DECIMAL(10,2) NOT NULL,
    reference_range VARCHAR(100),
    unit VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_test_cost CHECK (cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    appointment_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_lab_orders_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_orders_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lab_orders_appt FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL,
    CONSTRAINT chk_lab_order_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE lab_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lab_order_id BIGINT NOT NULL,
    test_id BIGINT NOT NULL,
    result_value VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PRELIMINARY',
    pdf_report_path VARCHAR(255),
    uploaded_by BIGINT,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lab_reports_order FOREIGN KEY (lab_order_id) REFERENCES lab_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_reports_test FOREIGN KEY (test_id) REFERENCES lab_tests(id) ON DELETE RESTRICT,
    CONSTRAINT chk_report_status CHECK (status IN ('PRELIMINARY', 'FINAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
