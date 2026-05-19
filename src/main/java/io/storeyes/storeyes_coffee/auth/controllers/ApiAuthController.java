package io.storeyes.storeyes_coffee.auth.controllers;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.storeyes.storeyes_coffee.auth.dto.AuthErrorResponse;
import io.storeyes.storeyes_coffee.auth.dto.LoginRequest;
import io.storeyes.storeyes_coffee.auth.dto.MultiStoreAuthResponse;
import io.storeyes.storeyes_coffee.auth.services.AuthService;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class ApiAuthController {

    private final AuthService authService;

    public ApiAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            log.info("Multi-store login attempt for user: {}", request.getUsername());

            MultiStoreAuthResponse response = authService.loginMultiStore(
                    request.getUsername(),
                    request.getPassword()
            );

            log.info("Multi-store login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.warn("Multi-store login failed for user: {} - {}", request.getUsername(), e.getMessage());

            if (e.getMessage() != null && e.getMessage().contains("Invalid username or password")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new AuthErrorResponse("invalid_grant", "Invalid username or password"));
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthErrorResponse("server_error", "Authentication failed: " + e.getMessage()));
        }
    }
}
