package io.storeyes.storeyes_coffee.auth.services;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.storeyes.storeyes_coffee.auth.dto.AuthResponse;
import io.storeyes.storeyes_coffee.auth.dto.UserInfoDTO;
import io.storeyes.storeyes_coffee.auth.entities.UserInfo;
import io.storeyes.storeyes_coffee.auth.exceptions.TokenRefreshException;
import io.storeyes.storeyes_coffee.auth.repositories.UserInfoRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Service to handle authentication with Keycloak as a proxy
 * This service acts as an intermediary between the mobile app and Keycloak,
 * allowing the app to use HTTPS while Keycloak may only be available over HTTP
 */
@Service
@Slf4j
public class AuthService {

    private final RestTemplate restTemplate;
    private final UserInfoRepository userInfoRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String keycloakIssuerUri;
    
    /**
     * Client ID for Keycloak authentication
     * CRITICAL: This must match the client_id used during login.
     * The same client_id must be used for both login and refresh token operations.
     * Configured via: spring.security.oauth2.resourceserver.jwt.audience
     * Default: storeyes-mobile
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.audience}")
    private String clientId;
    
    public AuthService(RestTemplate restTemplate, UserInfoRepository userInfoRepository) {
        this.restTemplate = restTemplate;
        this.userInfoRepository = userInfoRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Authenticate user with Keycloak using username/email and password
     * 
     * @param username User's username or email address (Keycloak accepts both)
     * @param password User's password
     * @return AuthResponse containing access token, refresh token, and expiration info
     * @throws HttpClientErrorException if authentication fails
     */
    public AuthResponse login(String username, String password) {
        String tokenEndpoint = keycloakIssuerUri + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("username", username);
        body.add("password", password);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenEndpoint, 
                request, 
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Empty response from Keycloak");
            }
            
            return AuthResponse.builder()
                .accessToken((String) responseBody.get("access_token"))
                .refreshToken((String) responseBody.get("refresh_token"))
                .expiresIn(getExpiresIn(responseBody))
                .tokenType((String) responseBody.getOrDefault("token_type", "Bearer"))
                .build();
                
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new RuntimeException("Invalid username or password", e);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Authentication failed: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Refresh access token using refresh token
     * 
     * @param refreshToken The refresh token to use
     * @return AuthResponse containing new access token, refresh token, and expiration info
     * @throws TokenRefreshException if token refresh fails with OAuth2-compliant error information
     */
    public AuthResponse refreshToken(String refreshToken) {
        // Validate inputs
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new TokenRefreshException("invalid_request", "Refresh token is required");
        }
        
        if (clientId == null || clientId.isEmpty()) {
            throw new TokenRefreshException("server_error", "Client ID is not configured");
        }
        
        String tokenEndpoint = keycloakIssuerUri + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // Build request body with all required parameters
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId); // CRITICAL: Must match the client used for login
        body.add("refresh_token", refreshToken);
        
        log.debug("Refreshing token with client_id: {}", clientId);
        log.debug("Token endpoint: {}", tokenEndpoint);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenEndpoint, 
                request, 
                Map.class
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new TokenRefreshException("server_error", "Empty response from Keycloak");
            }
            
            // Ensure refresh token is present (required for token rotation)
            String newRefreshToken = (String) responseBody.get("refresh_token");
            if (newRefreshToken == null || newRefreshToken.isEmpty()) {
                // If Keycloak didn't return a new refresh token, use the old one
                // This handles cases where token rotation is disabled
                newRefreshToken = refreshToken;
            }
            
            return AuthResponse.builder()
                .accessToken((String) responseBody.get("access_token"))
                .refreshToken(newRefreshToken)
                .expiresIn(getExpiresIn(responseBody))
                .tokenType((String) responseBody.getOrDefault("token_type", "Bearer"))
                .build();
                
        } catch (HttpClientErrorException e) {
            // Log the error for debugging
            log.warn("Keycloak token refresh failed. Status: {}, Response: {}, Client ID: {}", 
                e.getStatusCode(), e.getResponseBodyAsString(), clientId);
            
            // Parse Keycloak error response to extract OAuth2 error information
            String error = "invalid_grant";
            String errorDescription = "Token refresh failed";
            
            try {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && !responseBody.isEmpty()) {
                    // Try to parse as JSON
                    Map<String, Object> errorMap = objectMapper.readValue(responseBody, Map.class);
                    error = (String) errorMap.getOrDefault("error", error);
                    errorDescription = (String) errorMap.getOrDefault("error_description", errorDescription);
                    
                    log.debug("Parsed Keycloak error: {} - {}", error, errorDescription);
                }
            } catch (Exception parseException) {
                // If parsing fails, try to extract error from response body string
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null) {
                    // Check for specific error patterns
                    String lowerResponseBody = responseBody.toLowerCase();
                    
                    if (lowerResponseBody.contains("session doesn't have required client") ||
                        lowerResponseBody.contains("session does not have required client")) {
                        error = "invalid_request";
                        errorDescription = "Session doesn't have required client. Ensure client_id matches the client used for login.";
                        log.error("Client ID mismatch detected! Used client_id: {}. This usually means the refresh token was issued for a different client.", clientId);
                    } else if (lowerResponseBody.contains("token is not active") || 
                               lowerResponseBody.contains("invalid_grant")) {
                        error = "invalid_grant";
                        errorDescription = "Token is not active";
                    } else if (lowerResponseBody.contains("invalid client")) {
                        error = "invalid_client";
                        errorDescription = "Invalid client ID: " + clientId;
                    } else {
                        errorDescription = responseBody;
                    }
                }
            }
            
            // Determine appropriate error code based on HTTP status and error message
            // Note: "Token is not active" should be treated as invalid_grant (401) even if Keycloak returns 400
            boolean isTokenNotActive = errorDescription != null && 
                errorDescription.toLowerCase().contains("token is not active");
            
            if (isTokenNotActive) {
                // "Token is not active" means the refresh token is invalid/expired/already used
                // This should be treated as invalid_grant (401 Unauthorized), not invalid_request
                log.debug("Detected 'Token is not active' error - mapping to invalid_grant (401)");
                error = "invalid_grant";
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                if (!"invalid_request".equals(error) && !"invalid_client".equals(error)) {
                    error = "invalid_grant";
                    if (errorDescription == null || errorDescription.equals("Token refresh failed")) {
                        errorDescription = "Invalid or expired refresh token";
                    }
                }
            } else if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                // For 400 errors, keep the parsed error unless it's a token validity issue
                // "Session doesn't have required client" and "Invalid client" should remain as-is
                if (!"invalid_grant".equals(error) && !"invalid_request".equals(error) && !"invalid_client".equals(error)) {
                    error = "invalid_request";
                }
            } else {
                error = "server_error";
            }
            
            log.debug("Final error mapping: error={}, errorDescription={}, httpStatus={}", 
                error, errorDescription, e.getStatusCode());
            
            throw new TokenRefreshException(error, errorDescription, e);
        }
    }

    /**
     * Logout user by revoking refresh token in Keycloak
     * 
     * @param refreshToken The refresh token to revoke
     */
    public void logout(String refreshToken) {
        String logoutEndpoint = keycloakIssuerUri + "/protocol/openid-connect/logout";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("refresh_token", refreshToken);
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        
        try {
            restTemplate.postForEntity(logoutEndpoint, request, String.class);
        } catch (HttpClientErrorException e) {
            // Log the error but don't fail logout if Keycloak revocation fails
            // The token will expire anyway, and the client will clear local storage
            // This is a best-effort revocation
            System.err.println("Failed to revoke token in Keycloak: " + e.getResponseBodyAsString());
        }
    }

    /**
     * Get current user information from JWT token and database
     * Returns user info from database if available, otherwise from JWT token
     * 
     * @return UserInfoDTO containing user information
     * @throws RuntimeException if user is not authenticated
     */
    public UserInfoDTO getUserInfo() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        
        // Try to get user info from database
        UserInfo userInfo = userInfoRepository.findById(userId).orElse(null);
        
        // Get information from JWT token
        String email = KeycloakTokenUtils.getEmail();
        String firstName = KeycloakTokenUtils.getGivenName();
        String lastName = KeycloakTokenUtils.getFamilyName();
        String preferredUsername = KeycloakTokenUtils.getPreferredUsername();
        
        // Use database values if available, otherwise fall back to JWT token values
        UserInfoDTO.UserInfoDTOBuilder builder = UserInfoDTO.builder()
                .id(userId)
                .preferredUsername(preferredUsername);
        
        if (userInfo != null) {
            // Use database values
            builder.email(userInfo.getEmail())
                   .firstName(userInfo.getFirstName())
                   .lastName(userInfo.getLastName());
        } else {
            // Use JWT token values
            builder.email(email)
                   .firstName(firstName)
                   .lastName(lastName);
        }
        
        return builder.build();
    }

    /**
     * Extract expires_in from response, handling both Integer and Long types
     */
    private Long getExpiresIn(Map<String, Object> responseBody) {
        Object expiresIn = responseBody.get("expires_in");
        if (expiresIn instanceof Integer) {
            return ((Integer) expiresIn).longValue();
        } else if (expiresIn instanceof Long) {
            return (Long) expiresIn;
        } else if (expiresIn instanceof Number) {
            return ((Number) expiresIn).longValue();
        }
        // Default to 5 minutes if not provided
        return 300L;
    }
}

