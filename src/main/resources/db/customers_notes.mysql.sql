-- Add optional customer notes field for discussion/context.
ALTER TABLE customers ADD COLUMN notes TEXT NULL;
