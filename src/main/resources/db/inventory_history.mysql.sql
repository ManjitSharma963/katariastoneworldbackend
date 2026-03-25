-- New table only — does not modify `products`.
-- Run against your production DB during a maintenance window after deploying the app.

CREATE TABLE IF NOT EXISTS inventory_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL COMMENT 'ADD, SALE, UPDATE, ADJUST',
    quantity_changed DECIMAL(12, 2) NOT NULL,
    previous_quantity DECIMAL(12, 2) NOT NULL,
    new_quantity DECIMAL(12, 2) NOT NULL,
    reference_id BIGINT NULL COMMENT 'e.g. bill id when applicable',
    notes VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inventory_history_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE,
    KEY idx_inventory_history_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
