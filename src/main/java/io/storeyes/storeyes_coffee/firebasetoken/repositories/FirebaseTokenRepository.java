package io.storeyes.storeyes_coffee.firebasetoken.repositories;

import io.storeyes.storeyes_coffee.firebasetoken.entities.FirebaseToken;
import io.storeyes.storeyes_coffee.firebasetoken.entities.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FirebaseTokenRepository extends JpaRepository<FirebaseToken, Long> {

    /**
     * Find token by user ID, store ID and platform for upsert logic
     */
    Optional<FirebaseToken> findByUser_IdAndStore_IdAndPlatform(String userId, Long storeId, Platform platform);
}
