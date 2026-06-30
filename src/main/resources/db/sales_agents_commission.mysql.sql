-- Manual migration: sales agents + bill commission columns (same as Flyway V8)

CREATE TABLE IF NOT EXISTS sales_agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(20) NULL,
    email VARCHAR(200) NULL,
    default_commission_type VARCHAR(20) NOT NULL DEFAULT 'PERCENTAGE',
    default_commission_value DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    notes TEXT NULL,
    location VARCHAR(50) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sales_agents_location (location),
    INDEX idx_sales_agents_name (name)
);

ALTER TABLE bills_gst
    ADD COLUMN agent_id BIGINT NULL,
    ADD COLUMN agent_commission_type VARCHAR(20) NULL,
    ADD COLUMN agent_commission_value DECIMAL(10, 2) NULL,
    ADD COLUMN agent_commission_amount DECIMAL(12, 2) NULL,
    ADD COLUMN agent_commission_status VARCHAR(20) NULL,
    ADD COLUMN agent_commission_notes TEXT NULL,
    ADD INDEX idx_bills_gst_agent (agent_id);

ALTER TABLE bills_non_gst
    ADD COLUMN agent_id BIGINT NULL,
    ADD COLUMN agent_commission_type VARCHAR(20) NULL,
    ADD COLUMN agent_commission_value DECIMAL(10, 2) NULL,
    ADD COLUMN agent_commission_amount DECIMAL(12, 2) NULL,
    ADD COLUMN agent_commission_status VARCHAR(20) NULL,
    ADD COLUMN agent_commission_notes TEXT NULL,
    ADD INDEX idx_bills_non_gst_agent (agent_id);
