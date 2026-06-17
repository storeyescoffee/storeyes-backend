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

    /**
     * Updates labels and/or active state.
     * If displayOrder changed, re-inserts the question at the requested position
     * and renumbers all questions 1…N — so there are never gaps or duplicates.
     *
     * Move scenarios handled automatically:
     *   move forward  (e.g. 2 → 4): questions between shift up by one
     *   move backward (e.g. 4 → 1): questions between shift down by one
     *   same position: no-op resequence
     *   out-of-range  (e.g. 99): clamped to last position
     */
    @Transactional
    public FeedbackQuestionDTO update(Long profileId, Long questionId, FeedbackQuestionUpdateRequest request) {
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
        if (request.getIsActive() != null) {
            question.setActive(request.getIsActive());
        }

        // Save label / active changes first
        questionRepository.save(question);

        // Then resequence if order changed
        if (request.getDisplayOrder() != null) {
            resequenceForMove(profileId, questionId, request.getDisplayOrder());
        }

        return toDTO(findById(questionId));
    }

    @Transactional
    public void delete(Long profileId, Long questionId) {
        questionRepository.delete(findById(questionId));
        resequence(profileId);
    }

    // ── Private helpers ──────────────────────────────────────────

    /**
     * Re-inserts the target question at {@code targetOrder} within the profile's
     * ordered list, then renumbers all questions 1…N.
     *
     * Algorithm (list-manipulation, no conditional branches needed):
     *   1. Sort all questions by current displayOrder.
     *   2. Remove the moved question from the list.
     *   3. Insert it at index (targetOrder - 1), clamped to [0, size].
     *   4. Assign displayOrder = index + 1 to every element and save.
     */
    private void resequenceForMove(Long profileId, Long movedQuestionId, int targetOrder) {
        List<FeedbackQuestion> questions = questionRepository
                .findByFeedbackProfileIdOrderByDisplayOrderAsc(profileId);

        FeedbackQuestion moved = questions.stream()
                .filter(q -> q.getId().equals(movedQuestionId))
                .findFirst()
                .orElseThrow();

        questions.remove(moved);

        int insertIndex = Math.max(0, Math.min(targetOrder - 1, questions.size()));
        questions.add(insertIndex, moved);

        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setDisplayOrder(i + 1);
        }
        questionRepository.saveAll(questions);
    }

    /** Renumbers remaining questions 1…N after a deletion. */
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
