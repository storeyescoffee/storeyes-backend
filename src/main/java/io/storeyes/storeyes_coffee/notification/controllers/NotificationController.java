package io.storeyes.storeyes_coffee.notification.controllers;

import io.storeyes.storeyes_coffee.notification.dto.DailyKpiNotificationRequest;
import io.storeyes.storeyes_coffee.notification.dto.SendNotificationRequest;
import io.storeyes.storeyes_coffee.notification.services.NotificationService;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String STORE_CODE_HEADER = "X-STORE-CODE";

    private final StoreRepository storeRepository;
    private final NotificationService notificationService;

    /**
     * Open endpoint: notify all users attached to a store about its daily KPI (RAZ).
     * POST /api/notifications/daily-kpi
     *
     * Headers:
     * - X-STORE-CODE: store code (required)
     *
     * Body:
     * { "raz": 1234.56 }
     *
     * Sends a push to every active device of users mapped to the store with a message like
     * "{store name} received {raz} DH today".
     *
     * Returns: { "storeId": ..., "store": "...", "notified": <count sent successfully> }
     */
    @PostMapping("/daily-kpi")
    public ResponseEntity<Map<String, Object>> notifyDailyKpi(
            @RequestHeader(STORE_CODE_HEADER) String storeCode,
            @Valid @RequestBody DailyKpiNotificationRequest request) {

        Store store = storeRepository.findByCode(storeCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Store not found with code: " + storeCode));

        String body = store.getName() + " received " + formatRaz(request.getRaz()) + " DH today";

        SendNotificationRequest notification = new SendNotificationRequest();
        notification.setStoreId(store.getId());
        notification.setTitle("Daily KPI");
        notification.setBody(body);

        int notified = notificationService.send(notification);
        log.debug("Daily KPI notification for store '{}' (id={}) sent to {} device(s): {}",
                store.getName(), store.getId(), notified, body);

        return ResponseEntity.ok(Map.of(
                "storeId", store.getId(),
                "store", store.getName(),
                "notified", notified
        ));
    }

    /** Trims a trailing ".0" so whole amounts read "1200" instead of "1200.0". */
    private String formatRaz(Double raz) {
        if (raz == raz.longValue()) {
            return String.valueOf(raz.longValue());
        }
        return String.valueOf(raz);
    }
}
