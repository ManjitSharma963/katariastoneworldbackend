-- Migration script to add new fields to products table
-- Database: MySQL
-- Table: products

USE katariastoneworld;

-- Add labour_charges column
ALTER TABLE products 
ADD COLUMN labour_charges DECIMAL(10, 2) NULL 
COMMENT 'Labour charges for the product';

-- Add rto_fees column
ALTER TABLE products 
ADD COLUMN rto_fees DECIMAL(10, 2) NULL 
COMMENT 'RTO fees for the product';

-- Add damage_expenses column
ALTER TABLE products 
ADD COLUMN damage_expenses DECIMAL(10, 2) NULL 
COMMENT 'Damage expenses for the product';

-- Add others_expenses column
ALTER TABLE products 
ADD COLUMN others_expenses DECIMAL(10, 2) NULL 
COMMENT 'Other expenses for the product';

-- Add price_per_sqft_after column
ALTER TABLE products 
ADD COLUMN price_per_sqft_after DECIMAL(10, 2) NULL 
COMMENT 'Price per sqft after all expenses';

-- Verify the changes
DESCRIBE products;

