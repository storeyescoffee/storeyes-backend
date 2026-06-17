package io.storeyes.storeyes_coffee.feedback.services;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionCreateRequest;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionUpdateRequest;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackProfile;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackQuestion;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackProfileRepository;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackQuestionService {

    private final FeedbackQuestionRepository questionRepository;
    private final FeedbackProfileRepository profileRepository;

    /** Returns all questions for a profile (including inactive) — admin view. */
    public List<FeedbackQuestionDTO> getAllByProfile(Long profileId) {
        return questionRepository.findByFeedbackProfileIdOrderByDisplayOrderAsc(profileId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public FeedbackQuestionDTO create(Long profileId, FeedbackQuestionCreateRequest request) {
        FeedbackProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("FeedbackProfile not found: " + profileId));

        FeedbackQuestion question = FeedbackQuestion.builder()
                .feedbackProfile(profile)
                .labelAr(request.getLabelAr())
                .labelFr(request.getLabelFr())
                .labelEn(request.getLabelEn())
                .displayOrder(request.getDisplayOrder())
                .isActive(true)
                .build();

        return toDTO(questionRepository.save(question));
    }

    public FeedbackQuestionDTO update(Long questionId, FeedbackQuestionUpdateRequest request) {
        FeedbackQuestion question = findById(questionId);

        if (request.getLabelAr() != null && !request.getLabelAr().isBlank()) {
            question.setLabelAr(request.getLabelAr());
        }
        if (request.getLabelFr() != null && !request.getLabelFr().isBlank()) {
            question.setLabelFr(request.getLabelFr());
        }
        if (request.getLabelEn() != null && !request.getLabelEn().isBlank()) {
            question.setLabelEn(request.getLabelEn());
        }
        if (request.getDisplayOrder() != null) {
            question.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            question.setActive(request.getIsActive());
        }

        return toDTO(questionRepository.save(question));
    }

    public void delete(Long questionId) {
        questionRepository.delete(findById(questionId));
    }

    private FeedbackQuestion findById(Long id) {
        return questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("FeedbackQuestion not found: " + id));
    }

    public FeedbackQuestionDTO toDTO(FeedbackQuestion q) {
        return FeedbackQuestionDTO.builder()
                .id(q.getId())
                .feedbackProfileId(q.getFeedbackProfile().getId())
                .labelAr(q.getLabelAr())
                .labelFr(q.getLabelFr())
                .labelEn(q.getLabelEn())
                .displayOrder(q.getDisplayOrder())
                .isActive(q.isActive())
                .build();
    }
}
