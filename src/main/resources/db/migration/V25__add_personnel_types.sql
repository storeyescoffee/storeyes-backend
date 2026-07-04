-- Create personnel_types table for dynamic employee type management
CREATE SEQUENCE IF NOT EXISTS personnel_type_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS personnel_types (
    id          BIGINT       NOT NULL DEFAULT nextval('personnel_type_id_seq') PRIMARY KEY,
    store_id    BIGINT       NOT NULL REFERENCES stores(id),
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_personnel_type_store_name UNIQUE (store_id, name)
);

CREATE INDEX IF NOT EXISTS idx_personnel_types_store ON personnel_types(store_id);
CREATE INDEX IF NOT EXISTS idx_personnel_types_store_active ON personnel_types(store_id, is_active);

-- Extend type column on employees to support custom type names (100 chars)
ALTER TABLE employees ALTER COLUMN type TYPE VARCHAR(100);

-- Extend type column on personnel_employees to support custom type names (100 chars)
ALTER TABLE personnel_employees ALTER COLUMN type TYPE VARCHAR(100);

-- Normalize existing enum values to display names in employees
UPDATE employees SET type = 'Server'  WHERE type = 'SERVER';
UPDATE employees SET type = 'Barman'  WHERE type = 'BARMAN';
UPDATE employees SET type = 'Cleaner' WHERE type = 'CLEANER';

-- Normalize existing enum values to display names in personnel_employees
UPDATE personnel_employees SET type = 'Server'  WHERE type = 'SERVER';
UPDATE personnel_employees SET type = 'Barman'  WHERE type = 'BARMAN';
UPDATE personnel_employees SET type = 'Cleaner' WHERE type = 'CLEANER';

-- Seed default personnel types for all existing stores
INSERT INTO personnel_types (store_id, name, is_active)
SELECT id, 'Server',  true FROM stores
ON CONFLICT (store_id, name) DO NOTHING;

INSERT INTO personnel_types (store_id, name, is_active)
SELECT id, 'Barman',  true FROM stores
ON CONFLICT (store_id, name) DO NOTHING;

INSERT INTO personnel_types (store_id, name, is_active)
SELECT id, 'Cleaner', true FROM stores
ON CONFLICT (store_id, name) DO NOTHING;
