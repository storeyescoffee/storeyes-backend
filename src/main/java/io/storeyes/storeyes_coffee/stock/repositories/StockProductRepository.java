package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockProductRepository extends JpaRepository<StockProduct, Long> {

    List<StockProduct> findByStoreIdOrderByNameAsc(Long storeId);

    @Query("SELECT DISTINCT p FROM StockProduct p JOIN FETCH p.subCategory WHERE p.store.id = :storeId ORDER BY p.name ASC")
    List<StockProduct> findByStoreIdWithSubCategoryOrderByNameAsc(@Param("storeId") Long storeId);

    /** Bar / kitchen / freezer / soda only — same filter as inventory summary (case-insensitive codes). */
    @Query("""
            SELECT DISTINCT p FROM StockProduct p
            JOIN FETCH p.subCategory sc
            WHERE p.store.id = :storeId
              AND LOWER(sc.code) IN ('bar', 'kitchen', 'freezer', 'soda')
            ORDER BY p.name ASC
            """)
    List<StockProduct> findRawMaterialProductsByStoreIdOrderByNameAsc(@Param("storeId") Long storeId);

    List<StockProduct> findByStoreIdAndSubCategoryIdOrderByNameAsc(Long storeId, Long subCategoryId);

    List<StockProduct> findByStoreIdAndNameContainingIgnoreCaseOrderByNameAsc(Long storeId, String search);

    List<StockProduct> findByStoreIdAndSubCategoryIdAndNameContainingIgnoreCaseOrderByNameAsc(
            Long storeId, Long subCategoryId, String search);

    List<StockProduct> findByStoreIdAndSubCategoryIdInOrderByNameAsc(Long storeId, List<Long> subCategoryIds);

    List<StockProduct> findByStoreIdAndSubCategoryIdInAndNameContainingIgnoreCaseOrderByNameAsc(
            Long storeId, List<Long> subCategoryIds, String search);

    @Query("SELECT p FROM StockProduct p WHERE p.store.id = :storeId AND ("
            + "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "(p.nameAr IS NOT NULL AND LOWER(p.nameAr) LIKE LOWER(CONCAT('%', :search, '%')))) "
            + "ORDER BY p.name ASC")
    List<StockProduct> findByStoreIdAndSearchText(@Param("storeId") Long storeId, @Param("search") String search);

    @Query("SELECT p FROM StockProduct p WHERE p.store.id = :storeId AND p.subCategory.id IN :subIds AND ("
            + "LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR "
            + "(p.nameAr IS NOT NULL AND LOWER(p.nameAr) LIKE LOWER(CONCAT('%', :search, '%')))) "
            + "ORDER BY p.name ASC")
    List<StockProduct> findByStoreIdAndSubCategoryIdInAndSearchText(
            @Param("storeId") Long storeId,
            @Param("subIds") List<Long> subCategoryIds,
            @Param("search") String search);
}
