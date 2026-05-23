-- Allow marking articles that may appear as sub-recipe ingredients in other articles' recipes.
ALTER TABLE articles
    ADD COLUMN IF NOT EXISTS allow_as_sub_recipe_article BOOLEAN NOT NULL DEFAULT FALSE;

-- Recipe lines: either a stock product OR another article (sub-recipe), never both.
ALTER TABLE recipe_ingredients
    DROP CONSTRAINT IF EXISTS recipe_ingredients_article_id_product_id_key;

ALTER TABLE recipe_ingredients
    ALTER COLUMN product_id DROP NOT NULL;

ALTER TABLE recipe_ingredients
    ADD COLUMN IF NOT EXISTS ingredient_article_id BIGINT NULL REFERENCES articles(id) ON DELETE RESTRICT;

ALTER TABLE recipe_ingredients
    ADD CONSTRAINT chk_recipe_ingredients_target_xor CHECK (
        (product_id IS NOT NULL AND ingredient_article_id IS NULL)
        OR (product_id IS NULL AND ingredient_article_id IS NOT NULL)
    );

CREATE UNIQUE INDEX IF NOT EXISTS ux_recipe_ingredients_article_product
    ON recipe_ingredients (article_id, product_id)
    WHERE product_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_recipe_ingredients_article_ingredient_article
    ON recipe_ingredients (article_id, ingredient_article_id)
    WHERE ingredient_article_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_ingredient_article
    ON recipe_ingredients (ingredient_article_id);
