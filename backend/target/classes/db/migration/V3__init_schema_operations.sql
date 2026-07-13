-- V3__init_schema_operations.sql
-- Hospital Management System Relational Persistence Schemas (Rooms, Admissions, Finance, Staff, Operations)

-- =========================================================================
-- 4. OPERATION & BED ALLOCATION MODULES
-- =========================================================================

CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_number VARCHAR(20) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    floor INT NOT NULL,
    cost_per_day DECIMAL(10,2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_type CHECK (type IN ('GENERAL', 'PRIVATE', 'SEMI_PRIVATE', 'ICU')),
    CONSTRAINT chk_room_cost CHECK (cost_per_day >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE beds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    bed_number VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_beds_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
    CONSTRAINT uk_room_bed UNIQUE (room_id, bed_number),
    CONSTRAINT chk_bed_status CHECK (status IN ('AVAILABLE', 'OCCUPIED', 'MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    bed_id BIGINT NOT NULL,
    doctor_id BIGINT NOT NULL,
    admission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discharge_date TIMESTAMP NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    admission_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_admissions_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_bed FOREIGN KEY (bed_id) REFERENCES beds(id) ON DELETE RESTRICT,
    CONSTRAINT fk_admissions_doctor FOREIGN KEY (doctor_id) REFERENCES doctors(id) ON DELETE RESTRICT,
    CONSTRAINT chk_admission_status CHECK (status IN ('ACTIVE', 'DISCHARGED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE discharges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admission_id BIGINT NOT NULL UNIQUE,
    discharge_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discharge_summary TEXT,
    final_invoice_id BIGINT,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_discharges_admission FOREIGN KEY (admission_id) REFERENCES admissions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================================
-- 5. BILLING & FINANCE MODULES
-- =========================================================================

CREATE TABLE insurance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    provider_name VARCHAR(150) NOT NULL,
    policy_number VARCHAR(50) NOT NULL UNIQUE,
    coverage_limit DECIMAL(12,2) NOT NULL,
    co_pay_percentage DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    expiry_date DATE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_insurance_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    CONSTRAINT chk_ins_limit CHECK (coverage_limit >= 0),
    CONSTRAINT chk_ins_copay CHECK (co_pay_percentage BETWEEN 0.00 AND 100.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    tax_amount DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    net_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    billing_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    due_date TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoices_patient FOREIGN KEY (patient_id) REFERENCES patients(id) ON DELETE RESTRICT,
    CONSTRAINT chk_invoice_status CHECK (status IN ('UNPAID', 'PAID', 'PARTIALLY_PAID', 'CANCELLED', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    item_ref_id BIGINT,
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(10,2) NOT NULL,
    tax_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    tax_amount DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT chk_item_type CHECK (item_type IN ('CONSULTATION', 'LAB_TEST', 'MEDICINE', 'ROOM_CHARGE')),
    CONSTRAINT chk_item_qty CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE RESTRICT,
    CONSTRAINT chk_pay_method CHECK (payment_method IN ('CASH', 'CARD', 'INSURANCE', 'ONLINE')),
    CONSTRAINT chk_pay_status CHECK (status IN ('SUCCESS', 'FAILED', 'REFUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================================
-- 6. STAFF ATTENDANCE, OPERATIONS & NOTIFICATIONS
-- =========================================================================

CREATE TABLE employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    job_title VARCHAR(100) NOT NULL,
    department_id BIGINT,
    salary DECIMAL(12,2) NOT NULL,
    hire_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL,
    CONSTRAINT chk_emp_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    check_in_time TIMESTAMP NULL,
    check_out_time TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PRESENT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT uk_emp_date UNIQUE (employee_id, work_date),
    CONSTRAINT chk_att_status CHECK (status IN ('PRESENT', 'ABSENT', 'LEAVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE leaves (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    leave_type VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_leaves_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE CASCADE,
    CONSTRAINT chk_leave_type CHECK (leave_type IN ('CASUAL', 'SICK', 'MATERNITY')),
    CONSTRAINT chk_leave_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_notif_type CHECK (type IN ('EMAIL', 'SMS', 'SYSTEM')),
    CONSTRAINT chk_notif_status CHECK (status IN ('UNREAD', 'READ'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    old_value JSON,
    new_value JSON,
    modified_by VARCHAR(100) NOT NULL,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
