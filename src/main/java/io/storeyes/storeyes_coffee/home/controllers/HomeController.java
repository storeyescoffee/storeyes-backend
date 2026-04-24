package io.storeyes.storeyes_coffee.home.controllers;

import io.storeyes.storeyes_coffee.home.dto.HomeSummaryResponse;
import io.storeyes.storeyes_coffee.home.services.HomeSummaryService;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeSummaryService homeSummaryService;

    /**
     * Home tab summary: month profit (MAD), processed alerts count, daily TTC for the display day,
     * plus {@code displayDate} and {@code monthKey} so client and server share the same day/month rules.
     * <p>
     * GET /api/home/summary<br>
     * Optional: {@code ?date=yyyy-MM-dd} to pin the display day (otherwise 21:00 UTC rollover applies).
     */
    @GetMapping("/summary")
    public ResponseEntity<HomeSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }
        return ResponseEntity.ok(homeSummaryService.getSummary(storeId, date));
    }
}
