-- Drop owner_id column from stores table.
-- Store ownership is now determined solely via role_mappings.
ALTER TABLE stores DROP COLUMN IF EXISTS owner_id;
