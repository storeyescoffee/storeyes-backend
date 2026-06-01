package io.storeyes.storeyes_coffee.kpi.controllers;

import io.storeyes.storeyes_coffee.kpi.dto.DailyReportDTO;
import io.storeyes.storeyes_coffee.kpi.dto.Granularity;
import io.storeyes.storeyes_coffee.kpi.dto.StatisticsResponseDTO;
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
import java.util.Map;

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

    /**
     * Get aggregated statistics for a date range — powers the Statistiques dashboard.
     * <p>
     * GET /api/kpi/statistics
     *
     * <ul>
     *   <li><b>DAILY</b>   — {@code from} and {@code to} required; one chart point per day.</li>
     *   <li><b>WEEKLY</b>  — {@code from} and {@code to} required; chart points are rolling
     *       7-day windows anchored on J-1 (yesterday), going backward.</li>
     *   <li><b>MONTHLY</b> — {@code from}/{@code to} are optional and ignored; the backend
     *       always returns the current month + the 11 preceding months (12 points).</li>
     * </ul>
     *
     * @param from        range start (YYYY-MM-DD); required for DAILY and WEEKLY
     * @param to          range end   (YYYY-MM-DD); required for DAILY and WEEKLY
     * @param granularity DAILY | WEEKLY | MONTHLY (default DAILY)
     * @param limit       max products to return in bestSales / worstSales (default 5)
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAILY") Granularity granularity,
            @RequestParam(defaultValue = "5") int limit) {

        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }

        // from and to are required for DAILY and WEEKLY
        if (granularity != Granularity.MONTHLY) {
            if (from == null || to == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "'from' and 'to' are required for DAILY and WEEKLY granularity"));
            }
            if (from.isAfter(to)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "'from' must not be after 'to'"));
            }
        }
        // For MONTHLY: from/to are ignored; the service computes the last 12 months internally.

        StatisticsResponseDTO response = kpiService.getStatistics(storeId, from, to, granularity, limit);
        return ResponseEntity.ok(response);
    }
}

