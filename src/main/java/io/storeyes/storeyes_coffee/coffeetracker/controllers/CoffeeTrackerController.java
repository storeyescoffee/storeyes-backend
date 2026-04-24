package io.storeyes.storeyes_coffee.coffeetracker.controllers;

import io.storeyes.storeyes_coffee.coffeetracker.entities.CoffeeTracker;
import io.storeyes.storeyes_coffee.coffeetracker.services.CoffeeTrackerService;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/coffee-tracker")
@RequiredArgsConstructor
public class CoffeeTrackerController {

    private final CoffeeTrackerService coffeeTrackerService;

    /**
     * Completed coffee trackers for the current user's store on a given calendar day
     * (by {@code DATE(timestamp)} semantics). Store comes from {@link CurrentStoreContext}.
     * GET /api/coffee-tracker?date={yyyy-MM-dd}
     */
    @GetMapping
    public ResponseEntity<List<CoffeeTracker>> getCompletedByStoreAndDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }
        return ResponseEntity.ok(coffeeTrackerService.findCompletedByStoreAndDate(storeId, date));
    }
}
