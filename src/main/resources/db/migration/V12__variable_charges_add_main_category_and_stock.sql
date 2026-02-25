-- Add new columns for variable charge category hierarchy and stock linkage
ALTER TABLE variable_charges
ADD COLUMN main_category_id BIGINT NULL REFERENCES variable_charge_main_categories (id) ON DELETE RESTRICT;

ALTER TABLE variable_charges
ADD COLUMN sub_category_id BIGINT NULL REFERENCES variable_charge_sub_categories (id) ON DELETE SET NULL;

ALTER TABLE variable_charges
ADD COLUMN product_id BIGINT NULL REFERENCES stock_products (id) ON DELETE SET NULL;

ALTER TABLE variable_charges ADD COLUMN quantity DECIMAL(12, 2) NULL;

ALTER TABLE variable_charges
ADD COLUMN unit_price DECIMAL(12, 2) NULL;

CREATE INDEX idx_variable_charges_main_category_id ON variable_charges (main_category_id);

CREATE INDEX idx_variable_charges_sub_category_id ON variable_charges (sub_category_id);

CREATE INDEX idx_variable_charges_product_id ON variable_charges (product_id);

-- Backfill existing rows: set main_category_id to "Achat exceptionnel" for each store
UPDATE variable_charges vc
SET
    main_category_id = mc.id
FROM
    variable_charge_main_categories mc
WHERE
    mc.store_id = vc.store_id
    AND mc.code = 'achat_exceptionnel';

-- Now require main_category_id for all rows
ALTER TABLE variable_charges
ALTER COLUMN main_category_id
SET
    NOT NULL;