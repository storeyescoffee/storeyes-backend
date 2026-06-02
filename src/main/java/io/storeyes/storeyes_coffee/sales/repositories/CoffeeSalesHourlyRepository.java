package io.storeyes.storeyes_coffee.sales.repositories;

import io.storeyes.storeyes_coffee.sales.entities.CoffeeSalesHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CoffeeSalesHourlyRepository extends JpaRepository<CoffeeSalesHourly, Long> {

    /**
     * Aggregate product revenue and quantity over a date range for a given store code.
     * Only products with non-zero total revenue are returned.
     * <p>
     * Returns {@code Object[]} rows where:
     * <ul>
     *   <li>[0] {@code String} — coffeeName</li>
     *   <li>[1] {@code BigDecimal} — SUM(quantity)</li>
     *   <li>[2] {@code BigDecimal} — SUM(totalPrice)</li>
     * </ul>
     */
    @Query("""
            SELECT c.coffeeName, SUM(c.quantity), SUM(c.totalPrice)
            FROM CoffeeSalesHourly c
            WHERE c.coffeeShopName = :storeCode
              AND c.saleDate >= :startDate
              AND c.saleDate <= :endDate
            GROUP BY c.coffeeName
            HAVING SUM(c.totalPrice) > 0
            """)
    List<Object[]> aggregateByStoreCodeAndDateRange(
            @Param("storeCode") String storeCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
