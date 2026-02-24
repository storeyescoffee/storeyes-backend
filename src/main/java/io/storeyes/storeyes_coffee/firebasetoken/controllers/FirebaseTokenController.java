package io.storeyes.storeyes_coffee.firebasetoken.controllers;

import io.storeyes.storeyes_coffee.firebasetoken.dto.CreateFirebaseTokenRequest;
import io.storeyes.storeyes_coffee.firebasetoken.services.FirebaseTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/firebase-tokens")
@RequiredArgsConstructor
public class FirebaseTokenController {

    private final FirebaseTokenService firebaseTokenService;

    /**
     * Insert Firebase token for the authenticated user and their store.
     * POST /api/firebase-tokens
     * Requires: JWT token in Authorization header (user and store are derived from it)
     *
     * Request body:
     * {
     *   "token": "firebase-device-token-string",
     *   "platform": "IOS" | "ANDROID"
     * }
     *
     * Always creates a new token record and returns the generated sessionId as a plain string.
     */
    @PostMapping
    public ResponseEntity<String> upsertToken(@Valid @RequestBody CreateFirebaseTokenRequest request) {
        String sessionId = firebaseTokenService.upsertToken(request);
        return ResponseEntity.ok(sessionId);
    }

    /**
     * Revoke (delete) a Firebase token by its sessionId for the current user.
     * DELETE /api/firebase-tokens/{sessionId}
     *
     * Returns 204 No Content on success, 404 if no token is found for this user+sessionId.
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revokeBySessionId(@PathVariable String sessionId) {
        firebaseTokenService.revokeBySessionId(sessionId);
        return ResponseEntity.noContent().build();
    }
}
