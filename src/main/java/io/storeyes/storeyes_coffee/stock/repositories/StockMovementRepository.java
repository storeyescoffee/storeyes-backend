package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.StockMovement;
import io.storeyes.storeyes_coffee.stock.entities.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByStoreIdAndProductIdOrderByMovementDateDescIdDesc(Long storeId, Long productId);

    boolean existsByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    Optional<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    void deleteByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    /**
     * Inventory summary per product: quantity and total value from movement amounts.
     * Value = sum of amounts: PURCHASE and ADJUSTMENT add, CONSUMPTION subtracts.
     */
    @Query(value = """
        SELECT sm.product_id AS product_id,
               COALESCE(SUM(sm.quantity), 0) AS current_quantity,
               COALESCE(SUM(
                 CASE WHEN sm.type = 'PURCHASE' OR sm.type = 'ADJUSTMENT' THEN COALESCE(sm.amount, 0)
                      WHEN sm.type = 'CONSUMPTION' THEN -COALESCE(sm.amount, 0)
                      ELSE 0 END
               ), 0) AS total_value
        FROM stock_movements sm
        WHERE sm.store_id = :storeId
        GROUP BY sm.product_id
        """, nativeQuery = true)
    List<Object[]> getInventorySummaryByStore(@Param("storeId") Long storeId);

    /** Sum of movement quantities for a product after a given date (for real stock = snapshot + movements after). */
    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m WHERE m.store.id = :storeId AND m.product.id = :productId AND m.movementDate > :afterDate")
    BigDecimal sumQuantityAfterDate(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("afterDate") java.time.LocalDate afterDate);
}
