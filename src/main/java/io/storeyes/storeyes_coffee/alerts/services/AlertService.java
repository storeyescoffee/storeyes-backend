package io.storeyes.storeyes_coffee.alerts.services;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.dto.CreateAlertRequest;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import io.storeyes.storeyes_coffee.alerts.mappers.AlertMapper;
import io.storeyes.storeyes_coffee.alerts.repositories.AlertRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {
    
    private final AlertRepository alertRepository;
    private final AlertMapper alertMapper;
    
    /**
     * Create a new alert
     * If an alert with the same alertDate already exists, ignores it without creating a duplicate
     */
    public void createAlert(CreateAlertRequest request) {
        // Check if an alert with the same alertDate already exists
        if (alertRepository.findByExactAlertDate(request.getAlertDate()).isEmpty()) {
            // No existing alert found, create a new one
            Alert alert = Alert.builder()
                    .alertDate(request.getAlertDate())
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
     */
    public List<Alert> getAlertsByDate(LocalDateTime date, LocalDateTime endDate, Boolean unprocessed) {
        boolean filterUnprocessed = Boolean.TRUE.equals(unprocessed);
        
        // Default to today's date if not provided
        if (date == null) {
            date = LocalDate.now().atStartOfDay();
        }
        
        // Date filter is provided (or defaulted to today)
        if (endDate != null) {
            // Date range provided
            if (filterUnprocessed) {
                return alertRepository.findUnprocessedByAlertDateBetween(date, endDate);
            } else {
                return alertRepository.findProcessedByAlertDateBetween(date, endDate);
            }
        } else {
            // Exact date provided (or defaulted to today)
            if (filterUnprocessed) {
                return alertRepository.findUnprocessedByAlertDate(date);
            } else {
                return alertRepository.findProcessedByAlertDate(date);
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
     * Update secondary video URL and mark alert as processed
     */
    @Transactional
    public boolean updateSecondaryVideoAndMarkProcessed(Long alertId, String secondaryVideoUrl) {
        LocalDateTime now = LocalDateTime.now();
        int updated = alertRepository.updateSecondaryVideoAndMarkProcessed(alertId, secondaryVideoUrl, now);
        return updated > 0;
    }
}

