package io.storeyes.storeyes_coffee.alerts.repositories;

import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {
    
    // Find alerts by exact date (day)
    @Query("SELECT a FROM Alert a WHERE DATE(a.alertDate) = DATE(:date) ORDER BY a.alertDate DESC")
    List<Alert> findByAlertDate(LocalDateTime date);
    
    // Find alert by exact alertDate timestamp
    @Query("SELECT a FROM Alert a WHERE a.alertDate = :alertDate")
    Optional<Alert> findByExactAlertDate(LocalDateTime alertDate);
    
    // Find alerts within a date range
    @Query("SELECT a FROM Alert a WHERE a.alertDate >= :startDate AND a.alertDate <= :endDate ORDER BY a.alertDate DESC")
    List<Alert> findByAlertDateBetween(
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    
    // Find all processed alerts
    @Query("SELECT a FROM Alert a WHERE a.isProcessed = true ORDER BY a.alertDate DESC")
    List<Alert> findAllProcessed();
    
    // Find all unprocessed alerts
    @Query("SELECT a FROM Alert a WHERE a.isProcessed = false ORDER BY a.alertDate DESC")
    List<Alert> findAllUnprocessed();
    
    // Find processed alerts by exact date (day)
    @Query("SELECT a FROM Alert a WHERE DATE(a.alertDate) = DATE(:date) AND a.isProcessed = true ORDER BY a.alertDate DESC")
    List<Alert> findProcessedByAlertDate(LocalDateTime date);
    
    // Find unprocessed alerts by exact date (day)
    @Query("SELECT a FROM Alert a WHERE DATE(a.alertDate) = DATE(:date) AND a.isProcessed = false ORDER BY a.alertDate DESC")
    List<Alert> findUnprocessedByAlertDate(LocalDateTime date);
    
    // Find processed alerts within a date range
    @Query("SELECT a FROM Alert a WHERE a.alertDate >= :startDate AND a.alertDate <= :endDate AND a.isProcessed = true ORDER BY a.alertDate DESC")
    List<Alert> findProcessedByAlertDateBetween(
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    
    // Find unprocessed alerts within a date range
    @Query("SELECT a FROM Alert a WHERE a.alertDate >= :startDate AND a.alertDate <= :endDate AND a.isProcessed = false ORDER BY a.alertDate DESC")
    List<Alert> findUnprocessedByAlertDateBetween(
        LocalDateTime startDate,
        LocalDateTime endDate
    );
    
    // Find processed alerts by exact date (day) and store ID
    @Query("SELECT a FROM Alert a WHERE DATE(a.alertDate) = DATE(:date) AND a.isProcessed = true AND a.store.id = :storeId ORDER BY a.alertDate DESC")
    List<Alert> findProcessedByAlertDateAndStoreId(LocalDateTime date, Long storeId);
    
    // Find unprocessed alerts by exact date (day) and store ID
    @Query("SELECT a FROM Alert a WHERE DATE(a.alertDate) = DATE(:date) AND a.isProcessed = false AND a.store.id = :storeId ORDER BY a.alertDate DESC")
    List<Alert> findUnprocessedByAlertDateAndStoreId(LocalDateTime date, Long storeId);
    
    // Find processed alerts within a date range and store ID
    @Query("SELECT a FROM Alert a WHERE a.alertDate >= :startDate AND a.alertDate <= :endDate AND a.isProcessed = true AND a.store.id = :storeId ORDER BY a.alertDate DESC")
    List<Alert> findProcessedByAlertDateBetweenAndStoreId(
        LocalDateTime startDate,
        LocalDateTime endDate,
        Long storeId
    );
    
    // Find unprocessed alerts within a date range and store ID
    @Query("SELECT a FROM Alert a WHERE a.alertDate >= :startDate AND a.alertDate <= :endDate AND a.isProcessed = false AND a.store.id = :storeId ORDER BY a.alertDate DESC")
    List<Alert> findUnprocessedByAlertDateBetweenAndStoreId(
        LocalDateTime startDate,
        LocalDateTime endDate,
        Long storeId
    );
    
    // Find alerts by today's date and store ID
    @Query("SELECT a FROM Alert a WHERE DATE(a.alertDate) = DATE(:today) AND a.store.id = :storeId AND a.isProcessed = false ORDER BY a.alertDate")
    List<Alert> findByTodayAndStoreId(LocalDateTime today, Long storeId);
    
    // Update human judgement directly via query
    @Modifying
    @Query("UPDATE Alert a SET a.humanJudgement = :judgement, a.updatedAt = :updatedAt WHERE a.id = :id")
    int updateHumanJudgement(
        Long id, 
        HumanJudgement judgement, 
        LocalDateTime updatedAt
    );
    
    // Update secondary video URL, image URL and mark as processed
    @Modifying
    @Query("UPDATE Alert a SET a.secondaryVideoUrl = :secondaryVideoUrl, a.imageUrl = :imageUrl, a.isProcessed = true, a.updatedAt = :updatedAt WHERE a.id = :id")
    int updateSecondaryVideoAndMarkProcessed(
        Long id, 
        String secondaryVideoUrl, 
        String imageUrl,
        LocalDateTime updatedAt
    );
}

