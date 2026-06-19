package io.storeyes.storeyes_coffee.notification.services;

import io.storeyes.storeyes_coffee.firebase.repositories.FirebaseToken2Repository;
import io.storeyes.storeyes_coffee.notification.dto.SendNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FirebaseToken2Repository firebaseToken2Repository;
    private final Optional<FcmNotificationService> fcmNotificationService;

    public int send(SendNotificationRequest request) {
        if (fcmNotificationService.isEmpty()) {
            log.debug("FCM is disabled — skipping notification for storeId={}", request.getStoreId());
            return 0;
        }

        List<String> tokens = resolveTokens(request.getStoreId(), request.getRoles());

        if (tokens.isEmpty()) {
            log.debug("No active tokens found for storeId={} roles={}", request.getStoreId(), request.getRoles());
            return 0;
        }

        log.debug("Sending notification to {} token(s) — storeId={} roles={}",
                tokens.size(), request.getStoreId(), request.getRoles());

        return fcmNotificationService.get().sendToTokens(tokens, request.getTitle(), request.getBody(), Map.of());
    }

    private List<String> resolveTokens(Long storeId, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return firebaseToken2Repository.findActiveTokensByStoreId(storeId);
        }
        return firebaseToken2Repository.findActiveTokensByStoreIdAndRoles(storeId, roles);
    }
}
