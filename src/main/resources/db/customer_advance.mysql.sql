-- Customer advance / token payments (extends system without altering bills or bill_payments)
-- Run once on MySQL. Safe to run if tables already exist (check manually).

CREATE TABLE IF NOT EXISTS customer_advance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    remaining_amount DECIMAL(14, 2) NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_customer_advance_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX idx_customer_advance_customer ON customer_advance (customer_id);
CREATE INDEX idx_customer_advance_remaining ON customer_advance (customer_id, remaining_amount);

CREATE TABLE IF NOT EXISTS customer_advance_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    advance_id BIGINT NOT NULL,
    bill_kind VARCHAR(16) NOT NULL COMMENT 'GST or NON_GST — matches bill_payments.bill_kind',
    bill_id BIGINT NOT NULL,
    amount_used DECIMAL(14, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_advance_usage_advance FOREIGN KEY (advance_id) REFERENCES customer_advance (id)
);

CREATE INDEX idx_advance_usage_bill ON customer_advance_usage (bill_kind, bill_id);
CREATE INDEX idx_advance_usage_advance ON customer_advance_usage (advance_id);
