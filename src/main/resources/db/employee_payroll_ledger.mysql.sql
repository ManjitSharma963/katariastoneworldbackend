-- Payroll ledger (employee advances + salary settlements).
-- This is the source of truth for payroll calculations.
-- Safe to run multiple times (CREATE TABLE IF NOT EXISTS + indexes).

CREATE TABLE IF NOT EXISTS employee_payroll_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT NOT NULL,
    location VARCHAR(50) NOT NULL,
    event_type VARCHAR(32) NOT NULL COMMENT 'ADVANCE_GIVEN, ADVANCE_APPLIED, SALARY_CASH_PAID',
    amount DECIMAL(14,2) NOT NULL,
    payment_mode VARCHAR(32) NULL COMMENT 'CASH, UPI, BANK_TRANSFER, CHEQUE, OTHER',
    event_date DATE NOT NULL,
    month VARCHAR(7) NOT NULL COMMENT 'YYYY-MM derived from event_date',
    notes VARCHAR(512) NULL,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    INDEX idx_emp_payroll_emp_month (employee_id, month),
    INDEX idx_emp_payroll_loc_date (location, event_date),
    INDEX idx_emp_payroll_emp_date (employee_id, event_date)
);

