-- SQL Script to Update Existing Records with Location Based on Your Business Logic
-- Run this after adding the location column

-- Option 1: Set all existing records to a default location (e.g., 'Bhondsi')
UPDATE employees SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
UPDATE expenses SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
UPDATE products SET location = 'Bhondsi' WHERE location IS NULL OR location = '';
UPDATE customers SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- Option 2: If you have a way to determine location from existing data
-- For example, if you have a created_by or branch field, you can use that
-- UPDATE employees SET location = CASE 
--     WHEN branch = 'Bhondsi' THEN 'Bhondsi'
--     WHEN branch = 'Tapugada' THEN 'Tapugada'
--     ELSE 'Bhondsi'
-- END;

-- Option 3: Update based on date or other criteria
-- UPDATE employees SET location = 'Bhondsi' WHERE created_at < '2025-01-01';
-- UPDATE employees SET location = 'Tapugada' WHERE created_at >= '2025-01-01';

-- Verify updates
SELECT 'employees' as table_name, location, COUNT(*) as count
FROM employees
GROUP BY location
UNION ALL
SELECT 'expenses', location, COUNT(*)
FROM expenses
GROUP BY location
UNION ALL
SELECT 'products', location, COUNT(*)
FROM products
GROUP BY location
UNION ALL
SELECT 'customers', COALESCE(location, 'NULL'), COUNT(*)
FROM customers
GROUP BY location;

