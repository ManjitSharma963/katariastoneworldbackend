CREATE TABLE IF NOT EXISTS product_change_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    previous_snapshot LONGTEXT NULL,
    new_snapshot LONGTEXT NULL,
    notes VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_change_history_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE,
    KEY idx_product_change_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
