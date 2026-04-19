-- Draft supplier orders (WhatsApp-style messages + lines) convertible to variable charges later
CREATE TABLE supplier_orders (
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

CREATE INDEX idx_supplier_orders_store_status ON supplier_orders(store_id, status);
CREATE INDEX idx_supplier_orders_store_created ON supplier_orders(store_id, created_at DESC);

CREATE TABLE supplier_order_lines (
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

CREATE INDEX idx_supplier_order_lines_order_id ON supplier_order_lines(order_id);
