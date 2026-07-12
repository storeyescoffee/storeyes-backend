package io.storeyes.storeyes_coffee.feedback.services;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackAnswerItemDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackCreateRequest;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackDailyPointDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackItemDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackPatchRequest;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionStatsDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackStatsResponse;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackSubmitRequest;
import io.storeyes.storeyes_coffee.feedback.entities.Feedback;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackAnswer;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackLanguage;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackProfile;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackQuestion;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackRating;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackAnswerRepository;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackProfileRepository;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackQuestionRepository;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final StoreRepository storeRepository;
    private final FeedbackProfileRepository feedbackProfileRepository;
    private final FeedbackQuestionRepository feedbackQuestionRepository;
    private final FeedbackAnswerRepository feedbackAnswerRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public boolean hasFeedbackProfile(Long storeId) {
        return feedbackProfileRepository.findByStoreId(storeId).isPresent();
    }

    public FeedbackStatsResponse getStats(Long storeId, String from, String to) {
        LocalDateTime fromDt = LocalDate.parse(from).atStartOfDay();
        LocalDateTime toDt   = LocalDate.parse(to).atTime(23, 59, 59);

        List<Feedback> feedbacks = feedbackRepository
                .findByStoreIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(storeId, fromDt, toDt);

        // ── KPI counts ───────────────────────────────────────────
        int pos = 0, neg = 0;
        for (Feedback f : feedbacks) {
            if (f.getRating() == FeedbackRating.GOOD) pos++;
            else                                      neg++;
        }
        int total           = feedbacks.size();
        int satisfactionPct = total == 0 ? 0 : (int) Math.round(pos * 100.0 / total);

        // ── Daily breakdown ──────────────────────────────────────
        Map<String, int[]> dayMap = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        for (Feedback f : feedbacks) {
            String day = String.valueOf(f.getSubmittedAt().getDayOfMonth());
            dayMap.computeIfAbsent(day, k -> new int[2]);
            int[] counts = dayMap.get(day);
            if (f.getRating() == FeedbackRating.GOOD) counts[0]++;
            else                                      counts[1]++;
        }
        List<FeedbackDailyPointDTO> daily = dayMap.entrySet().stream()
                .map(e -> FeedbackDailyPointDTO.builder()
                        .label(e.getKey())
                        .pos(e.getValue()[0])
                        .neg(e.getValue()[1])
                        .build())
                .collect(Collectors.toList());

        // ── Fetch all answers for this batch ─────────────────────
        List<Long> feedbackIds = feedbacks.stream().map(Feedback::getId).collect(Collectors.toList());
        List<FeedbackAnswer> allAnswers = feedbackIds.isEmpty()
                ? Collections.emptyList()
                : feedbackAnswerRepository.findByFeedbackIdIn(feedbackIds);

        // Group answers by feedbackId for quick lookup
        Map<Long, List<FeedbackAnswer>> answersByFeedback = allAnswers.stream()
                .collect(Collectors.groupingBy(a -> a.getFeedback().getId()));

        // ── Reviews list ─────────────────────────────────────────
        List<FeedbackItemDTO> reviews = feedbacks.stream()
                .map(f -> {
                    List<FeedbackAnswerItemDTO> answerDTOs = answersByFeedback
                            .getOrDefault(f.getId(), Collections.emptyList())
                            .stream()
                            .map(a -> FeedbackAnswerItemDTO.builder()
                                    .questionId(a.getQuestion().getId())
                                    .rating(a.getRating().name())
                                    .build())
                            .collect(Collectors.toList());

                    return FeedbackItemDTO.builder()
                            .id(f.getId())
                            .rating(f.getRating().name())
                            .comment(f.getComment())
                            .submittedAt(f.getSubmittedAt().format(ISO))
                            .isVisiting(f.isVisiting())
                            .isMobile(f.isMobile())
                            .language(f.getLanguage() != null ? f.getLanguage().name() : null)
                            .answers(answerDTOs)
                            .build();
                })
                .collect(Collectors.toList());

        // ── Per-question stats ────────────────────────────────────
        Map<Long, int[]> questionCounts = new LinkedHashMap<>();
        Map<Long, FeedbackQuestion> questionById = new LinkedHashMap<>();
        for (FeedbackAnswer a : allAnswers) {
            FeedbackQuestion q = a.getQuestion();
            questionById.put(q.getId(), q);
            questionCounts.computeIfAbsent(q.getId(), k -> new int[2]);
            int[] c = questionCounts.get(q.getId());
            if (a.getRating() == FeedbackRating.GOOD) c[0]++;
            else                                      c[1]++;
        }
        List<FeedbackQuestionStatsDTO> questionStats = questionCounts.entrySet().stream()
                .map(e -> {
                    FeedbackQuestion q = questionById.get(e.getKey());
                    int qPos = e.getValue()[0];
                    int qNeg = e.getValue()[1];
                    int qTotal = qPos + qNeg;
                    return FeedbackQuestionStatsDTO.builder()
                            .questionId(q.getId())
                            .labelAr(q.getLabelAr())
                            .labelFr(q.getLabelFr())
                            .labelEn(q.getLabelEn())
                            .total(qTotal)
                            .pos(qPos)
                            .neg(qNeg)
                            .satisfactionPct(qTotal == 0 ? 0 : (int) Math.round(qPos * 100.0 / qTotal))
                            .build();
                })
                .collect(Collectors.toList());

        FeedbackProfileDTO profile = feedbackProfileRepository.findByStoreId(storeId)
                .map(p -> FeedbackProfileDTO.builder()
                        .id(p.getId())
                        .storeId(p.getStore().getId())
                        .code(p.getCode())
                        .storeName(p.getStoreName())
                        .logoUrl(p.getLogoUrl())
                        .googleReviewUrl(p.getGoogleReviewUrl())
                        .multipleQuestionsEnabled(p.getStore().isMultipleQuestionsEnabled())
                        .build())
                .orElse(null);

        return FeedbackStatsResponse.builder()
                .total(total)
                .pos(pos)
                .neg(neg)
                .satisfactionPct(satisfactionPct)
                .daily(daily)
                .reviews(reviews)
                .profile(profile)
                .questionStats(questionStats)
                .build();
    }

    public String create(FeedbackCreateRequest request) {
        FeedbackProfile profile = feedbackProfileRepository.findByCode(request.getFeedbackProfileCode())
                .orElseThrow(() -> new RuntimeException("FeedbackProfile not found: " + request.getFeedbackProfileCode()));
        Store store = profile.getStore();

        Feedback feedback = Feedback.builder()
                .store(store)
                .rating(FeedbackRating.valueOf(request.getRating()))
                .language(FeedbackLanguage.valueOf(request.getLanguage()))
                .isMobile(request.getIsMobile())
                .build();

        return String.valueOf(feedbackRepository.save(feedback).getId());
    }

    public void patch(Long id, FeedbackPatchRequest request) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feedback not found: " + id));

        if (request.getComment() != null) {
            feedback.setComment(request.getComment());
        }
        if (request.getIsVisiting() != null) {
            feedback.setVisiting(request.getIsVisiting());
        }
        feedbackRepository.save(feedback);

        if (request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            request.getAnswers().forEach(answerReq -> {
                FeedbackQuestion question = feedbackQuestionRepository.findById(answerReq.getQuestionId())
                        .orElseThrow(() -> new RuntimeException("FeedbackQuestion not found: " + answerReq.getQuestionId()));
                feedbackAnswerRepository.save(FeedbackAnswer.builder()
                        .feedback(feedback)
                        .question(question)
                        .rating(FeedbackRating.valueOf(answerReq.getRating()))
                        .build());
            });
        }
    }

    public void submit(FeedbackSubmitRequest request) {
        Store store = storeRepository.findByCode(request.getStoreCode())
                .orElseThrow(() -> new RuntimeException("Store not found: " + request.getStoreCode()));

        Feedback feedback = Feedback.builder()
                .store(store)
                .rating(FeedbackRating.valueOf(request.getRating()))
                .comment(request.getComment())
                .isVisiting(request.isVisiting())
                .language(request.getLanguage() != null ? FeedbackLanguage.valueOf(request.getLanguage()) : null)
                .build();

        feedbackRepository.save(feedback);
    }
}
