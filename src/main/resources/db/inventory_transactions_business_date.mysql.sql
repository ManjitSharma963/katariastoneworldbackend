-- Optional: Hibernate ddl-auto=update usually adds this from InventoryTransaction.businessDate.
ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS business_date DATE NULL AFTER location_id;
