package io.storeyes.storeyes_coffee.firebase.services;

import io.storeyes.storeyes_coffee.auth.entities.UserInfo;
import io.storeyes.storeyes_coffee.auth.repositories.UserInfoRepository;
import io.storeyes.storeyes_coffee.firebase.dto.FirebaseTokenActionRequest;
import io.storeyes.storeyes_coffee.firebase.entities.FirebaseToken2;
import io.storeyes.storeyes_coffee.firebase.repositories.FirebaseToken2Repository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FirebaseToken2Service {

    private final FirebaseToken2Repository firebaseToken2Repository;
    private final UserInfoRepository userInfoRepository;

    /**
     * Upserts a device record by mobileId, then updates isLoggedIn + lastLoggedInTimestamp.
     * On login: also updates firebaseToken; inserts a new record if mobileId is unknown.
     * On logout: no-op if mobileId is unknown (nothing to log out).
     */
    @Transactional
    public void handleAction(FirebaseTokenActionRequest request) {
        boolean isLogin = "login".equals(request.getAction());

        if (isLogin && (request.getFirebaseToken() == null || request.getFirebaseToken().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "firebaseToken is required for login action");
        }

        Optional<FirebaseToken2> existing = firebaseToken2Repository.findByMobileId(request.getMobileId());

        if (existing.isEmpty()) {
            if (!isLogin) {
                // Nothing to log out — treat as no-op
                return;
            }
            insertNewToken(request);
            return;
        }

        FirebaseToken2 token = existing.get();
        if (isLogin) {
            token.setFirebaseToken(request.getFirebaseToken());
            token.setLoggedIn(true);
        } else {
            token.setLoggedIn(false);
        }
        token.setLastLoggedInTimestamp(LocalDateTime.now());
        firebaseToken2Repository.save(token);
    }

    private void insertNewToken(FirebaseTokenActionRequest request) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }

        UserInfo user = userInfoRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found with id: " + userId));

        String platform = (request.getPlatform() != null && !request.getPlatform().isBlank())
                ? request.getPlatform()
                : "UNKNOWN";

        FirebaseToken2 newToken = FirebaseToken2.builder()
                .user(user)
                .mobileId(request.getMobileId())
                .firebaseToken(request.getFirebaseToken())
                .platform(platform)
                .isLoggedIn(true)
                .lastLoggedInTimestamp(LocalDateTime.now())
                .build();

        firebaseToken2Repository.save(newToken);
    }
}
