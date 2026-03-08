package io.storeyes.storeyes_coffee.coffeetracker.controllers;

import io.storeyes.storeyes_coffee.coffeetracker.dto.CoffeeTrackerEventDTO;
import io.storeyes.storeyes_coffee.coffeetracker.services.CoffeeTrackerService;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/coffee-tracker")
@RequiredArgsConstructor
public class CoffeeTrackerController {

    private final CoffeeTrackerService coffeeTrackerService;

    /**
     * Get COMPLETED state events for the current user's store on the given date.
     * GET /api/coffee-tracker/completed?date=YYYY-MM-DD
     */
    @GetMapping("/completed")
    public ResponseEntity<List<CoffeeTrackerEventDTO>> getCompletedEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }

        List<CoffeeTrackerEventDTO> events = coffeeTrackerService.getCompletedEventsByStoreAndDate(storeId, date);
        return ResponseEntity.ok(events);
    }
}
