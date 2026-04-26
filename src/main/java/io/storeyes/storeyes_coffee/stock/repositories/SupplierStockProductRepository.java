package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.SupplierStockProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SupplierStockProductRepository extends JpaRepository<SupplierStockProduct, Long> {

    List<SupplierStockProduct> findBySupplier_IdOrderByStockProduct_NameAsc(Long supplierId);

    void deleteBySupplier_Id(Long supplierId);

    void deleteByStockProduct_Id(Long stockProductId);

    long countBySupplier_Id(Long supplierId);

    @Query("""
            SELECT l FROM SupplierStockProduct l
            JOIN FETCH l.supplier s
            JOIN FETCH l.stockProduct p
            WHERE p.id = :productId AND s.store.id = :storeId AND s.isActive = true
            ORDER BY s.name ASC
            """)
    List<SupplierStockProduct> findByStoreIdAndStockProductId(
            @Param("storeId") Long storeId,
            @Param("productId") Long productId);

    @Modifying
    @Query("UPDATE SupplierStockProduct l SET l.isPreferred = false WHERE l.stockProduct.id = :productId")
    void clearPreferredForStockProduct(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE SupplierStockProduct l SET l.isPreferred = true WHERE l.stockProduct.id = :productId AND l.supplier.id = :supplierId")
    void markPreferredForSupplierAndProduct(
            @Param("productId") Long productId,
            @Param("supplierId") Long supplierId);

    @Query("""
            SELECT l.supplier.id, COUNT(l.id) FROM SupplierStockProduct l
            WHERE l.supplier.id IN :supplierIds
            GROUP BY l.supplier.id
            """)
    List<Object[]> countLinksBySupplierIdIn(@Param("supplierIds") List<Long> supplierIds);

    @Query("""
            SELECT l FROM SupplierStockProduct l
            JOIN FETCH l.supplier s
            JOIN FETCH l.stockProduct p
            WHERE p.id IN :productIds AND s.store.id = :storeId AND s.isActive = true
            ORDER BY p.id ASC, s.name ASC
            """)
    List<SupplierStockProduct> findByStoreIdAndStockProduct_IdInWithSupplier(
            @Param("storeId") Long storeId,
            @Param("productIds") Collection<Long> productIds);
}
