-- Inventory ledger + reservations (run once; Hibernate ddl-auto=update may also create these tables)
-- MySQL 8.0+

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    txn_type VARCHAR(32) NOT NULL,
    quantity DECIMAL(14,2) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    reference_id BIGINT NULL,
    reference_type VARCHAR(32) NULL,
    bill_kind VARCHAR(16) NULL,
    notes VARCHAR(255) NULL,
    location_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_inv_txn_product_created (product_id, created_at),
    KEY idx_inv_txn_ref (reference_type, reference_id, bill_kind),
    CONSTRAINT fk_inv_txn_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    reserved_quantity DECIMAL(14,2) NOT NULL,
    reference_id BIGINT NULL,
    bill_kind VARCHAR(16) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_inv_resv_product_status (product_id, status),
    KEY idx_inv_resv_bill (reference_id, bill_kind, status),
    CONSTRAINT fk_inv_resv_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Optional: low-stock threshold (default 10)
SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'products' AND COLUMN_NAME = 'min_stock'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE products ADD COLUMN min_stock DECIMAL(14,2) NOT NULL DEFAULT 10.00 AFTER total_sqft_stock',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Optional future: warehouse id (nullable, no FK until locations table exists)
SET @exists2 := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'products' AND COLUMN_NAME = 'location_id'
);
SET @sql2 := IF(@exists2 = 0,
  'ALTER TABLE products ADD COLUMN location_id BIGINT NULL AFTER location',
  'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET @exists3 := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'inventory_transactions' AND COLUMN_NAME = 'location_id'
);
-- location_id already in CREATE above

-- One-time backfill from legacy inventory_history (optional; comment out if not needed)
-- INSERT INTO inventory_transactions (product_id, txn_type, quantity, direction, reference_id, reference_type, bill_kind, notes, created_at)
-- SELECT product_id,
--   CASE action_type WHEN 'SALE' THEN 'SALE' WHEN 'ADD' THEN 'PURCHASE' ELSE 'ADJUSTMENT' END,
--   ABS(quantity_changed),
--   CASE WHEN quantity_changed >= 0 THEN 'IN' ELSE 'OUT' END,
--   reference_id, 'MANUAL', NULL, LEFT(notes, 255), created_at
-- FROM inventory_history;
