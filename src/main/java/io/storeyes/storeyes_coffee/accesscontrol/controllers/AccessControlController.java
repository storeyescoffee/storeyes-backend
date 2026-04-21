package io.storeyes.storeyes_coffee.accesscontrol.controllers;

import io.storeyes.storeyes_coffee.accesscontrol.dto.ACEventByDateItemDTO;
import io.storeyes.storeyes_coffee.accesscontrol.services.ACEventService;
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
@RequestMapping("/api/access-control")
@RequiredArgsConstructor
public class AccessControlController {

    private final ACEventService acEventService;

    /**
     * Access control events for the current store on the given date (local calendar day).
     * GET /api/access-control/events?date=YYYY-MM-DD
     */
    @GetMapping("/events")
    public ResponseEntity<List<ACEventByDateItemDTO>> getEventsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new RuntimeException("Store context not found for current user");
        }

        return ResponseEntity.ok(acEventService.listByStoreAndDate(storeId, date));
    }
}
