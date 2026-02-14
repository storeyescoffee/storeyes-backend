-- Document categories table (store-scoped)
CREATE TABLE IF NOT EXISTS document_categories (
    id BIGSERIAL PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_categories_store_id ON document_categories(store_id);

-- Add category to documents
ALTER TABLE documents ADD COLUMN IF NOT EXISTS category_id BIGINT NULL REFERENCES document_categories(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_documents_category_id ON documents(category_id);
