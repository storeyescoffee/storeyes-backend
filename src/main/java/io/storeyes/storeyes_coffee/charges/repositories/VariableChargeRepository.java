package io.storeyes.storeyes_coffee.charges.repositories;

import io.storeyes.storeyes_coffee.charges.entities.VariableCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VariableChargeRepository extends JpaRepository<VariableCharge, Long> {
    
    /**
     * Find variable charges by store and date range (all parameters required)
     */
    @Query("SELECT vc FROM VariableCharge vc WHERE vc.store.id = :storeId AND " +
           "vc.date >= :startDate AND vc.date <= :endDate " +
           "ORDER BY vc.date DESC")
    List<VariableCharge> findByStoreIdAndDateRange(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    /**
     * Find variable charges by store, date range, and category
     */
    @Query("SELECT vc FROM VariableCharge vc WHERE vc.store.id = :storeId AND " +
           "vc.date >= :startDate AND vc.date <= :endDate AND " +
           "vc.category = :category " +
           "ORDER BY vc.date DESC")
    List<VariableCharge> findByStoreIdAndDateRangeAndCategory(
            @Param("storeId") Long storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("category") String category
    );
    
    /**
     * Find all variable charges by store
     */
    List<VariableCharge> findByStoreIdOrderByDateDesc(Long storeId);
    
    /**
     * Find variable charges by store and category
     */
    List<VariableCharge> findByStoreIdAndCategory(Long storeId, String category);
    
    /**
     * Find variable charges by store and date
     */
    List<VariableCharge> findByStoreIdAndDate(Long storeId, LocalDate date);

    /**
     * Find variable charges by store and exact notes value (used to locate charges linked to a supplier order)
     */
    List<VariableCharge> findByStoreIdAndNotes(Long storeId, String notes);
}
