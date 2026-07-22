-- Allow soft-deleting (activate/deactivate) main categories instead of hard-deleting them,
-- so history (variable charges) that reference a main category survives.
ALTER TABLE variable_charge_main_categories ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
