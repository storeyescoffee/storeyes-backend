-- Add optional fixed alert date to demo store mappings.
-- When set, alert queries for the demo store target this date instead of the
-- caller-supplied date; returned alert dates are then rewritten to the requested date.
ALTER TABLE demo_store_mappings
    ADD COLUMN IF NOT EXISTS alert_date DATE;
