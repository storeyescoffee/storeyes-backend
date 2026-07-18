-- Per-store toggle to restrict the backoffice to the Feedbacks page only.
-- Defaults to FALSE so existing stores keep full access to the backoffice.
-- Toggle per store manually in pgAdmin with e.g.:
--   UPDATE stores SET feedback_only_mode = TRUE WHERE code = 'STORE_CODE';
--   UPDATE stores SET feedback_only_mode = FALSE WHERE code = 'STORE_CODE';
ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS feedback_only_mode BOOLEAN NOT NULL DEFAULT FALSE;
