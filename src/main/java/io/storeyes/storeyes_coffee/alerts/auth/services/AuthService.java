package io.storeyes.storeyes_coffee.alerts.auth.services;

import io.storeyes.storeyes_coffee.alerts.auth.dto.AuthResponse;
import io.storeyes.storeyes_coffee.alerts.auth.dto.UserInfoDTO;
import io.storeyes.storeyes_coffee.alerts.auth.entities.UserInfo;
import io.storeyes.storeyes_coffee.alerts.auth.repositories.UserInfoRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
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
public class AuthService {

    private final RestTemplate restTemplate;
    private final UserInfoRepository userInfoRepository;
    
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String keycloakIssuerUri;
    
    @Value("${spring.security.oauth2.resourceserver.jwt.audience}")
    private String clientId;
    
    public AuthService(RestTemplate restTemplate, UserInfoRepository userInfoRepository) {
        this.restTemplate = restTemplate;
        this.userInfoRepository = userInfoRepository;
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
     * @throws HttpClientErrorException if token refresh fails
     */
    public AuthResponse refreshToken(String refreshToken) {
        String tokenEndpoint = keycloakIssuerUri + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("refresh_token", refreshToken);
        
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
            throw new RuntimeException("Invalid or expired refresh token", e);
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Token refresh failed: " + e.getResponseBodyAsString(), e);
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

