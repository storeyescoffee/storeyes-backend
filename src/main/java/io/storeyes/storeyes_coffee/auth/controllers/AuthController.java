package io.storeyes.storeyes_coffee.auth.controllers;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.storeyes.storeyes_coffee.auth.dto.AuthErrorResponse;
import io.storeyes.storeyes_coffee.auth.dto.AuthResponse;
import io.storeyes.storeyes_coffee.auth.dto.LoginRequest;
import io.storeyes.storeyes_coffee.auth.dto.LogoutRequest;
import io.storeyes.storeyes_coffee.auth.dto.RefreshTokenRequest;
import io.storeyes.storeyes_coffee.auth.dto.UpdateProfileRequest;
import io.storeyes.storeyes_coffee.auth.dto.UserInfoDTO;
import io.storeyes.storeyes_coffee.auth.exceptions.TokenRefreshException;
import io.storeyes.storeyes_coffee.auth.services.AuthService;
import org.springframework.web.client.HttpClientErrorException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                AuthErrorResponse errorResponse = new AuthErrorResponse(
                    "invalid_grant",
                    "Invalid username or password"
                );
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse);
            }
            
            AuthErrorResponse errorResponse = new AuthErrorResponse(
                "server_error",
                "Authentication failed: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
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
     * Response (success):
     * {
     *   "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
     *   "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI...",
     *   "expiresIn": 300,
     *   "tokenType": "Bearer"
     * }
     * 
     * Response (error):
     * {
     *   "error": "invalid_grant",
     *   "error_description": "Token is not active"
     * }
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            log.debug("Token refresh requested");
            
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            
            log.debug("Token refresh successful");
            return ResponseEntity.ok(response);
            
        } catch (TokenRefreshException e) {
            log.warn("Token refresh failed: {} - {}", e.getError(), e.getErrorDescription());
            
            // Determine HTTP status code based on error type
            HttpStatus status;
            if ("invalid_grant".equals(e.getError())) {
                status = HttpStatus.UNAUTHORIZED;
            } else if ("invalid_request".equals(e.getError())) {
                status = HttpStatus.BAD_REQUEST;
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            
            AuthErrorResponse errorResponse = new AuthErrorResponse(
                e.getError(),
                e.getErrorDescription()
            );
            
            return ResponseEntity.status(status).body(errorResponse);
            
        } catch (RuntimeException e) {
            log.warn("Token refresh failed with unexpected error: {}", e.getMessage());
            
            // Fallback for any other runtime exceptions
            AuthErrorResponse errorResponse = new AuthErrorResponse(
                "server_error",
                "Token refresh failed: " + e.getMessage()
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
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

    private static final Pattern KC_ERROR_DESC =
            Pattern.compile("\"errorMessage\"\\s*:\\s*\"([^\"]*)\"");

    /** Best-effort parse of Keycloak Admin API JSON error body for a client-visible hint. */
    private static String extractKeycloakErrorMessage(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Matcher m = KC_ERROR_DESC.matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }

    /**
     * Update current user's profile (email, username, first name, last name)
     *
     * PUT /auth/me
     *
     * Requires: Authentication (JWT token in Authorization header)
     *
     * Request body:
     * {
     *   "email": "user@example.com",
     *   "username": "johndoe",
     *   "firstName": "John",
     *   "lastName": "Doe"
     * }
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateCurrentUser(@Valid @RequestBody UpdateProfileRequest request) {
        try {
            UserInfoDTO updated = authService.updateUserInfo(request);
            return ResponseEntity.ok(updated);
        } catch (IllegalStateException e) {
            log.warn("Profile update refused: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AuthErrorResponse("not_configured", e.getMessage()));
        } catch (HttpClientErrorException e) {
            log.warn("Keycloak error during profile update: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            String hint = extractKeycloakErrorMessage(e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 409) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new AuthErrorResponse(
                                "conflict", hint != null ? hint : "Email or username is already in use"));
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new AuthErrorResponse(
                            "keycloak_error", hint != null ? hint : "Could not update profile"));
        } catch (RuntimeException e) {
            log.warn("Failed to update user info: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
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

