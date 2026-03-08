package io.storeyes.storeyes_coffee.firebasetoken.services;

import io.storeyes.storeyes_coffee.auth.entities.UserInfo;
import io.storeyes.storeyes_coffee.auth.repositories.UserInfoRepository;
import io.storeyes.storeyes_coffee.firebasetoken.dto.CreateFirebaseTokenRequest;
import io.storeyes.storeyes_coffee.firebasetoken.entities.FirebaseToken;
import io.storeyes.storeyes_coffee.firebasetoken.repositories.FirebaseTokenRepository;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FirebaseTokenService {

    private final FirebaseTokenRepository firebaseTokenRepository;
    private final UserInfoRepository userInfoRepository;
    private final StoreService storeService;

    /**
     * Inserts a Firebase token for the authenticated user and their store.
     * userId and store are derived from the Keycloak JWT token.
     * Always creates a new record with a generated sessionId and returns that sessionId.
     */
    @Transactional
    public String upsertToken(CreateFirebaseTokenRequest request) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + userId));

        Long storeId = CurrentStoreContext.getCurrentStoreId();
        if (storeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store context not found for current user");
        }
        Store store = storeService.getStoreEntityById(storeId);

        String sessionId = UUID.randomUUID().toString();

        FirebaseToken newToken = FirebaseToken.builder()
                .user(user)
                .store(store)
                .sessionId(sessionId)
                .token(request.getToken())
                .platform(request.getPlatform())
                .build();

        firebaseTokenRepository.save(newToken);
        return sessionId;
    }

    /**
     * Revokes (deletes) a Firebase token by sessionId for the current user.
     */
    @Transactional
    public void revokeBySessionId(String sessionId) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        FirebaseToken token = firebaseTokenRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Firebase token not found for sessionId: " + sessionId));

        if (token.getUser() == null || token.getUser().getId() == null ||
                !token.getUser().getId().equals(userId)) {
            // Hide existence details if token doesn't belong to current user
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Firebase token not found for sessionId: " + sessionId);
        }

        firebaseTokenRepository.delete(token);
    }
}
