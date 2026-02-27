package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.StockMovement;
import io.storeyes.storeyes_coffee.stock.entities.StockMovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByStoreIdAndProductIdOrderByMovementDateDescIdDesc(Long storeId, Long productId);

    boolean existsByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    Optional<StockMovement> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    void deleteByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    /**
     * Inventory summary per product for a store: current quantity and total purchase amount/quantity for average cost.
     */
    @Query(value = """
        SELECT sm.product_id AS product_id,
               COALESCE(SUM(sm.quantity), 0) AS current_quantity,
               COALESCE(SUM(CASE WHEN sm.type = 'PURCHASE' THEN sm.amount ELSE NULL END), 0) AS total_purchase_amount,
               COALESCE(SUM(CASE WHEN sm.type = 'PURCHASE' THEN sm.quantity ELSE 0 END), 0) AS total_purchase_quantity
        FROM stock_movements sm
        WHERE sm.store_id = :storeId
        GROUP BY sm.product_id
        """, nativeQuery = true)
    List<Object[]> getInventorySummaryByStore(@Param("storeId") Long storeId);
}
