package io.storeyes.storeyes_coffee.alerts.controllers;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertDetailsDTO;
import io.storeyes.storeyes_coffee.alerts.dto.CreateAlertRequest;
import io.storeyes.storeyes_coffee.alerts.dto.UpdateHumanJudgementRequest;
import io.storeyes.storeyes_coffee.alerts.dto.UpdateSecondaryVideoRequest;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import io.storeyes.storeyes_coffee.alerts.services.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
    
    private final AlertService alertService;
    
    /**
     * Create a new alert
     * POST /api/alerts
     */
    @PostMapping
    public ResponseEntity<Void> createAlert(@Valid @RequestBody CreateAlertRequest request) {
        alertService.createAlert(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    

    @GetMapping
    public ResponseEntity<List<Alert>> getAlertsByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) Boolean unprocessed,
            @RequestParam(required = false) Long store_id) {
        List<Alert> alerts = alertService.getAlertsByDate(date, endDate, unprocessed, store_id);
        return ResponseEntity.ok(alerts);
    }

    

    
    
    /**
     * Update secondary video URL, image URL and mark alert as processed
     * PUT /api/alerts/{id}/secondary-video
     */
    @PutMapping("/{id}/secondary-video")
    public ResponseEntity<Void> updateSecondaryVideoAndMarkProcessed(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSecondaryVideoRequest request) {
        boolean updated = alertService.updateSecondaryVideoAndMarkProcessed(id, request.getSecondaryVideoUrl(), request.getImageUrl());
        
        if (updated) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    /**
     * Update human judgement for an alert
     * PATCH /api/alerts/{id}/human-judgement
     */
    @PatchMapping("/{id}/human-judgement")
    public ResponseEntity<?> updateHumanJudgement(
            @PathVariable Long id,
            @Valid @RequestBody UpdateHumanJudgementRequest request) {
        boolean updated = alertService.updateHumanJudgement(id, request.getHumanJudgement());
        
        if (updated) {
            // Fetch and return the updated alert
            Alert updatedAlert = alertService.getAlertById(id);
            return ResponseEntity.ok(updatedAlert);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Alert not found with id: " + id);
        }
    }
    
    /**
     * Get alert by ID
     * GET /api/alerts/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Alert> getAlertById(@PathVariable Long id) {
        Alert alert = alertService.getAlertById(id);
        return ResponseEntity.ok(alert);
    }
    
    /**
     * Get alert details with sales by ID
     * GET /api/alerts/{id}/details
     * Uses JOIN FETCH to avoid N+1 query problem
     */
    @GetMapping("/{id}/details")
    public ResponseEntity<AlertDetailsDTO> getAlertDetailsWithSales(@PathVariable Long id) {
        AlertDetailsDTO alertDetails = alertService.getAlertDetailsWithSales(id);
        return ResponseEntity.ok(alertDetails);
    }
    
    /**
     * Get all alerts
     * GET /api/alerts/all
     */
    @GetMapping("/all")
    public ResponseEntity<List<AlertDTO>> getAllAlerts() {
        List<AlertDTO> alerts = alertService.getAllAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    /**
     * Get alert summaries (alertId and alertDate) for today by store_id
     * GET /api/alerts/today?store_id={storeId}
     * 
     * Query Parameters:
     * - store_id: Store ID - required
     * 
     * Returns alerts for today for the specified store
     */
}

