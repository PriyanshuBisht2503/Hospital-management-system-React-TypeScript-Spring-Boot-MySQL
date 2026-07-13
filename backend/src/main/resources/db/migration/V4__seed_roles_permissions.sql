-- V4__seed_roles_permissions.sql
-- Seed default Roles and Administrative Permissions

-- 1. Insert Core Roles
INSERT INTO roles (name, description) VALUES
('ROLE_ADMIN', 'System Administrator with full access to hospital configurations and user management'),
('ROLE_DOCTOR', 'Medical practitioner responsible for patient care, diagnoses, and prescriptions'),
('ROLE_PATIENT', 'Hospital patient with access to personal appointments, billing, and history'),
('ROLE_RECEPTIONIST', 'Front-desk administrative staff managing appointments, admissions, and billing'),
('ROLE_PHARMACIST', 'Pharmacy operator managing inventory and medicine sales'),
('ROLE_LAB_TECHNICIAN', 'Laboratory operator processing lab test orders and uploading reports');

-- 2. Insert Core Permissions
INSERT INTO permissions (name, description) VALUES
('WRITE_USERS', 'Create, update, and lock user profiles'),
('READ_USERS', 'View user profiles and activity logs'),
('WRITE_PATIENT', 'Create and modify patient clinical profiles'),
('READ_PATIENT_RECORDS', 'View patient diagnosis, prescription history, and medical records'),
('WRITE_PRESCRIPTION', 'Write and adjust prescriptions'),
('BOOK_APPOINTMENT', 'Create doctor appointment slots and allocate tokens'),
('PROCESS_BILLING', 'Generate invoices, register payments, and calculate refunds'),
('WRITE_LAB_REPORTS', 'Process lab orders and upload report PDFs'),
('MANAGE_INVENTORY', 'Add medicines, check stock levels, and log pharmacy purchases');

-- 3. Map Permissions to Admin Role (All Permissions)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ROLE_ADMIN';

-- 4. Map Permissions to Doctor Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p 
WHERE r.name = 'ROLE_DOCTOR' 
AND p.name IN ('READ_PATIENT_RECORDS', 'WRITE_PRESCRIPTION', 'READ_USERS');

-- 5. Map Permissions to Receptionist Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p 
WHERE r.name = 'ROLE_RECEPTIONIST' 
AND p.name IN ('WRITE_PATIENT', 'BOOK_APPOINTMENT', 'PROCESS_BILLING', 'READ_USERS');

-- 6. Map Permissions to Pharmacist Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p 
WHERE r.name = 'ROLE_PHARMACIST' 
AND p.name IN ('MANAGE_INVENTORY', 'PROCESS_BILLING');

-- 7. Map Permissions to Lab Tech Role
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p 
WHERE r.name = 'ROLE_LAB_TECHNICIAN' 
AND p.name IN ('WRITE_LAB_REPORTS');
