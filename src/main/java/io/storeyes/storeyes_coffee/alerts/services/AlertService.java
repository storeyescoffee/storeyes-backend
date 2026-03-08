package io.storeyes.storeyes_coffee.alerts.services;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertDetailsDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertSummaryDTO;
import io.storeyes.storeyes_coffee.alerts.dto.CreateAlertRequest;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import io.storeyes.storeyes_coffee.alerts.mappers.AlertMapper;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
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
    
    /**
     * Create a new alert
     * If an alert with the same alertDate and alertType already exists, ignores it without creating a duplicate
     * If alertType is null, defaults to NOT_TAPPED
     */
    public void createAlert(CreateAlertRequest request) {
        // Get store by code
        var store = storeRepository.findByCode(request.getStoreCode())
                .orElseThrow(() -> new RuntimeException("Store not found with code: " + request.getStoreCode()));
        
        // Determine alertType (default to NOT_TAPPED if null)
        io.storeyes.storeyes_coffee.alerts.entities.AlertType alertType = request.getAlertType() != null 
                ? request.getAlertType() 
                : io.storeyes.storeyes_coffee.alerts.entities.AlertType.NOT_TAPPED;
        
        // Check if an alert with the same alertDate, alertType, and storeId already exists
        if (alertRepository.findByExactAlertDateAndAlertTypeAndStoreId(request.getAlertDate(), alertType, store.getId()).isEmpty()) {
            // No existing alert found, create a new one
            Alert alert = Alert.builder()
                    .alertDate(request.getAlertDate())
                    .store(store)
                    .mainVideoUrl(request.getMainVideoUrl())
                    .productName(request.getProductName())
                    .imageUrl(request.getImageUrl())
                    .alertType(alertType)
                    .isProcessed(false)
                    .build();
            
            alertRepository.save(alert);
        }
    }
    
    /**
     * Get alerts by date and processed status (supports both exact date and date range)
     * Store is resolved from CurrentStoreContext (set by StoreContextInterceptor).
     * By default returns processed alerts, unless unprocessed=true
     * If date is not provided, defaults to today's date
     * If returnType=true, returns only alerts with alertType=RETURN
     * If alertType is provided (NOT_TAPPED or RETURN), returns only alerts of that type (takes precedence over returnType)
     */
    public List<Alert> getAlertsByDate(LocalDateTime date, LocalDateTime endDate, Boolean unprocessed, Boolean returnType, io.storeyes.storeyes_coffee.alerts.entities.AlertType alertType) {
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }
        boolean filterUnprocessed = Boolean.TRUE.equals(unprocessed);
        boolean filterReturnType = Boolean.TRUE.equals(returnType);
        // alertType param takes precedence; if not set, fall back to returnType for backward compat
        io.storeyes.storeyes_coffee.alerts.entities.AlertType filterAlertType = alertType != null
                ? alertType
                : (filterReturnType ? io.storeyes.storeyes_coffee.alerts.entities.AlertType.RETURN : null);
        
        // Default to today's date if not provided
        if (date == null) {
            date = LocalDate.now().atStartOfDay();
        }
        
        List<Alert> alerts;
        // Date filter is provided (or defaulted to today)
        if (endDate != null) {
            // Date range
            if (filterUnprocessed) {
                alerts = alertRepository.findUnprocessedByAlertDateBetweenAndStoreId(date, endDate, storeId);
            } else {
                alerts = alertRepository.findProcessedByAlertDateBetweenAndStoreId(date, endDate, storeId);
            }
        } else {
            // Exact date (or defaulted to today)
            if (filterUnprocessed) {
                alerts = alertRepository.findUnprocessedByAlertDateAndStoreId(date, storeId);
            } else {
                alerts = alertRepository.findProcessedByAlertDateAndStoreId(date, storeId);
            }
        }
        
        // Apply filters
        return alerts.stream()
                .filter(a -> {
                    // If alertType filter is set, only return matching alerts
                    if (filterAlertType != null) {
                        if (a.getAlertType() != filterAlertType) return false;
                    }
                    // Otherwise, return alerts with humanJudgement NEW or TRUE_POSITIVE
                    HumanJudgement h = a.getHumanJudgement();
                    return h == null || h == HumanJudgement.NEW || h == HumanJudgement.TRUE_POSITIVE;
                })
                .collect(Collectors.toList());
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
    
    /**
     * Get alert summaries (alertId and alertDate) by date and store_id
     * Returns all alerts (regardless of processed status) for the specified date and store
     */
    public List<AlertSummaryDTO> getAlertSummariesByDateAndStoreId(LocalDate date, Long storeId) {
        // Find alerts by date and store
        List<Alert> alerts = alertRepository.findByAlertDateAndStoreId(date, storeId);
        
        // Map to summary DTO
        return alerts.stream()
                .map(alert -> AlertSummaryDTO.builder()
                        .alertId(alert.getId())
                        .alertDate(alert.getAlertDate())
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * Get alert details with sales by alert ID
     * Uses JOIN FETCH to avoid N+1 query problem
     * @param id Alert ID
     * @return AlertDetailsDTO with sales
     */
    public AlertDetailsDTO getAlertDetailsWithSales(Long id) {
        Alert alert = alertRepository.findByIdWithSales(id)
                .orElseThrow(() -> new RuntimeException("Alert not found with id: " + id));
        
        // Use mapper to convert Alert to AlertDetailsDTO
        return alertMapper.toDetailsDTO(alert);
    }
}

