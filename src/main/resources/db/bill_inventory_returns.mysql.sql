-- Partial stock returns against an existing bill (GST / non-GST line ids).
-- Run once per environment; safe to re-run if tables already exist.

CREATE TABLE IF NOT EXISTS bill_inventory_returns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bill_kind VARCHAR(16) NOT NULL,
    bill_id BIGINT NOT NULL,
    location VARCHAR(50) NULL,
    notes TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id BIGINT NULL,
    KEY idx_bir_bill (bill_kind, bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bill_inventory_return_lines (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    return_id BIGINT NOT NULL,
    bill_item_id BIGINT NOT NULL,
    quantity_returned DECIMAL(14, 2) NOT NULL,
    CONSTRAINT fk_birl_return FOREIGN KEY (return_id) REFERENCES bill_inventory_returns (id) ON DELETE CASCADE,
    KEY idx_birl_return (return_id),
    KEY idx_birl_item (bill_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
