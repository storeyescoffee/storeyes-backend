-- Per-store toggle for the multi-questions feedback feature.
-- Defaults to FALSE so existing stores keep current single-rating behavior.
-- Toggle per store with e.g.:
--   UPDATE stores SET multiple_questions_enabled = TRUE WHERE code = 'STORE_CODE';
ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS multiple_questions_enabled BOOLEAN NOT NULL DEFAULT FALSE;
