package io.storeyes.storeyes_coffee.auth.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.storeyes.storeyes_coffee.auth.config.KeycloakAdminProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Sets a user's password via Keycloak Admin REST API.
 * Token: OAuth2 {@code client_credentials} or {@code password} when admin username/password are set.
 * Configure {@link KeycloakAdminProperties} under {@code app.keycloak-admin.*}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakPasswordAdminService {

    /**
     * Password grant for Keycloak Admin API: admin users live in {@code master}; token URL is always
     * {@code .../realms/master/protocol/openid-connect/token} (not the JWT issuer realm).
     */
    private static final String PASSWORD_GRANT_TOKEN_REALM = "master";

    /** Default OpenID client in {@value #PASSWORD_GRANT_TOKEN_REALM} for direct-access password grant. */
    private static final String PASSWORD_GRANT_CLIENT_ID_DEFAULT = "admin-cli";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final KeycloakAdminProperties adminProperties;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String keycloakIssuerUri;

    public boolean isEnabled() {
        return adminProperties.isPasswordResetAdminReady();
    }

    /** Non-empty when {@link #isEnabled()} is false — for API 503 messages. */
    public Optional<String> notReadyReason() {
        return adminProperties.describeNotReady();
    }

    public void resetPassword(String keycloakUserId, String newPassword) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    notReadyReason().orElse("Keycloak admin API is not configured"));
        }
        String base = keycloakServerBase();
        String usersRealm = resolveUsersRealm();
        String token = fetchAccessToken(base);
        String url = base + "/admin/realms/" + usersRealm + "/users/" + keycloakUserId + "/reset-password";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(resetPasswordJson(newPassword), headers);
        try {
            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
        } catch (HttpClientErrorException e) {
            log.warn("Keycloak reset-password failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    private String resetPasswordJson(String newPassword) {
        try {
            return objectMapper.writeValueAsString(
                    Map.of("type", "password", "value", newPassword, "temporary", false));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize reset-password body", e);
        }
    }

    private String fetchAccessToken(String base) {
        boolean passwordGrant = adminProperties.usesPasswordGrant();
        String tokenRealm = passwordGrant ? resolvePasswordGrantTokenRealm() : resolveTokenRealm();
        String tokenUrl = base + "/realms/" + tokenRealm + "/protocol/openid-connect/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        String clientId =
                passwordGrant ? resolvePasswordGrantClientId() : adminProperties.effectiveClientId();
        String clientSecret =
                passwordGrant ? resolvePasswordGrantClientSecret() : adminProperties.effectiveClientSecret();
        boolean secretSent = StringUtils.hasText(clientSecret);
        if (passwordGrant) {
            form.add("grant_type", "password");
            form.add("client_id", clientId);
            if (secretSent) {
                form.add("client_secret", clientSecret);
            }
            form.add("username", adminProperties.getAdminUsername().trim());
            form.add("password", adminProperties.getAdminPassword());
            form.add("scope", "openid");
        } else {
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("scope", "openid");
        }
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = response.getBody();
            if (body == null || body.get("access_token") == null) {
                throw new IllegalStateException("Keycloak admin token response missing access_token");
            }
            return (String) body.get("access_token");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String rawBody = e.getResponseBodyAsString();
            if (rawBody == null || rawBody.isBlank()) {
                byte[] bytes = e.getResponseBodyAsByteArray();
                if (bytes != null && bytes.length > 0) {
                    rawBody = new String(bytes, StandardCharsets.UTF_8);
                }
            }
            String wwwAuth = e.getResponseHeaders() != null
                    ? String.valueOf(e.getResponseHeaders().get("WWW-Authenticate"))
                    : "";
            log.warn(
                    "Keycloak TOKEN request failed (password reset uses this before Admin API): url={} tokenRealm={} "
                            + "userManagementRealm={} client_id={} grant_type={} client_secret_sent={} status={} "
                            + "wwwAuthenticate={} body={}",
                    tokenUrl,
                    tokenRealm,
                    resolveUsersRealm(),
                    clientId,
                    passwordGrant ? "password" : "client_credentials",
                    secretSent,
                    e.getStatusCode(),
                    wwwAuth,
                    rawBody);
            if (e.getStatusCode().value() == 401 && passwordGrant && !secretSent) {
                log.error(
                        "Keycloak returned 401 on password grant (realm={}, client={}). "
                                + "Check admin username/password, Direct Access Grants on the client, or set "
                                + "app.keycloak-admin.password-grant-client-id / password-grant-client-secret for a dedicated client.",
                        PASSWORD_GRANT_TOKEN_REALM,
                        clientId);
            }
            throw e;
        }
    }

    private String resolvePasswordGrantTokenRealm() {
        return PASSWORD_GRANT_TOKEN_REALM;
    }

    /**
     * Unless {@link KeycloakAdminProperties#usesDedicatedPasswordGrantClient()} is true, uses
     * {@value #PASSWORD_GRANT_CLIENT_ID_DEFAULT} in {@value #PASSWORD_GRANT_TOKEN_REALM}.
     */
    private String resolvePasswordGrantClientId() {
        if (adminProperties.usesDedicatedPasswordGrantClient()) {
            return adminProperties.effectivePasswordGrantClientId();
        }
        return PASSWORD_GRANT_CLIENT_ID_DEFAULT;
    }

    private String resolvePasswordGrantClientSecret() {
        if (adminProperties.usesDedicatedPasswordGrantClient()) {
            return adminProperties.effectivePasswordGrantClientSecret();
        }
        return "";
    }

    private String resolveTokenRealm() {
        String configured = adminProperties.effectiveTokenRealm();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return extractRealm(keycloakIssuerUri);
    }

    private String keycloakServerBase() {
        String fromProps = adminProperties.effectiveAuthServerUrl();
        if (StringUtils.hasText(fromProps)) {
            return fromProps;
        }
        int idx = keycloakIssuerUri.indexOf("/realms/");
        if (idx <= 0) {
            return keycloakIssuerUri.endsWith("/")
                    ? keycloakIssuerUri.substring(0, keycloakIssuerUri.length() - 1)
                    : keycloakIssuerUri;
        }
        return keycloakIssuerUri.substring(0, idx);
    }

    private String resolveUsersRealm() {
        String configured = adminProperties.effectiveUserManagementRealm();
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        return extractRealm(keycloakIssuerUri);
    }

    static String extractRealm(String issuerUri) {
        int i = issuerUri.indexOf("/realms/");
        if (i < 0) {
            throw new IllegalArgumentException("issuer-uri must contain /realms/{realm}");
        }
        String tail = issuerUri.substring(i + "/realms/".length());
        int slash = tail.indexOf('/');
        return slash > 0 ? tail.substring(0, slash) : tail;
    }
}
