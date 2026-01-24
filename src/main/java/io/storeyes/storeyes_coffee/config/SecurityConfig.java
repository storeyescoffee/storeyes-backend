package io.storeyes.storeyes_coffee.config;

import io.storeyes.storeyes_coffee.security.KeycloakJwtAuthenticationConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.audience}")
    private String audience;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Configure Spring Security with OAuth2 Resource Server (JWT validation)
     * The JWT decoder is automatically configured from application.yaml properties:
     * - spring.security.oauth2.resourceserver.jwt.issuer-uri
     * - spring.security.oauth2.resourceserver.jwt.audience
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless JWT-based authentication
            .csrf(AbstractHttpConfigurer::disable)
            
            // Enable CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure session management to be stateless
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure OAuth2 Resource Server (JWT validation)
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    // Use custom JWT decoder with audience validation
                    .decoder(jwtDecoder())
                    // Use custom converter to extract roles from Keycloak token
                    .jwtAuthenticationConverter(new KeycloakJwtAuthenticationConverter())
                )
                // Configure custom authentication entry point for JWT errors
                .authenticationEntryPoint((request, response, authException) -> {
                    // JWT validation errors - let GlobalExceptionHandler handle these
                    // by throwing the exception (it will be caught by the exception handler)
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    
                    String message = "Invalid or expired token";
                    if (authException.getMessage() != null) {
                        String exMessage = authException.getMessage();
                        if (exMessage.contains("expired")) {
                            message = "Token has expired";
                        } else if (exMessage.contains("invalid") || exMessage.contains("malformed")) {
                            message = "Invalid token";
                        } else if (exMessage.contains("audience")) {
                            message = "Token audience validation failed";
                        } else {
                            message = exMessage.replace("\"", "\\\"");
                        }
                    }
                    
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}"
                    );
                })
            )
            
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
                // Allow CORS preflight requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // User info endpoint requires authentication
                .requestMatchers("/auth/me").authenticated()
                
                // Public authentication endpoints - allow login, refresh, logout without authentication
                .requestMatchers("/auth/**").permitAll()
                
                .requestMatchers("/api/alerts/**").permitAll()
                
                // All other /api/** endpoints require authentication
                //.requestMatchers("/api/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Handle exceptions
            // Note: JWT validation errors are handled by the OAuth2 resource server's entry point above.
            // This entry point handles cases where no authentication was attempted (missing token).
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // Only handle cases where no Authorization header was provided
                    // JWT validation errors are handled by the OAuth2 resource server entry point
                    String authHeader = request.getHeader("Authorization");
                    
                    if (authHeader == null || authHeader.trim().isEmpty()) {
                        // No authentication attempt - truly missing authentication
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write(
                            "{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                        );
                    } else {
                        // There was an auth header but authentication failed
                        // This should have been handled by OAuth2 resource server entry point,
                        // but if it reaches here, provide a generic message
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        String message = authException.getMessage() != null 
                            ? authException.getMessage().replace("\"", "\\\"") 
                            : "Authentication failed";
                        response.getWriter().write(
                            "{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}"
                        );
                    }
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    // Access denied - user authenticated but lacks permission
                    // Let GlobalExceptionHandler handle this for consistent error format
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    String message = accessDeniedException.getMessage() != null 
                        ? accessDeniedException.getMessage().replace("\"", "\\\"") 
                        : "Access denied";
                    response.getWriter().write(
                        "{\"error\":\"Forbidden\",\"message\":\"" + message + "\"}"
                    );
                })
            );

        return http.build();
    }

    /**
     * JWT Decoder configured to validate tokens against Keycloak
     * Uses issuer URI for automatic OpenID Connect discovery and JWKS retrieval
     * Validates both issuer and audience claims
     * Includes clock skew tolerance to handle minor time differences between servers
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Use issuer location to automatically discover JWKS endpoint
        // This handles OpenID Connect discovery and retrieves keys from JWKS endpoint
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

        // Create validators manually to have full control over clock skew tolerance
        // 1. Timestamp validator with clock skew tolerance (60 seconds)
        //    This allows tokens to be accepted even if there's a small time difference
        //    between the server and Keycloak (up to 60 seconds)
        //    This helps with:
        //    - Clock synchronization issues between servers
        //    - Network latency
        //    - Token refresh timing
        OAuth2TokenValidator<Jwt> timestampValidator = new JwtTimestampValidator(
            Duration.ofSeconds(60) // Clock skew tolerance: 60 seconds
        );
        
        // 2. Issuer validator - validates that token was issued by the correct Keycloak realm
        OAuth2TokenValidator<Jwt> issuerValidator = new JwtClaimValidator<String>(
            JwtClaimNames.ISS,
            issuerUri::equals
        );
        
        // 3. Audience validator - validates that token is for the correct client
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);

        // Combine all validators
        // All validators must pass for the token to be accepted
        OAuth2TokenValidator<Jwt> combinedValidator = 
            new DelegatingOAuth2TokenValidator<>(timestampValidator, issuerValidator, audienceValidator);

        jwtDecoder.setJwtValidator(combinedValidator);

        return jwtDecoder;
    }

    /**
     * Custom validator to check audience claim in JWT token
     * In Keycloak, tokens from password grant flow have "account" as audience
     * and the client ID is in the "azp" (authorized party) claim.
     * This validator accepts both scenarios:
     * 1. Standard OAuth2: aud claim contains the expected audience
     * 2. Keycloak password grant: aud is "account" and azp matches the expected client ID
     */
    private static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String audience;

        public AudienceValidator(String audience) {
            this.audience = audience;
        }

        @Override
        public org.springframework.security.oauth2.core.OAuth2TokenValidatorResult validate(Jwt jwt) {
            List<String> audiences = jwt.getAudience();
            String azp = jwt.getClaimAsString("azp");
            
            // Check if audience claim contains the expected audience (standard OAuth2)
            if (audiences != null && audiences.contains(audience)) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            }
            
            // In Keycloak password grant flow, aud is "account" and client ID is in azp
            // Validate that azp (authorized party) matches the expected client ID
            if (audience.equals(azp)) {
                return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success();
            }
            
            return org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                new org.springframework.security.oauth2.core.OAuth2Error(
                    org.springframework.security.oauth2.core.OAuth2ErrorCodes.INVALID_TOKEN,
                    "The token does not contain the required audience: " + audience + " (aud: " + audiences + ", azp: " + azp + ")",
                    null
                )
            );
        }
    }

    /**
     * CORS Configuration
     * Allows requests from mobile applications and other clients
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow all origins (you can restrict this to specific domains in production)
        configuration.setAllowedOrigins(List.of("*"));
        
        // Allowed HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        
        // Allowed headers (including Authorization for JWT tokens)
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-Requested-With"));
        
        // Exposed headers
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        
        // Allow credentials (set to true if you need cookies/auth headers)
        configuration.setAllowCredentials(false);
        
        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}


