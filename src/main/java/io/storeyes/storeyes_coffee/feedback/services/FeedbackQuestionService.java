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
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public FeedbackQuestionDTO create(Long profileId, FeedbackQuestionCreateRequest request) {
        FeedbackProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("FeedbackProfile not found: " + profileId));

        int nextOrder = questionRepository
                .findByFeedbackProfileIdOrderByDisplayOrderAsc(profileId)
                .size() + 1;

        FeedbackQuestion question = FeedbackQuestion.builder()
                .feedbackProfile(profile)
                .labelAr(request.getLabelAr())
                .labelFr(request.getLabelFr())
                .labelEn(request.getLabelEn())
                .displayOrder(nextOrder)
                .isActive(true)
                .build();

        return toDTO(questionRepository.save(question));
    }

    @Transactional
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

    @Transactional
    public void delete(Long profileId, Long questionId) {
        questionRepository.delete(findById(questionId));
        resequence(profileId);
    }

    /** Reassigns displayOrder 1…N based on current sort to close gaps after a deletion. */
    private void resequence(Long profileId) {
        List<FeedbackQuestion> remaining = questionRepository
                .findByFeedbackProfileIdOrderByDisplayOrderAsc(profileId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i + 1);
        }
        questionRepository.saveAll(remaining);
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
