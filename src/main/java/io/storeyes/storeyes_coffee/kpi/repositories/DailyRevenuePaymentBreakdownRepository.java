package io.storeyes.storeyes_coffee.kpi.repositories;

import io.storeyes.storeyes_coffee.kpi.entities.DateDimension;
import io.storeyes.storeyes_coffee.kpi.entities.DailyRevenuePaymentBreakdown;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyRevenuePaymentBreakdownRepository extends JpaRepository<DailyRevenuePaymentBreakdown, Long> {

    Optional<DailyRevenuePaymentBreakdown> findByStoreIdAndDate(Long storeId, DateDimension date);
}
