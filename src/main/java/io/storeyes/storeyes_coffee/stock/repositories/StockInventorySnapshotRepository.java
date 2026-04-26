package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.StockInventorySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * Latest snapshot id per product for a store (PostgreSQL {@code DISTINCT ON}).
     * Avoids loading every historical snapshot row for inventory summary.
     */
    @Query(value = """
            SELECT DISTINCT ON (s.product_id) s.id
            FROM stock_inventory_snapshots s
            INNER JOIN stock_inventory_sessions ses ON s.session_id = ses.id
            WHERE ses.store_id = :storeId
            ORDER BY s.product_id, s.created_at DESC, s.id DESC
            """, nativeQuery = true)
    List<Long> findLatestSnapshotIdsByStoreId(@Param("storeId") Long storeId);

    @Query("""
            SELECT DISTINCT s FROM StockInventorySnapshot s
            JOIN FETCH s.product p
            JOIN FETCH p.subCategory
            WHERE s.id IN :ids
            """)
    List<StockInventorySnapshot> findByIdInWithProductAndSubCategory(@Param("ids") Collection<Long> ids);
}
