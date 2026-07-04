-- Per-store visibility of alert types (NOT_TAPPED / RETURN).
-- Both default to TRUE so existing stores keep seeing both tabs.
-- Toggle per store with e.g.:
--   UPDATE stores SET return_alerts_enabled = FALSE WHERE code = 'STORE_CODE';
ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS not_tapped_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS return_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE;
