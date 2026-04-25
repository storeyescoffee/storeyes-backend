package io.storeyes.storeyes_coffee.auth.controllers;

import io.storeyes.storeyes_coffee.auth.dto.AuthErrorResponse;
import io.storeyes.storeyes_coffee.auth.dto.PasswordResetAckResponse;
import io.storeyes.storeyes_coffee.auth.dto.PasswordResetCompleteBody;
import io.storeyes.storeyes_coffee.auth.dto.PasswordResetRequestBody;
import io.storeyes.storeyes_coffee.auth.dto.PasswordResetVerifyBody;
import io.storeyes.storeyes_coffee.auth.dto.PasswordResetVerifyResponse;
import io.storeyes.storeyes_coffee.auth.services.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/auth/password-reset")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private static final String ACK =
            "If an account exists for this email, a verification code was sent.";

    private final PasswordResetService passwordResetService;

    private static final Pattern KC_ERROR_DESC =
            Pattern.compile("\"error_description\"\\s*:\\s*\"([^\"]*)\"");

    /** Best-effort parse of Keycloak token / admin JSON error body for client-visible hint. */
    private static String extractKeycloakErrorDescription(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Matcher m = KC_ERROR_DESC.matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"") : null;
    }

    @PostMapping("/request")
    public ResponseEntity<PasswordResetAckResponse> request(@Valid @RequestBody PasswordResetRequestBody body) {
        passwordResetService.requestCode(body.getEmail());
        return ResponseEntity.ok(new PasswordResetAckResponse(ACK));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody PasswordResetVerifyBody body) {
        Optional<PasswordResetVerifyResponse> result = passwordResetService.verify(body.getEmail(), body.getCode());
        if (result.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new AuthErrorResponse("invalid_code", "Invalid or expired verification code"));
        }
        return ResponseEntity.ok(result.get());
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(@Valid @RequestBody PasswordResetCompleteBody body) {
        try {
            boolean ok = passwordResetService.complete(
                    body.getEmail(),
                    body.getCode(),
                    body.getNewPassword(),
                    body.getResetToken());
            if (!ok) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthErrorResponse(
                                "invalid_reset",
                                "Invalid code, reset token, or reset session has expired"));
            }
            return ResponseEntity.ok(new PasswordResetAckResponse("Password has been reset."));
        } catch (IllegalStateException e) {
            log.warn("Password reset complete refused: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AuthErrorResponse("not_configured", e.getMessage()));
        } catch (HttpClientErrorException e) {
            log.warn(
                    "Keycloak error during password reset: {} — {}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            String hint = extractKeycloakErrorDescription(e.getResponseBodyAsString());
            String description = hint != null ? hint : "Could not update password";
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new AuthErrorResponse("keycloak_error", description));
        }
    }
}
