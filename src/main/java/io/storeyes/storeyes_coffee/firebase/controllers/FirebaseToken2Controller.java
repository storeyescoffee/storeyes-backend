package io.storeyes.storeyes_coffee.firebase.controllers;

import io.storeyes.storeyes_coffee.firebase.dto.FirebaseTokenActionRequest;
import io.storeyes.storeyes_coffee.firebase.repositories.FirebaseToken2Repository;
import io.storeyes.storeyes_coffee.firebase.services.FirebaseToken2Service;
import io.storeyes.storeyes_coffee.notification.services.FcmNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/firebase-tokens-v2")
@RequiredArgsConstructor
public class FirebaseToken2Controller {

    private final FirebaseToken2Service firebaseToken2Service;
    private final FirebaseToken2Repository firebaseToken2Repository;
    private final FcmNotificationService fcmNotificationService;

    /**
     * Handle login/logout action for a mobile device.
     * POST /api/firebase-tokens-v2/action
     *
     * Request body:
     * {
     *   "mobileId": "device-unique-id",
     *   "action": "login" | "logout",
     *   "firebaseToken": "fcm-token-string"   // required for login, ignored for logout
     * }
     *
     * Finds the row by mobileId, updates isLoggedIn and lastLoggedInTimestamp.
     * On login: also updates the firebaseToken.
     * Returns 204 No Content on success.
     */
    @PostMapping("/action")
    public ResponseEntity<Void> handleAction(@Valid @RequestBody FirebaseTokenActionRequest request) {
        firebaseToken2Service.handleAction(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Send a test push notification to all active devices of a user.
     * POST /api/firebase-tokens-v2/test/{userId}
     *
     * Public endpoint. Looks up the active FCM tokens for the given user in
     * firebase_tokens_v2 and sends a test message to each of them.
     *
     * Returns: { "tokens": <count of active tokens>, "notified": <count sent successfully> }
     */
    @PostMapping("/test/{userId}")
    public ResponseEntity<Map<String, Object>> sendTestMessage(@PathVariable String userId) {
        List<String> tokens = firebaseToken2Repository.findActiveTokensByUserId(userId);
        if (tokens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No active firebase tokens found for user: " + userId);
        }

        int notified = fcmNotificationService.sendToTokens(
                tokens,
                "Test notification",
                "This is a test message from Storeyes Coffee",
                Map.of("type", "test")
        );

        return ResponseEntity.ok(Map.of(
                "tokens", tokens.size(),
                "notified", notified
        ));
    }
}
