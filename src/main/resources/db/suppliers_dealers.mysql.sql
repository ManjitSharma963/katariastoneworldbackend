-- Supplier (firm) and Dealer (middleman) + optional links on products.
-- Run on MySQL after backup. Safe to run once; if columns exist, skip ALTERs.

CREATE TABLE IF NOT EXISTS suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    contact_number VARCHAR(50) NULL,
    address VARCHAR(500) NULL,
    location VARCHAR(50) NOT NULL COMMENT 'Branch scope — matches JWT / products.location',
    created_at DATETIME(6) NOT NULL,
    INDEX idx_suppliers_location (location),
    INDEX idx_suppliers_name (name)
);

CREATE TABLE IF NOT EXISTS dealers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    contact_number VARCHAR(50) NULL,
    address VARCHAR(500) NULL,
    location VARCHAR(50) NOT NULL COMMENT 'Branch scope — matches JWT / products.location',
    created_at DATETIME(6) NOT NULL,
    INDEX idx_dealers_location (location),
    INDEX idx_dealers_name (name)
);

-- If these fail with "Duplicate column", the migration was already applied.
ALTER TABLE products ADD COLUMN supplier_id BIGINT NULL;
ALTER TABLE products ADD COLUMN dealer_id BIGINT NULL;

-- Optional: enforce referential integrity (comment out if your DB policies disallow)
-- ALTER TABLE products
--     ADD CONSTRAINT fk_products_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
--     ADD CONSTRAINT fk_products_dealer FOREIGN KEY (dealer_id) REFERENCES dealers (id);
