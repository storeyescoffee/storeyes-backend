package io.storeyes.storeyes_coffee.auth.repositories;

import io.storeyes.storeyes_coffee.auth.entities.PasswordResetChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PasswordResetChallengeRepository extends JpaRepository<PasswordResetChallenge, Long> {

    void deleteByEmailNormalizedAndConsumedAtIsNull(String emailNormalized);

    java.util.Optional<PasswordResetChallenge> findTopByEmailNormalizedAndConsumedAtIsNullOrderByCreatedAtDesc(
            String emailNormalized);
}
