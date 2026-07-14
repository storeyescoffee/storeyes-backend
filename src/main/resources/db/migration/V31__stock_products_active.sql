-- Allow soft-deleting (activate/deactivate) stock products instead of hard-deleting them,
-- so history (supplier orders, recipes, movements, charges) that reference a product survives.
ALTER TABLE stock_products ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
