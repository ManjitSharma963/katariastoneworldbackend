# Database Migration Guide - Adding Location Column

This guide explains how to update your database tables to add the `location` column required for location-based data isolation.

## Method 1: Automatic (Recommended)

Since your `application.properties` has `spring.jpa.hibernate.ddl-auto=update`, Hibernate will automatically add the columns when you restart the application.

**Steps:**
1. Stop your Spring Boot application
2. Restart the application
3. Hibernate will automatically:
   - Add `location` column to `employees`, `expenses`, `products`, and `customers` tables
   - Set default values if specified in entity annotations

**Note:** If you have existing data, you'll need to update it manually (see Method 2).

## Method 2: Manual SQL Script

If you prefer to control the migration manually or need to update existing data:

### Step 1: Add Location Columns

Run the SQL script: `database_migration_add_location.sql`

```bash
mysql -u root -p katariastoneworld < database_migration_add_location.sql
```

Or execute it in MySQL Workbench/phpMyAdmin.

### Step 2: Update Existing Records

Run the SQL script: `database_migration_update_existing_data.sql`

This will:
- Set default location for existing records
- You can customize the logic based on your business requirements

### Step 3: Verify Changes

Check that all tables have the location column:

```sql
DESCRIBE employees;
DESCRIBE expenses;
DESCRIBE products;
DESCRIBE customers;
```

## Method 3: Using MySQL Workbench or phpMyAdmin

1. **Connect to your database** (`katariastoneworld`)

2. **For each table, run:**

   ```sql
   -- Employees
   ALTER TABLE employees 
   ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER joining_date;
   
   -- Expenses
   ALTER TABLE expenses 
   ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER settled;
   
   -- Products
   ALTER TABLE products 
   ADD COLUMN location VARCHAR(50) NOT NULL DEFAULT 'Bhondsi' AFTER meta_keywords;
   
   -- Customers (optional/nullable)
   ALTER TABLE customers 
   ADD COLUMN location VARCHAR(50) NULL AFTER email;
   ```

3. **Update existing records:**

   ```sql
   UPDATE employees SET location = 'Bhondsi' WHERE location IS NULL;
   UPDATE expenses SET location = 'Bhondsi' WHERE location IS NULL;
   UPDATE products SET location = 'Bhondsi' WHERE location IS NULL;
   UPDATE customers SET location = 'Bhondsi' WHERE location IS NULL;
   ```

## Important Notes

1. **Default Location**: The scripts set default location as `'Bhondsi'`. Change this if needed.

2. **Existing Data**: If you have existing records, decide which location they should belong to:
   - All existing records → Set to one location (e.g., 'Bhondsi')
   - Based on business logic → Update with appropriate location values

3. **Customers Table**: Location is optional (nullable) for customers, as it's set when creating bills.

4. **Validation**: After migration, verify:
   - All required tables have the `location` column
   - Existing records have location values
   - New records automatically get location from JWT token

## Testing After Migration

1. **Restart your Spring Boot application**
2. **Test creating a new record** (employee, expense, product) - it should automatically get location from JWT token
3. **Test GET endpoints** - should only return records for the authenticated user's location
4. **Test PUT endpoints** - should only allow updating records from the same location

## Rollback (if needed)

If you need to remove the location column:

```sql
ALTER TABLE employees DROP COLUMN location;
ALTER TABLE expenses DROP COLUMN location;
ALTER TABLE products DROP COLUMN location;
ALTER TABLE customers DROP COLUMN location;
```

**Warning:** This will remove location-based filtering. Only do this if you're sure you want to remove this feature.

