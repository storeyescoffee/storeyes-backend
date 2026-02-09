package io.storeyes.storeyes_coffee.charges.repositories;

import io.storeyes.storeyes_coffee.charges.entities.ChargeCategory;
import io.storeyes.storeyes_coffee.charges.entities.ChargePeriod;
import io.storeyes.storeyes_coffee.charges.entities.FixedCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FixedChargeRepository extends JpaRepository<FixedCharge, Long> {
    
    /**
     * Find fixed charges by store and month key
     */
    List<FixedCharge> findByStoreIdAndMonthKey(Long storeId, String monthKey);
    
    /**
     * Find fixed charges by store and category
     */
    List<FixedCharge> findByStoreIdAndCategory(Long storeId, ChargeCategory category);
    
    /**
     * Find fixed charges by store and period
     */
    List<FixedCharge> findByStoreIdAndPeriod(Long storeId, ChargePeriod period);
    
    /**
     * Find fixed charges by store, category and month key
     */
    List<FixedCharge> findByStoreIdAndCategoryAndMonthKey(Long storeId, ChargeCategory category, String monthKey);
    
    /**
     * Find fixed charges by store, category, month key, and period
     */
    List<FixedCharge> findByStoreIdAndCategoryAndMonthKeyAndPeriod(Long storeId, ChargeCategory category, String monthKey, ChargePeriod period);
    
    /**
     * Find fixed charges by store, category, month key, and week key
     */
    List<FixedCharge> findByStoreIdAndCategoryAndMonthKeyAndWeekKey(Long storeId, ChargeCategory category, String monthKey, String weekKey);
    
    /**
     * Find fixed charges by store, month key, and week key
     */
    List<FixedCharge> findByStoreIdAndMonthKeyAndWeekKey(Long storeId, String monthKey, String weekKey);
    
    /**
     * Find previous fixed charge for trend calculation
     * Looks for a charge with same store, category and period, but previous month/week
     */
    @Query("SELECT fc FROM FixedCharge fc WHERE fc.store.id = :storeId AND fc.category = :category AND fc.period = :period " +
           "AND ((:period = io.storeyes.storeyes_coffee.charges.entities.ChargePeriod.MONTH AND fc.monthKey < :monthKey) " +
           "OR (:period = io.storeyes.storeyes_coffee.charges.entities.ChargePeriod.WEEK AND fc.weekKey < :weekKey)) " +
           "ORDER BY fc.monthKey DESC, fc.weekKey DESC")
    List<FixedCharge> findPreviousCharges(
            @Param("storeId") Long storeId,
            @Param("category") ChargeCategory category,
            @Param("period") ChargePeriod period,
            @Param("monthKey") String monthKey,
            @Param("weekKey") String weekKey
    );
    
    /**
     * Find fixed charges for historical chart data
     */
    @Query("SELECT fc FROM FixedCharge fc WHERE fc.store.id = :storeId AND fc.category = :category AND fc.period = :period " +
           "AND fc.monthKey <= :monthKey ORDER BY fc.monthKey DESC")
    List<FixedCharge> findHistoricalCharges(
            @Param("storeId") Long storeId,
            @Param("category") ChargeCategory category,
            @Param("period") ChargePeriod period,
            @Param("monthKey") String monthKey
    );

    /**
     * Find previous fixed charge for trend (OTHER category: same custom name)
     */
    @Query("SELECT fc FROM FixedCharge fc WHERE fc.store.id = :storeId AND fc.category = :category AND fc.period = :period " +
           "AND fc.name = :name AND ((:period = io.storeyes.storeyes_coffee.charges.entities.ChargePeriod.MONTH AND fc.monthKey < :monthKey) " +
           "OR (:period = io.storeyes.storeyes_coffee.charges.entities.ChargePeriod.WEEK AND fc.weekKey < :weekKey)) " +
           "ORDER BY fc.monthKey DESC, fc.weekKey DESC")
    List<FixedCharge> findPreviousChargesWithName(
            @Param("storeId") Long storeId,
            @Param("category") ChargeCategory category,
            @Param("period") ChargePeriod period,
            @Param("name") String name,
            @Param("monthKey") String monthKey,
            @Param("weekKey") String weekKey
    );

    /**
     * Find historical charges for chart (OTHER category: same custom name)
     */
    @Query("SELECT fc FROM FixedCharge fc WHERE fc.store.id = :storeId AND fc.category = :category AND fc.period = :period " +
           "AND fc.name = :name AND fc.monthKey <= :monthKey ORDER BY fc.monthKey DESC")
    List<FixedCharge> findHistoricalChargesWithName(
            @Param("storeId") Long storeId,
            @Param("category") ChargeCategory category,
            @Param("period") ChargePeriod period,
            @Param("name") String name,
            @Param("monthKey") String monthKey
    );
}
