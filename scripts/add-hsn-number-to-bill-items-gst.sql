-- Persist HSN per GST line from inventory (product.hsn_number) at bill time.
-- Run if column is missing (e.g. ddl-auto=none).

SET @db := DATABASE();
SET @exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'bill_items_gst' AND COLUMN_NAME = 'hsn_number'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE bill_items_gst ADD COLUMN hsn_number VARCHAR(20) NULL COMMENT ''from product inventory at billing'' AFTER item_total_price',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
