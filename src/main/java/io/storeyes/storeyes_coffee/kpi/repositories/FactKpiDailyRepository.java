package io.storeyes.storeyes_coffee.kpi.repositories;

import io.storeyes.storeyes_coffee.kpi.entities.DateDimension;
import io.storeyes.storeyes_coffee.kpi.entities.FactKpiDaily;
import io.storeyes.storeyes_coffee.store.entities.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FactKpiDailyRepository extends JpaRepository<FactKpiDaily, Long> {
    
    /**
     * Find daily KPI by store and date (single query; {@code date} and {@code store} eagerly loaded).
     */
    @Query("SELECT DISTINCT f FROM FactKpiDaily f JOIN FETCH f.date JOIN FETCH f.store WHERE f.store = :store AND f.date = :date")
    Optional<FactKpiDaily> findByStoreAndDate(@Param("store") Store store, @Param("date") DateDimension date);

    /**
     * Find daily KPI by store ID and date (single query; associations eagerly loaded — avoids duplicate joins from EntityGraph + JOIN).
     */
    @Query("SELECT DISTINCT f FROM FactKpiDaily f JOIN FETCH f.date JOIN FETCH f.store s WHERE s.id = :storeId AND f.date = :date")
    Optional<FactKpiDaily> findByStoreIdAndDate(@Param("storeId") Long storeId, @Param("date") DateDimension date);

    /**
     * All daily KPI rows for a store whose calendar date falls in {@code [startDate, endDate]} (inclusive).
     */
    @Query("SELECT DISTINCT f FROM FactKpiDaily f JOIN FETCH f.date dd JOIN FETCH f.store s WHERE s.id = :storeId AND dd.date >= :startDate AND dd.date <= :endDate")
    List<FactKpiDaily> findAllByStoreIdAndDateBetween(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

