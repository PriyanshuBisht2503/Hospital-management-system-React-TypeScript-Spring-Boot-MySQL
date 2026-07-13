-- V5__add_version_and_audit_columns.sql
-- Add optimistic locking (version) and auditing (created_by, updated_by) fields to tables inheriting from BaseEntity

ALTER TABLE users 
    ADD COLUMN version BIGINT DEFAULT 1 NOT NULL,
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE departments 
    ADD COLUMN version BIGINT DEFAULT 1 NOT NULL,
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE patients 
    ADD COLUMN version BIGINT DEFAULT 1 NOT NULL,
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE doctors 
    ADD COLUMN version BIGINT DEFAULT 1 NOT NULL,
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE appointments 
    ADD COLUMN version BIGINT DEFAULT 1 NOT NULL,
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);

ALTER TABLE medical_records 
    ADD COLUMN version BIGINT DEFAULT 1 NOT NULL,
    ADD COLUMN created_by VARCHAR(100),
    ADD COLUMN updated_by VARCHAR(100);
