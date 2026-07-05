-- Store installation date: the real-world date the store's hardware/cameras were installed.
-- This is the anchor for the alerts activation countdown (installation_date + 3 weeks by
-- default), independent from created_at (which only reflects when the DB row was inserted
-- and can differ from the actual installation, e.g. bulk imports or delayed onboarding).
-- Existing stores are backfilled to created_at as the best available default.
--
-- Manage per store with e.g.:
--   UPDATE stores SET installation_date = '2026-06-20' WHERE code = 'STORE_CODE';         -- correct the real install date
--   UPDATE stores SET alerts_activation_date = installation_date + INTERVAL '21 days'
--     WHERE code = 'STORE_CODE';                                                          -- recompute activation from it
ALTER TABLE stores ADD COLUMN IF NOT EXISTS installation_date TIMESTAMP;

UPDATE stores SET installation_date = created_at
WHERE installation_date IS NULL;

ALTER TABLE stores ALTER COLUMN installation_date SET NOT NULL;
