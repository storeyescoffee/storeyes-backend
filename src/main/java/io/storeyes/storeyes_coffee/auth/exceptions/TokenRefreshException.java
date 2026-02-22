package io.storeyes.storeyes_coffee.auth.exceptions;

/**
 * Exception thrown when token refresh fails
 * Carries OAuth2-compliant error information
 */
public class TokenRefreshException extends RuntimeException {
    
    private final String error;
    private final String errorDescription;
    
    public TokenRefreshException(String error, String errorDescription) {
        super(errorDescription != null ? errorDescription : error);
        this.error = error;
        this.errorDescription = errorDescription;
    }
    
    public TokenRefreshException(String error, String errorDescription, Throwable cause) {
        super(errorDescription != null ? errorDescription : error, cause);
        this.error = error;
        this.errorDescription = errorDescription;
    }
    
    public String getError() {
        return error;
    }
    
    public String getErrorDescription() {
        return errorDescription;
    }
    
    /**
     * Check if this is a "Token is not active" error
     */
    public boolean isTokenNotActive() {
        return "invalid_grant".equals(error) && 
               errorDescription != null && 
               errorDescription.toLowerCase().contains("token is not active");
    }
}
