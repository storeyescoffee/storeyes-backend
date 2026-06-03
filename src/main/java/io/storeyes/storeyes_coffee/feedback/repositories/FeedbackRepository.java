package io.storeyes.storeyes_coffee.feedback.repositories;

import io.storeyes.storeyes_coffee.feedback.entities.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FeedbackRepository
        extends JpaRepository<Feedback, Long>, JpaSpecificationExecutor<Feedback> {

    List<Feedback> findByStoreIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(
            Long storeId, LocalDateTime from, LocalDateTime to);
}
