-- SQL Script to Add Location Column to Database Tables
-- Run this script on your MySQL database: katariastoneworld

-- 1. Add location column to employees table
ALTER TABLE employees 
ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER joining_date;

-- 2. Add location column to expenses table
ALTER TABLE expenses 
ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER settled;

-- 3. Add location column to products table
ALTER TABLE products 
ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER meta_keywords;

-- 4. Add location column to customers table (optional/nullable)
ALTER TABLE customers 
ADD COLUMN location VARCHAR(50) NULL AFTER email;

-- 5. Update existing records with default location (if needed)
-- Update employees
UPDATE employees SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- Update expenses
UPDATE expenses SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- Update products
UPDATE products SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- Update customers (optional - can be NULL)
UPDATE customers SET location = 'Bhondsi' WHERE location IS NULL OR location = '';

-- 6. Add index on location for better query performance (optional)
CREATE INDEX idx_employees_location ON employees(location);
CREATE INDEX idx_expenses_location ON expenses(location);
CREATE INDEX idx_products_location ON products(location);
CREATE INDEX idx_customers_location ON customers(location);

-- 7. Verify the changes
SELECT 'employees' as table_name, COUNT(*) as total_records, 
       COUNT(DISTINCT location) as distinct_locations,
       GROUP_CONCAT(DISTINCT location) as locations
FROM employees
UNION ALL
SELECT 'expenses', COUNT(*), COUNT(DISTINCT location), GROUP_CONCAT(DISTINCT location)
FROM expenses
UNION ALL
SELECT 'products', COUNT(*), COUNT(DISTINCT location), GROUP_CONCAT(DISTINCT location)
FROM products
UNION ALL
SELECT 'customers', COUNT(*), COUNT(DISTINCT location), GROUP_CONCAT(DISTINCT location)
FROM customers;

