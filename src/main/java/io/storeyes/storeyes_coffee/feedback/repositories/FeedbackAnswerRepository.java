package io.storeyes.storeyes_coffee.feedback.repositories;

import io.storeyes.storeyes_coffee.feedback.entities.FeedbackAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackAnswerRepository extends JpaRepository<FeedbackAnswer, Long> {

    List<FeedbackAnswer> findByFeedbackIdIn(List<Long> feedbackIds);
}
