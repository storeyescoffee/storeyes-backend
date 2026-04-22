package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByStoreIdOrderByName(Long storeId);

    @Query("SELECT a FROM Article a WHERE a.store.id = :storeId AND (:category IS NULL OR a.category = :category) AND (:search IS NULL OR :search = '' OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')) OR (a.category IS NOT NULL AND LOWER(a.category) LIKE LOWER(CONCAT('%', :search, '%')))) ORDER BY a.name")
    List<Article> findByStoreIdAndFilters(@Param("storeId") Long storeId, @Param("category") String category, @Param("search") String search);

    Optional<Article> findByIdAndStoreId(Long id, Long storeId);

    boolean existsByIdAndStoreId(Long id, Long storeId);

    /** Find article by exact name ignoring case for a given store. Used to link SalesProduct -> Article. */
    Optional<Article> findFirstByStoreIdAndNameIgnoreCase(Long storeId, String name);

    /**
     * Articles whose normalized name (trim + lower) is in {@code lowerNames}. Used to batch-resolve sales lines.
     */
    @Query("SELECT a FROM Article a WHERE a.store.id = :storeId AND LOWER(TRIM(a.name)) IN :lowerNames")
    List<Article> findByStoreIdAndNameLowerTrimmedIn(
            @Param("storeId") Long storeId,
            @Param("lowerNames") Collection<String> lowerNames);
}
