package io.storeyes.storeyes_coffee.alerts.auth.controllers;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.storeyes.storeyes_coffee.alerts.auth.dto.AuthResponse;
import io.storeyes.storeyes_coffee.alerts.auth.dto.LoginRequest;
import io.storeyes.storeyes_coffee.alerts.auth.dto.LogoutRequest;
import io.storeyes.storeyes_coffee.alerts.auth.dto.RefreshTokenRequest;
import io.storeyes.storeyes_coffee.alerts.auth.dto.UserInfoDTO;
import io.storeyes.storeyes_coffee.alerts.auth.services.AuthService;

/**
 * Authentication controller providing proxy endpoints for Keycloak authentication
 * These endpoints allow mobile apps to authenticate via HTTPS backend instead of
 * directly communicating with Keycloak (which may only be available over HTTP)
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login endpoint - exchanges username/email and password for tokens
     * 
     * POST /auth/login
     * 
     * The username field accepts both username and email address (Keycloak supports both)
     * 
     * Request body:
     * {
     *   "username": "user@example.com",  // Can be username or email
     *   "password": "password123"
     * }
     * 
     * Response:
     * {
     *   "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
     *   "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
     *   "expiresIn": 300,
     *   "tokenType": "Bearer"
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Never log passwords in production
            log.info("Login attempt for user: {}", request.getUsername());
            
            AuthResponse response = authService.login(
                request.getUsername(), 
                request.getPassword()
            );
            
            log.info("Login successful for user: {}", request.getUsername());
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            log.warn("Login failed for user: {} - {}", request.getUsername(), e.getMessage());
            
            // Map different error types to appropriate HTTP status codes
            if (e.getMessage() != null && e.getMessage().contains("Invalid username or password")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid username or password"));
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Token refresh endpoint - exchanges refresh token for new access token
     * 
     * POST /auth/refresh
     * 
     * Request body:
     * {
     *   "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI..."
     * }
     * 
     * Response:
     * {
     *   "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
     *   "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
     *   "expiresIn": 300,
     *   "tokenType": "Bearer"
     * }
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            log.debug("Token refresh requested");
            
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            
            log.debug("Token refresh successful");
            return ResponseEntity.ok(response);
            
        } catch (RuntimeException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            
            if (e.getMessage() != null && e.getMessage().contains("Invalid or expired refresh token")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid or expired refresh token"));
            }
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Token refresh failed: " + e.getMessage()));
        }
    }

    /**
     * Logout endpoint - revokes refresh token in Keycloak
     * 
     * POST /auth/logout
     * 
     * Request body:
     * {
     *   "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI..."
     * }
     * 
     * Response:
     * {
     *   "message": "Logged out successfully"
     * }
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody LogoutRequest request) {
        try {
            log.debug("Logout requested");
            
            authService.logout(request.getRefreshToken());
            
            log.debug("Logout successful");
            return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
            
        } catch (Exception e) {
            // Even if Keycloak revocation fails, we consider logout successful
            // from the client's perspective (they should clear local storage anyway)
            log.warn("Logout completed with warning: {}", e.getMessage());
            return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
        }
    }

    /**
     * Get current user information endpoint
     * 
     * GET /auth/me
     * 
     * Requires: Authentication (JWT token in Authorization header)
     * 
     * Response:
     * {
     *   "id": "user-uuid",
     *   "email": "user@example.com",
     *   "firstName": "John",
     *   "lastName": "Doe",
     *   "preferredUsername": "johndoe"
     * }
     */
    @GetMapping("/me")
    public ResponseEntity<UserInfoDTO> getCurrentUser() {
        try {
            log.debug("User info requested");
            
            UserInfoDTO userInfo = authService.getUserInfo();
            
            log.debug("User info retrieved for user: {}", userInfo.getEmail());
            return ResponseEntity.ok(userInfo);
            
        } catch (RuntimeException e) {
            log.warn("Failed to get user info: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    /**
     * Simple error response DTO
     */
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() {
            return error;
        }
    }

    /**
     * Simple message response DTO
     */
    private static class MessageResponse {
        private String message;
        
        public MessageResponse(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }
}

