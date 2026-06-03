package io.storeyes.storeyes_coffee.feedback.services;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackDailyPointDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackItemDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackStatsResponse;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackSubmitRequest;
import io.storeyes.storeyes_coffee.feedback.entities.Feedback;
import io.storeyes.storeyes_coffee.feedback.entities.FeedbackRating;
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
    private final StoreRepository    storeRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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
        int total          = feedbacks.size();
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

        // ── Reviews list ─────────────────────────────────────────
        List<FeedbackItemDTO> reviews = feedbacks.stream()
                .map(f -> FeedbackItemDTO.builder()
                        .id(f.getId())
                        .rating(f.getRating().name())
                        .comment(f.getComment())
                        .submittedAt(f.getSubmittedAt().format(ISO))
                        .build())
                .collect(Collectors.toList());

        return FeedbackStatsResponse.builder()
                .total(total)
                .pos(pos)
                .neg(neg)
                .satisfactionPct(satisfactionPct)
                .daily(daily)
                .reviews(reviews)
                .build();
    }

    public void submit(FeedbackSubmitRequest request) {
        Store store = storeRepository.findByCode(request.getStoreCode())
                .orElseThrow(() -> new RuntimeException("Store not found: " + request.getStoreCode()));

        Feedback feedback = Feedback.builder()
                .store(store)
                .rating(FeedbackRating.valueOf(request.getRating()))
                .comment(request.getComment())
                .build();

        feedbackRepository.save(feedback);
    }
}
