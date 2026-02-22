package io.storeyes.storeyes_coffee.firebasetoken.controllers;

import io.storeyes.storeyes_coffee.firebasetoken.dto.UpsertFirebaseTokenRequest;
import io.storeyes.storeyes_coffee.firebasetoken.entities.FirebaseToken;
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
     * Upsert Firebase token for the authenticated user and their store.
     * POST /api/firebase-tokens
     * Requires: JWT token in Authorization header (user and store are derived from it)
     *
     * Request body:
     * {
     *   "token": "firebase-device-token-string"
     * }
     *
     * If a token exists for this user+store, it is updated; otherwise a new one is created.
     */
    @PostMapping
    public ResponseEntity<FirebaseToken> upsertToken(@Valid @RequestBody UpsertFirebaseTokenRequest request) {
        FirebaseToken result = firebaseTokenService.upsertToken(request);
        return ResponseEntity.ok(result);
    }
}
