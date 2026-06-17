package io.storeyes.storeyes_coffee.feedback.repositories;

import io.storeyes.storeyes_coffee.feedback.entities.FeedbackQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackQuestionRepository extends JpaRepository<FeedbackQuestion, Long> {

    List<FeedbackQuestion> findByFeedbackProfileIdOrderByDisplayOrderAsc(Long feedbackProfileId);

    List<FeedbackQuestion> findByFeedbackProfileIdAndIsActiveTrueOrderByDisplayOrderAsc(Long feedbackProfileId);
}
