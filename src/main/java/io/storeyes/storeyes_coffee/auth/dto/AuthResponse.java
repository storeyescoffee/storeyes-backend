package io.storeyes.storeyes_coffee.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication endpoints (login, refresh)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    
    private String accessToken;
    private String refreshToken;
    private Long expiresIn; // Expiration time in seconds
    private String tokenType; // Usually "Bearer"
    
    /** Store code from the user's role mapping. Null if user has no store. */
    private String storeCode;
    /** Role name from the user's store role mapping. Null if user has no store. */
    private String role;
}

