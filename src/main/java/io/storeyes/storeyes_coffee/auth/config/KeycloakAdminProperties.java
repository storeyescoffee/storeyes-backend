package io.storeyes.storeyes_coffee.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Keycloak Admin REST API used to set user passwords (e.g. password reset complete).
 * <p>
 * Supports {@code client_credentials} (service account) or, when {@link #adminUsername} and
 * {@link #adminPassword} are set, OAuth2 {@code password} grant (requires Direct Access Grants
 * enabled on the client in Keycloak).
 */
@Data
@ConfigurationProperties(prefix = "app.keycloak-admin")
public class KeycloakAdminProperties {

    /**
     * When false or token credentials are missing, password reset "complete" returns 503.
     */
    private boolean enabled = false;

    /**
     * When true (default), password grant is considered misconfigured without a client secret.
     * Confidential clients always need {@code client_secret}; set to false only for public clients
     * with Direct Access Grants enabled.
     */
    private boolean requireClientSecretForPasswordGrant = true;

    /** Keycloak base URL without path (no trailing slash). */
    private String authServerUrl = "";

    /**
     * Realm for the token endpoint when using {@code client_credentials} (service account client usually
     * lives in this realm).
     */
    private String realm = "";

    /**
     * When using password grant with admin user/password: realm for the token endpoint only.
     * Use {@code master} when {@link #adminUsername} is a Keycloak master-realm admin (e.g. {@code admin}
     * or a user created in {@code master}). If empty, {@link #realm} then JWT issuer realm are used.
     */
    private String passwordGrantRealm = "";

    /**
     * Client id for password-grant token only (e.g. {@code admin-cli} in {@code master}).
     * If empty, {@link #resource} / legacy {@link #clientId} is used (same as mobile / service client).
     */
    private String passwordGrantClientId = "";

    /** Client secret for password-grant only; if empty and {@link #passwordGrantClientId} is set, no secret is sent. */
    private String passwordGrantClientSecret = "";

    /** Realm whose users are updated via Admin API ({@code .../admin/realms/{this}/users/...}). */
    private String userManagementRealm = "";

    /** OpenID client id used for token requests (same as Keycloak "Client ID"). */
    private String resource = "";

    private Credentials credentials = new Credentials();

    /**
     * If both set with {@link #adminPassword}, token is obtained with {@code grant_type=password}.
     * Otherwise {@code grant_type=client_credentials} is used (requires non-empty client secret).
     */
    private String adminUsername = "";

    private String adminPassword = "";

    // --- Legacy property names (still bound from older yaml / KEYCLOAK_ADMIN_* env) ---

    private String serverUrl = "";
    private String authRealm = "";
    private String usersRealm = "";
    private String clientId = "";
    private String clientSecret = "";

    @Data
    public static class Credentials {
        private String secret = "";
    }

    public String effectiveAuthServerUrl() {
        if (StringUtils.hasText(authServerUrl)) {
            return trimTrailingSlash(authServerUrl.trim());
        }
        if (StringUtils.hasText(serverUrl)) {
            return trimTrailingSlash(serverUrl.trim());
        }
        return "";
    }

    public String effectiveTokenRealm() {
        if (StringUtils.hasText(realm)) {
            return realm.trim();
        }
        if (StringUtils.hasText(authRealm)) {
            return authRealm.trim();
        }
        return "";
    }

    public String effectiveUserManagementRealm() {
        if (StringUtils.hasText(userManagementRealm)) {
            return userManagementRealm.trim();
        }
        if (StringUtils.hasText(usersRealm)) {
            return usersRealm.trim();
        }
        return "";
    }

    public String effectiveClientId() {
        if (StringUtils.hasText(resource)) {
            return resource.trim();
        }
        if (StringUtils.hasText(clientId)) {
            return clientId.trim();
        }
        return "";
    }

    public String effectiveClientSecret() {
        String nested = credentials != null && credentials.getSecret() != null ? credentials.getSecret() : "";
        if (StringUtils.hasText(nested)) {
            return nested.trim();
        }
        if (StringUtils.hasText(clientSecret)) {
            return clientSecret.trim();
        }
        return "";
    }

    /** True when password-grant uses its own realm and/or client (e.g. master + admin-cli). */
    public boolean usesDedicatedPasswordGrantClient() {
        return StringUtils.hasText(passwordGrantRealm) || StringUtils.hasText(passwordGrantClientId);
    }

    /**
     * Realm segment for password-grant token URL: {@link #passwordGrantRealm}, else if client is
     * {@code admin-cli} then {@code master} (Keycloak default), else {@link #realm} / legacy auth-realm,
     * else empty (caller falls back to JWT issuer realm).
     */
    public String effectivePasswordGrantTokenRealm() {
        if (StringUtils.hasText(passwordGrantRealm)) {
            return passwordGrantRealm.trim();
        }
        if (StringUtils.hasText(passwordGrantClientId)
                && "admin-cli".equalsIgnoreCase(passwordGrantClientId.trim())) {
            return "master";
        }
        return effectiveTokenRealm();
    }

    public String effectivePasswordGrantClientId() {
        if (StringUtils.hasText(passwordGrantClientId)) {
            return passwordGrantClientId.trim();
        }
        return effectiveClientId();
    }

    /**
     * Secret sent on password grant: if dedicated PG client is configured, only
     * {@link #passwordGrantClientSecret} is used (empty = public client such as {@code admin-cli}).
     * Otherwise the main {@link #effectiveClientSecret()} is used.
     */
    public String effectivePasswordGrantClientSecret() {
        if (usesDedicatedPasswordGrantClient()) {
            return StringUtils.hasText(passwordGrantClientSecret) ? passwordGrantClientSecret.trim() : "";
        }
        return effectiveClientSecret();
    }

    public boolean usesPasswordGrant() {
        return StringUtils.hasText(adminUsername) && StringUtils.hasText(adminPassword);
    }

    /**
     * Empty if configuration is sufficient to attempt token acquisition for password reset complete.
     */
    public Optional<String> describeNotReady() {
        if (!enabled) {
            return Optional.of("app.keycloak-admin.enabled is false (set KEYCLOAK_ADMIN_ENABLED=true).");
        }
        if (!StringUtils.hasText(effectiveClientId())) {
            return Optional.of(
                    "Missing OpenID client id: set KEYCLOAK_CLIENT_ID or app.keycloak-admin.resource.");
        }
        if (usesPasswordGrant()) {
            if (!usesDedicatedPasswordGrantClient()) {
                // KeycloakPasswordAdminService uses fixed master + admin-cli (no app-realm client secret).
                return Optional.empty();
            }
            String pgSecret = effectivePasswordGrantClientSecret();
            boolean secretRequired =
                    requireClientSecretForPasswordGrant && !StringUtils.hasText(pgSecret);
            if (secretRequired) {
                return Optional.of(
                        "Password grant with dedicated client is active but client_secret is empty. "
                                + "Set app.keycloak-admin.password-grant-client-secret or "
                                + "KEYCLOAK_ADMIN_PASSWORD_GRANT_CLIENT_SECRET, or set "
                                + "require-client-secret-for-password-grant=false for a public token client.");
            }
            return Optional.empty();
        }
        if (!StringUtils.hasText(effectiveClientSecret())) {
            return Optional.of(
                    "client_credentials requires client_secret: set KEYCLOAK_CLIENT_SECRET or "
                            + "KEYCLOAK_ADMIN_CLIENT_SECRET.");
        }
        return Optional.empty();
    }

    public boolean isPasswordResetAdminReady() {
        return describeNotReady().isEmpty();
    }

    private static String trimTrailingSlash(String u) {
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
