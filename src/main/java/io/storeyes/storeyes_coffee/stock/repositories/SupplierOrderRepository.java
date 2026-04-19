package io.storeyes.storeyes_coffee.stock.repositories;

import io.storeyes.storeyes_coffee.stock.entities.SupplierOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierOrderRepository extends JpaRepository<SupplierOrder, Long> {

    @Query("""
            SELECT DISTINCT o FROM SupplierOrder o
            LEFT JOIN FETCH o.supplier
            LEFT JOIN FETCH o.lines l
            LEFT JOIN FETCH l.stockProduct
            WHERE o.id = :id AND o.store.id = :storeId
            """)
    Optional<SupplierOrder> findFetchedByIdAndStoreId(@Param("id") Long id, @Param("storeId") Long storeId);

    Optional<SupplierOrder> findByIdAndStore_Id(Long id, Long storeId);

    @Query(
            value = """
                    SELECT o.id, o.status, o.order_date, o.supplier_id,
                           COALESCE(s.name, o.supplier_name_snapshot),
                           (SELECT COUNT(*) FROM supplier_order_lines l WHERE l.order_id = o.id),
                           COALESCE(
                               (SELECT SUM(l2.line_amount) FROM supplier_order_lines l2 WHERE l2.order_id = o.id),
                               0
                           ),
                           o.created_at, o.updated_at
                    FROM supplier_orders o
                    LEFT JOIN suppliers s ON s.id = o.supplier_id
                    WHERE o.store_id = :storeId
                    ORDER BY o.created_at DESC
                    """,
            nativeQuery = true
    )
    List<Object[]> listSummaryRows(@Param("storeId") Long storeId);
}
