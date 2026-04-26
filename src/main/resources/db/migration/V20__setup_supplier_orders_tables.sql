-- Ensure supplier order tables exist and status constraint is correct.
-- This migration is intended to recover environments where previous
-- supplier-order migrations were missing or applied on another database.

-- 1) Create main table if missing.
CREATE TABLE IF NOT EXISTS supplier_orders (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    supplier_id BIGINT REFERENCES suppliers(id) ON DELETE SET NULL,
    supplier_name_snapshot VARCHAR(255),
    message_text TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    order_date DATE NOT NULL,
    converted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2) Create lines table if missing.
CREATE TABLE IF NOT EXISTS supplier_order_lines (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES supplier_orders(id) ON DELETE CASCADE,
    stock_product_id BIGINT NOT NULL REFERENCES stock_products(id) ON DELETE RESTRICT,
    quantity NUMERIC(12, 2) NOT NULL,
    unit_price_snapshot NUMERIC(12, 2) NOT NULL,
    line_amount NUMERIC(12, 2) NOT NULL,
    sort_index INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3) Ensure indexes exist.
CREATE INDEX IF NOT EXISTS idx_supplier_orders_store_status
    ON supplier_orders(store_id, status);
CREATE INDEX IF NOT EXISTS idx_supplier_orders_store_created
    ON supplier_orders(store_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_supplier_order_lines_order_id
    ON supplier_order_lines(order_id);

-- 4) Normalize legacy statuses if present.
UPDATE supplier_orders
SET status = CASE status
    WHEN 'DRAFT' THEN 'PENDING'
    WHEN 'SENT' THEN 'VALID'
    WHEN 'CONVERTED' THEN 'VALID'
    ELSE status
END
WHERE status IN ('DRAFT', 'SENT', 'CONVERTED');

-- 5) Repair status check constraint.
ALTER TABLE supplier_orders
    DROP CONSTRAINT IF EXISTS supplier_orders_status_check;

ALTER TABLE supplier_orders
    ADD CONSTRAINT supplier_orders_status_check
    CHECK (status IN ('PENDING', 'VALID', 'REJECTED'));
