-- Add optional name column for fixed charges (used when category is 'OTHER')
ALTER TABLE fixed_charges ADD COLUMN name VARCHAR(255) NULL;
