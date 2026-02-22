package io.storeyes.storeyes_coffee.firebasetoken.repositories;

import io.storeyes.storeyes_coffee.firebasetoken.entities.FirebaseToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FirebaseTokenRepository extends JpaRepository<FirebaseToken, Long> {

    /**
     * Find token by user ID and store ID for upsert logic
     */
    Optional<FirebaseToken> findByUser_IdAndStore_Id(String userId, Long storeId);
}
