package io.storeyes.storeyes_coffee.alerts.services;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertSummaryDTO;
import io.storeyes.storeyes_coffee.alerts.dto.CreateAlertRequest;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import io.storeyes.storeyes_coffee.alerts.mappers.AlertMapper;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {
    
    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    
    /**
     * Create a new alert
     * If an alert with the same alertDate already exists, ignores it without creating a duplicate
     */
    public void createAlert(CreateAlertRequest request) {
        // Get store by code
        var store = storeRepository.findByCode(request.getStoreCode())
                .orElseThrow(() -> new RuntimeException("Store not found with code: " + request.getStoreCode()));
        
        // Check if an alert with the same alertDate already exists
        if (alertRepository.findByExactAlertDate(request.getAlertDate()).isEmpty()) {
            // No existing alert found, create a new one
            Alert alert = Alert.builder()
                    .alertDate(request.getAlertDate())
                    .store(store)
                    .mainVideoUrl(request.getMainVideoUrl())
                    .productName(request.getProductName())
                    .imageUrl(request.getImageUrl())
                    .isProcessed(false)
                    .build();
            
            alertRepository.save(alert);
        }
    }
    
    /**
     * Get alerts by date and processed status (supports both exact date and date range)
     * By default returns processed alerts, unless unprocessed=true
     * If date is not provided, defaults to today's date
     * Optionally filters by store_id if provided, otherwise uses authenticated user's store
     */
    public List<Alert> getAlertsByDate(LocalDateTime date, LocalDateTime endDate, Boolean unprocessed, Long storeId) {
        boolean filterUnprocessed = Boolean.TRUE.equals(unprocessed);
        
        // If storeId is not provided, get it from the authenticated user's store
        if (storeId == null) {
            String userId = KeycloakTokenUtils.getUserId();
            if (userId == null) {
                throw new RuntimeException("User is not authenticated");
            }
            storeId = storeService.getStoreByOwnerId(userId).getId();
        }
        
        // Default to today's date if not provided
        if (date == null) {
            date = LocalDate.now().atStartOfDay();
        }
        
        // Date filter is provided (or defaulted to today)
        if (endDate != null) {
            // Date range provided
            if (storeId != null) {
                if (filterUnprocessed) {
                    return alertRepository.findUnprocessedByAlertDateBetweenAndStoreId(date, endDate, storeId);
                } else {
                    return alertRepository.findProcessedByAlertDateBetweenAndStoreId(date, endDate, storeId);
                }
            } else {
                if (filterUnprocessed) {
                    return alertRepository.findUnprocessedByAlertDateBetween(date, endDate);
                } else {
                    return alertRepository.findProcessedByAlertDateBetween(date, endDate);
                }
            }
        } else {
            // Exact date provided (or defaulted to today)
            if (storeId != null) {
                if (filterUnprocessed) {
                    return alertRepository.findUnprocessedByAlertDateAndStoreId(date, storeId);
                } else {
                    return alertRepository.findProcessedByAlertDateAndStoreId(date, storeId);
                }
            } else {
                if (filterUnprocessed) {
                    return alertRepository.findUnprocessedByAlertDate(date);
                } else {
                    return alertRepository.findProcessedByAlertDate(date);
                }
            }
        }
    }
    
    /**
     * Update human judgement directly via query
     */
    @Transactional
    public boolean updateHumanJudgement(Long alertId, HumanJudgement judgement) {
        LocalDateTime now = LocalDateTime.now();
        int updated = alertRepository.updateHumanJudgement(alertId, judgement, now);
        return updated > 0;
    }
    
    /**
     * Get alert by ID
     */
    public Alert getAlertById(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + id));
    }
    
    /**
     * Get all alerts
     */
    public List<AlertDTO> getAllAlerts() {
        List<Alert> alerts = alertRepository.findAll();
        return alertMapper.toDTOList(alerts);
    }
    
    /**
     * Update secondary video URL, image URL and mark alert as processed
     */
    @Transactional
    public boolean updateSecondaryVideoAndMarkProcessed(Long alertId, String secondaryVideoUrl, String imageUrl) {
        LocalDateTime now = LocalDateTime.now();
        int updated = alertRepository.updateSecondaryVideoAndMarkProcessed(alertId, secondaryVideoUrl, imageUrl, now);
        return updated > 0;
    }
    
    /**
     * Get alert summaries (alertId and alertDate) for today by store_id
     * Returns alerts for today for the specified store
     */
    public List<AlertSummaryDTO> getTodayAlertsByStoreId(Long storeId) {
        // Get today's date
        LocalDateTime today = LocalDate.now().atStartOfDay();
        
        // Find alerts for today and store
        List<Alert> alerts = alertRepository.findByTodayAndStoreId(today, storeId);
        
        // Map to summary DTO
        return alerts.stream()
                .map(alert -> AlertSummaryDTO.builder()
                        .alertId(alert.getId())
                        .alertDate(alert.getAlertDate())
                        .build())
                .collect(Collectors.toList());
    }
}

