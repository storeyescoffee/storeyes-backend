package io.storeyes.storeyes_coffee.feedback.repositories;

import io.storeyes.storeyes_coffee.feedback.entities.FeedbackProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FeedbackProfileRepository extends JpaRepository<FeedbackProfile, Long> {

    Optional<FeedbackProfile> findByCode(String code);

    Optional<FeedbackProfile> findByStoreId(Long storeId);
}
