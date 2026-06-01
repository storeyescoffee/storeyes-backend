package io.storeyes.storeyes_coffee.kpi.repositories;

import io.storeyes.storeyes_coffee.kpi.entities.DateDimension;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiProductDaily;
import io.storeyes.storeyes_coffee.store.entities.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FactKpiProductDailyRepository extends JpaRepository<FactKpiProductDaily, Long> {

    /**
     * Find all product KPIs for a store and date, ordered by quantity descending
     */
    List<FactKpiProductDaily> findByStoreAndDateOrderByQuantityDesc(Store store, DateDimension date);

    /**
     * Find all product KPIs for a store ID and date, ordered by revenue descending
     */
    List<FactKpiProductDaily> findByStoreIdAndDateOrderByRevenueDesc(Long storeId, DateDimension date);

    /**
     * Find all product KPIs for a store ID and date
     */
    List<FactKpiProductDaily> findByStoreIdAndDate(Long storeId, DateDimension date);

    /**
     * Aggregate product revenue and quantity over a date range, grouped by product name.
     * <p>
     * Returns {@code Object[]} rows where:
     * <ul>
     *   <li>[0] {@code String}  — productName</li>
     *   <li>[1] {@code Long}    — SUM(quantity)</li>
     *   <li>[2] {@code Double}  — SUM(revenue)</li>
     * </ul>
     */
    @Query("""
            SELECT p.productName, SUM(p.quantity), SUM(p.revenue)
            FROM FactKpiProductDaily p
            JOIN p.date d
            WHERE p.store.id = :storeId
              AND d.date >= :startDate
              AND d.date <= :endDate
            GROUP BY p.productName
            """)
    List<Object[]> aggregateByStoreIdAndDateRange(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

