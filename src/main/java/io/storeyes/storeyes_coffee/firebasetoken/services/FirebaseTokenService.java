package io.storeyes.storeyes_coffee.firebasetoken.services;

import io.storeyes.storeyes_coffee.auth.entities.UserInfo;
import io.storeyes.storeyes_coffee.auth.repositories.UserInfoRepository;
import io.storeyes.storeyes_coffee.firebasetoken.dto.UpsertFirebaseTokenRequest;
import io.storeyes.storeyes_coffee.firebasetoken.entities.FirebaseToken;
import io.storeyes.storeyes_coffee.firebasetoken.repositories.FirebaseTokenRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FirebaseTokenService {

    private final FirebaseTokenRepository firebaseTokenRepository;
    private final UserInfoRepository userInfoRepository;
    private final StoreRepository storeRepository;

    /**
     * Upserts a Firebase token for the authenticated user and their store.
     * userId and store are derived from the Keycloak JWT token.
     * If a token already exists for this user+store combination, updates it; otherwise creates a new one.
     */
    @Transactional
    public FirebaseToken upsertToken(UpsertFirebaseTokenRequest request) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + userId));

        Store store = storeRepository.findByOwner_Id(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Store not found for user with id: " + userId));

        FirebaseToken existing = firebaseTokenRepository
                .findByUser_IdAndStore_IdAndPlatform(userId, store.getId(), request.getPlatform())
                .orElse(null);

        if (existing != null) {
            existing.setToken(request.getToken());
            return firebaseTokenRepository.save(existing);
        }

        FirebaseToken newToken = FirebaseToken.builder()
                .user(user)
                .store(store)
                .token(request.getToken())
                .platform(request.getPlatform())
                .build();
        return firebaseTokenRepository.save(newToken);
    }
}
