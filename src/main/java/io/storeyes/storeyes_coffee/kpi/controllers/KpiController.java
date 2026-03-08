package io.storeyes.storeyes_coffee.kpi.controllers;

import io.storeyes.storeyes_coffee.kpi.dto.DailyReportDTO;
import io.storeyes.storeyes_coffee.kpi.dto.UpdateRevenueBreakdownRequest;
import io.storeyes.storeyes_coffee.kpi.repositories.DateDimensionRepository;
import io.storeyes.storeyes_coffee.kpi.services.KpiService;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Collections;

@RestController
@RequestMapping("/api/kpi")
@RequiredArgsConstructor
public class KpiController {
    
    private final KpiService kpiService;
    private final DateDimensionRepository dateDimensionRepository;
    
    /**
     * Get daily report for the authenticated user's store on a specific date
     * GET /api/kpi/daily-report?date={date}
     * 
     * Query Parameters:
     * - date: Date in format YYYY-MM-DD (optional, defaults to yesterday)
     * 
     * The store is automatically determined from the authenticated user's role mapping
     * Returns empty object {} if the date does not exist in date dimension
     */
    @GetMapping("/daily-report")
    public ResponseEntity<?> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }
        
        // Default to yesterday if date not provided
        if (date == null) {
            date = LocalDate.now().minusDays(1);
        }
        
        // Check if date exists in date dimension
        if (!dateDimensionRepository.findByDate(date).isPresent()) {
            return ResponseEntity.ok(Collections.emptyMap());
        }
        
        DailyReportDTO report = kpiService.getDailyReport(storeId, date);
        return ResponseEntity.ok(report);
    }

    /**
     * Update TPE (card payment) for a daily report. Espèce = TTC - TPE (computed automatically).
     * PATCH /api/kpi/daily-report/revenue-breakdown?date={date}
     *
     * Request body: { "tpe": 1234.56 }
     * TPE must be >= 0 and <= total revenue (TTC).
     */
    @PatchMapping("/daily-report/revenue-breakdown")
    public ResponseEntity<?> updateRevenueBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody UpdateRevenueBreakdownRequest request) {

        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }

        kpiService.updateRevenueBreakdown(storeId, date, request.getTpe());

        // Return updated daily report
        DailyReportDTO report = kpiService.getDailyReport(storeId, date);
        return ResponseEntity.ok(report);
    }
}

