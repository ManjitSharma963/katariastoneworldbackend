-- Audit log for cancelled (soft-deleted) bills. Hibernate ddl-auto=update also creates this from BillCancellationLog.
CREATE TABLE IF NOT EXISTS bill_cancellation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_kind VARCHAR(16) NOT NULL,
    bill_id BIGINT NOT NULL,
    bill_number VARCHAR(50) NOT NULL,
    location VARCHAR(50) NOT NULL,
    bill_date DATE NOT NULL,
    customer_name VARCHAR(200) NULL,
    customer_phone VARCHAR(20) NULL,
    total_amount DECIMAL(14,2) NOT NULL,
    paid_from_payments DECIMAL(14,2) NOT NULL,
    advance_applied DECIMAL(14,2) NOT NULL,
    in_hand_collected DECIMAL(14,2) NOT NULL,
    payment_method_summary VARCHAR(512) NULL,
    payment_status VARCHAR(32) NULL,
    cancelled_at DATETIME(6) NOT NULL,
    cancelled_by_user_id BIGINT NULL,
    INDEX idx_bill_cancel_loc_bill_date (location, bill_date),
    INDEX idx_bill_cancel_loc_cancelled_at (location, cancelled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
