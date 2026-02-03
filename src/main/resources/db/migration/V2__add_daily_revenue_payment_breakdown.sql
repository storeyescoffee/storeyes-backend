-- =====================================================
-- Daily Revenue Payment Breakdown (TPE / Espèce)
-- =====================================================
-- Stores owner-entered TPE (card payment) per store/day.
-- Espèce (cash) = Total TTC - TPE (computed)
-- =====================================================

CREATE SEQUENCE IF NOT EXISTS daily_revenue_payment_breakdown_id_seq START
WITH
    1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS daily_revenue_payment_breakdown (
    id BIGINT PRIMARY KEY DEFAULT nextval (
        'daily_revenue_payment_breakdown_id_seq'
    ),
    store_id BIGINT NOT NULL,
    date_id BIGINT NOT NULL,
    tpe DOUBLE PRECISION NOT NULL DEFAULT 0,
    CONSTRAINT fk_daily_revenue_breakdown_store FOREIGN KEY (store_id) REFERENCES stores (id) ON DELETE CASCADE,
    CONSTRAINT fk_daily_revenue_breakdown_date FOREIGN KEY (date_id) REFERENCES date_dimensions (id) ON DELETE CASCADE,
    CONSTRAINT uk_daily_revenue_breakdown_store_date UNIQUE (store_id, date_id)
);

CREATE INDEX IF NOT EXISTS idx_daily_revenue_breakdown_store ON daily_revenue_payment_breakdown (store_id);

CREATE INDEX IF NOT EXISTS idx_daily_revenue_breakdown_date ON daily_revenue_payment_breakdown (date_id);

CREATE INDEX IF NOT EXISTS idx_daily_revenue_breakdown_store_date ON daily_revenue_payment_breakdown (store_id, date_id);

ALTER SEQUENCE daily_revenue_payment_breakdown_id_seq OWNED BY daily_revenue_payment_breakdown.id;

COMMENT ON
TABLE daily_revenue_payment_breakdown IS 'Owner-entered TPE (card payment) per store/day. Espèce = TTC - TPE';