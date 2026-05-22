-- =============================================================================
-- Fix products not showing in the app after bulk SQL INSERT
-- =============================================================================
-- The Products tab only loads rows where products.location matches the
-- logged-in user's branch (from JWT), e.g. 'Bhondsi' or 'Tapugada'.
--
-- Run in MySQL Workbench against katariadb, then refresh the app (or re-login).
-- Replace 'Bhondsi' below with YOUR user's location from:
--   SELECT id, email, location FROM users;
-- =============================================================================

USE katariadb;

-- 1) Diagnose: how many products per location?
SELECT location, COUNT(*) AS cnt
FROM products
GROUP BY location
ORDER BY cnt DESC;

-- 2) Your login branch (use this value in step 3)
SELECT id, email, location, role FROM users;

-- 3) Assign branch to rows missing location (EDIT 'Bhondsi' if needed)
UPDATE products
SET location = 'Bhondsi'
WHERE location IS NULL OR TRIM(location) = '';

-- 4) Trim stray spaces on location
UPDATE products
SET location = TRIM(location)
WHERE location IS NOT NULL AND location <> TRIM(location);

-- 5) Fill required defaults often missing on raw INSERT
UPDATE products
SET primary_image_url = COALESCE(NULLIF(TRIM(primary_image_url), ''), '/placeholder.png')
WHERE primary_image_url IS NULL OR TRIM(primary_image_url) = '';

UPDATE products
SET is_featured = COALESCE(is_featured, 0),
    is_active = COALESCE(is_active, 1)
WHERE is_featured IS NULL OR is_active IS NULL;

UPDATE products
SET created_at = COALESCE(created_at, NOW()),
    updated_at = COALESCE(updated_at, NOW())
WHERE created_at IS NULL OR updated_at IS NULL;

UPDATE products
SET unit = COALESCE(NULLIF(TRIM(unit), ''), 'sqft')
WHERE unit IS NULL OR TRIM(unit) = '';

-- 6) Clear supplier/dealer FKs that point to deleted rows (optional buy / add later)
UPDATE products p
LEFT JOIN suppliers s ON s.id = p.supplier_id
SET p.supplier_id = NULL
WHERE p.supplier_id IS NOT NULL AND s.id IS NULL;

UPDATE products p
LEFT JOIN dealers d ON d.id = p.dealer_id
SET p.dealer_id = NULL
WHERE p.dealer_id IS NOT NULL AND d.id IS NULL;

-- 7) Verify count for your branch
SELECT COUNT(*) AS products_for_bhondsi
FROM products
WHERE TRIM(LOWER(location)) = TRIM(LOWER('Bhondsi'));
