package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.StockInventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockInventorySnapshotRepository extends JpaRepository<StockInventorySnapshot, Long> {

    List<StockInventorySnapshot> findBySessionId(Long sessionId);

    /** Latest snapshot per product for a store (from sessions of that store). */
    @Query(value = """
        SELECT s.* FROM stock_inventory_snapshots s
        JOIN stock_inventory_sessions ses ON s.session_id = ses.id
        WHERE s.product_id = :productId AND ses.store_id = :storeId
        ORDER BY s.created_at DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<StockInventorySnapshot> findLatestByProductIdAndStoreId(
        @Param("productId") Long productId,
        @Param("storeId") Long storeId
    );

    /** All snapshots for store, newest first (service will take latest per product). */
    @Query("SELECT DISTINCT s FROM StockInventorySnapshot s JOIN FETCH s.product p JOIN FETCH p.subCategory JOIN s.session ses WHERE ses.store.id = :storeId ORDER BY s.createdAt DESC")
    List<StockInventorySnapshot> findBySessionStoreIdOrderByCreatedAtDesc(@Param("storeId") Long storeId);
}
