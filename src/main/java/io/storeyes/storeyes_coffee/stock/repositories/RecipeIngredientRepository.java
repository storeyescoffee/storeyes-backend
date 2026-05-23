package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {

    @Query("""
            SELECT DISTINCT r FROM RecipeIngredient r
            JOIN FETCH r.article
            LEFT JOIN FETCH r.product p
            LEFT JOIN FETCH r.ingredientArticle ia
            LEFT JOIN FETCH ia.store
            WHERE r.article.id = :articleId
            ORDER BY COALESCE(p.name, ia.name)
            """)
    List<RecipeIngredient> findByArticleIdOrderByProductName(@Param("articleId") Long articleId);

    Optional<RecipeIngredient> findByIdAndArticleId(Long id, Long articleId);

    Optional<RecipeIngredient> findByArticleIdAndProductId(Long articleId, Long productId);

    boolean existsByArticleIdAndProductId(Long articleId, Long productId);

    boolean existsByArticleIdAndIngredientArticle_Id(Long articleId, Long ingredientArticleId);

    long countByIngredientArticle_Id(Long ingredientArticleId);

    void deleteByArticleIdAndProductId(Long articleId, Long productId);

    void deleteByProduct_Id(Long productId);

    @Query("SELECT ri.ingredientArticle.id FROM RecipeIngredient ri WHERE ri.article.id = :articleId AND ri.ingredientArticle IS NOT NULL")
    List<Long> findNestedIngredientArticleIdsByArticleId(@Param("articleId") Long articleId);
}
