-- Alerts activation date: alerts stay locked until this date (default = created_at + 3 weeks).
-- Existing stores are backfilled to created_at + 21 days, which is already in the past for
-- stores older than 3 weeks, so they stay active.
-- Manage per store with e.g.:
--   UPDATE stores SET alerts_activation_date = NOW() WHERE code = 'STORE_CODE';       -- activate now
--   UPDATE stores SET alerts_activation_date = '2026-07-20' WHERE id = 5;             -- custom date
--   UPDATE stores SET alerts_activation_date = created_at + INTERVAL '21 days' WHERE id = 5; -- default rule
ALTER TABLE stores ADD COLUMN IF NOT EXISTS alerts_activation_date TIMESTAMP;

UPDATE stores SET alerts_activation_date = created_at + INTERVAL '21 days'
WHERE alerts_activation_date IS NULL;

ALTER TABLE stores ALTER COLUMN alerts_activation_date SET NOT NULL;
