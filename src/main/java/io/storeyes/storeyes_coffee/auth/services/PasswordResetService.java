package io.storeyes.storeyes_coffee.auth.services;

import io.storeyes.storeyes_coffee.auth.dto.PasswordResetVerifyResponse;
import io.storeyes.storeyes_coffee.auth.entities.PasswordResetChallenge;
import io.storeyes.storeyes_coffee.auth.repositories.PasswordResetChallengeRepository;
import io.storeyes.storeyes_coffee.auth.repositories.UserInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetChallengeRepository challengeRepository;
    private final UserInfoRepository userInfoRepository;
    private final KeycloakPasswordAdminService keycloakPasswordAdminService;
    private final ObjectProvider<JavaMailSender> javaMailSender;

    @Value("${app.password-reset.pepper}")
    private String pepper;

    @Value("${app.password-reset.code-ttl-minutes:15}")
    private int codeTtlMinutes;

    @Value("${app.password-reset.reset-token-ttl-minutes:30}")
    private int resetTokenTtlMinutes;

    @Value("${app.password-reset.from-address:no-reply@localhost}")
    private String fromAddress;

    @Transactional
    public void requestCode(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return;
        }
        var userOpt = userInfoRepository.findByEmailIgnoreCase(normalized);
        if (userOpt.isEmpty()) {
            log.debug("Password reset request for unknown email (no user_info row)");
            return;
        }
        challengeRepository.deleteByEmailNormalizedAndConsumedAtIsNull(normalized);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        PasswordResetChallenge row = PasswordResetChallenge.builder()
                .emailNormalized(normalized)
                .codeHash(hashCode(normalized, code))
                .codeExpiresAt(now.plus(codeTtlMinutes, ChronoUnit.MINUTES))
                .createdAt(now)
                .build();
        challengeRepository.save(row);
        sendCodeEmail(userOpt.get().getEmail(), code);
    }

    @Transactional
    public Optional<PasswordResetVerifyResponse> verify(String email, String code) {
        String normalized = normalizeEmail(email);
        Optional<PasswordResetChallenge> opt = challengeRepository
                .findTopByEmailNormalizedAndConsumedAtIsNullOrderByCreatedAtDesc(normalized);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        PasswordResetChallenge c = opt.get();
        if (c.getConsumedAt() != null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(c.getCodeExpiresAt())) {
            return Optional.empty();
        }
        if (!constantTimeEquals(hashCode(normalized, code), c.getCodeHash())) {
            return Optional.empty();
        }
        if (c.getVerifiedAt() != null) {
            return Optional.empty();
        }
        String resetToken = UUID.randomUUID().toString();
        Instant now = Instant.now();
        c.setVerifiedAt(now);
        c.setResetTokenHash(hashResetToken(resetToken));
        c.setResetTokenExpiresAt(now.plus(resetTokenTtlMinutes, ChronoUnit.MINUTES));
        challengeRepository.save(c);
        return Optional.of(PasswordResetVerifyResponse.builder().resetToken(resetToken).build());
    }

    /**
     * @return true if password was updated
     */
    @Transactional
    public boolean complete(String email, String code, String newPassword, String resetToken) {
        if (!keycloakPasswordAdminService.isEnabled()) {
            throw new IllegalStateException(
                    keycloakPasswordAdminService
                            .notReadyReason()
                            .orElse("Keycloak admin API is not configured"));
        }
        String normalized = normalizeEmail(email);
        Optional<PasswordResetChallenge> opt = challengeRepository
                .findTopByEmailNormalizedAndConsumedAtIsNullOrderByCreatedAtDesc(normalized);
        if (opt.isEmpty()) {
            return false;
        }
        PasswordResetChallenge c = opt.get();
        if (c.getVerifiedAt() == null || c.getResetTokenExpiresAt() == null) {
            return false;
        }
        if (Instant.now().isAfter(c.getResetTokenExpiresAt())) {
            return false;
        }
        if (!constantTimeEquals(hashCode(normalized, code), c.getCodeHash())) {
            return false;
        }
        if (c.getResetTokenHash() != null
                && resetToken != null
                && !resetToken.isBlank()
                && !constantTimeEquals(hashResetToken(resetToken), c.getResetTokenHash())) {
            return false;
        }
        var user = userInfoRepository.findByEmailIgnoreCase(normalized);
        if (user.isEmpty()) {
            return false;
        }
        String keycloakUserId = user.get().getId();
        try {
            keycloakPasswordAdminService.resetPassword(keycloakUserId, newPassword);
        } catch (HttpClientErrorException e) {
            log.warn(
                    "Keycloak HTTP {} for user {} (token or reset-password): {}",
                    e.getStatusCode(),
                    keycloakUserId,
                    e.getResponseBodyAsString());
            throw e;
        }
        c.setConsumedAt(Instant.now());
        challengeRepository.save(c);
        return true;
    }

    private void sendCodeEmail(String toEmail, String code) {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            log.warn(
                    "Password reset for {} — email not sent (configure spring.mail.*). Code was persisted.",
                    toEmail);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(toEmail);
            msg.setSubject("Password reset verification code");
            msg.setText("Your verification code is: "
                    + code
                    + "\n\nIt expires in "
                    + codeTtlMinutes
                    + " minutes. If you did not request this, you can ignore this message.");
            sender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase();
    }

    private String hashCode(String emailNorm, String code) {
        String c = code == null ? "" : code.trim();
        return base64Sha256(pepper + "|code|" + emailNorm + "|" + c);
    }

    private String hashResetToken(String token) {
        return base64Sha256(pepper + "|rt|" + token);
    }

    private String base64Sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(x, y);
    }
}
