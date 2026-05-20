package io.storeyes.storeyes_coffee.firebase.controllers;

import io.storeyes.storeyes_coffee.firebase.dto.FirebaseTokenActionRequest;
import io.storeyes.storeyes_coffee.firebase.services.FirebaseToken2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/firebase-tokens-v2")
@RequiredArgsConstructor
public class FirebaseToken2Controller {

    private final FirebaseToken2Service firebaseToken2Service;

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
}
