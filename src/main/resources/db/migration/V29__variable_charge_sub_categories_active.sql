-- Allow soft-deleting (activate/inactivate) Sub-Categories and Sub-Sub-Categories
ALTER TABLE variable_charge_sub_categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
