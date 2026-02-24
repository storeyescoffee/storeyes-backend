package io.storeyes.storeyes_coffee.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2-compliant error response DTO for authentication endpoints
 * Matches the format expected by OAuth2 clients: { "error": "...", "error_description": "..." }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthErrorResponse {
    
    private String error;
    private String error_description;
    
    public AuthErrorResponse(String error) {
        this.error = error;
        this.error_description = null;
    }
}
