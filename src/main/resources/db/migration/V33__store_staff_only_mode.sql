-- Per-store toggle to restrict the backoffice to the Staff/Timekeeping section only
-- (attendance, anomalies, planning, employees, reports, settings).
-- Defaults to FALSE so existing stores keep full access to the backoffice.
-- Intended to be mutually exclusive with feedback_only_mode (V32); if both are set,
-- feedback_only_mode takes priority in the backoffice.
-- Toggle per store manually in pgAdmin with e.g.:
--   UPDATE stores SET staff_only_mode = TRUE WHERE code = 'STORE_CODE';
--   UPDATE stores SET staff_only_mode = FALSE WHERE code = 'STORE_CODE';
ALTER TABLE stores
    ADD COLUMN IF NOT EXISTS staff_only_mode BOOLEAN NOT NULL DEFAULT FALSE;
