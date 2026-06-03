package io.storeyes.storeyes_coffee.feedback.services;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackDailyPointDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackItemDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackStatsResponse;
import io.storeyes.storeyes_coffee.feedback.entities.Feedback;
import io.storeyes.storeyes_coffee.feedback.repositories.FeedbackRepository;
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

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public FeedbackStatsResponse getStats(Long storeId, String from, String to) {
        LocalDateTime fromDt = LocalDate.parse(from).atStartOfDay();
        LocalDateTime toDt   = LocalDate.parse(to).atTime(23, 59, 59);

        List<Feedback> feedbacks = feedbackRepository
                .findByStoreIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(storeId, fromDt, toDt);

        // ── KPI counts ──────────────────────────────────────────
        int pos = 0, neg = 0, neu = 0;
        double starSum = 0;
        for (Feedback f : feedbacks) {
            starSum += f.getStars();
            if      (f.getStars() >= 4) pos++;
            else if (f.getStars() <= 2) neg++;
            else                        neu++;
        }
        double avg = feedbacks.isEmpty() ? 0.0 : Math.round((starSum / feedbacks.size()) * 10.0) / 10.0;

        // ── Daily breakdown ──────────────────────────────────────
        // Build a map keyed by day-of-month (as String "1".."31")
        Map<String, int[]> dayMap = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        for (Feedback f : feedbacks) {
            String day = String.valueOf(f.getSubmittedAt().getDayOfMonth());
            dayMap.computeIfAbsent(day, k -> new int[3]);
            int[] counts = dayMap.get(day);
            if      (f.getStars() >= 4) counts[0]++;
            else if (f.getStars() <= 2) counts[1]++;
            else                        counts[2]++;
        }
        List<FeedbackDailyPointDTO> daily = dayMap.entrySet().stream()
                .map(e -> FeedbackDailyPointDTO.builder()
                        .label(e.getKey())
                        .pos(e.getValue()[0])
                        .neg(e.getValue()[1])
                        .neu(e.getValue()[2])
                        .build())
                .collect(Collectors.toList());

        // ── Reviews list ─────────────────────────────────────────
        List<FeedbackItemDTO> reviews = feedbacks.stream()
                .map(f -> FeedbackItemDTO.builder()
                        .id(f.getId())
                        .stars(f.getStars())
                        .comment(f.getComment())
                        .type(sentimentType(f.getStars()))
                        .submittedAt(f.getSubmittedAt().format(ISO))
                        .build())
                .collect(Collectors.toList());

        return FeedbackStatsResponse.builder()
                .total(feedbacks.size())
                .pos(pos)
                .neg(neg)
                .neu(neu)
                .avg(avg)
                .daily(daily)
                .reviews(reviews)
                .build();
    }

    private String sentimentType(int stars) {
        if (stars >= 4) return "positive";
        if (stars <= 2) return "negative";
        return "neutral";
    }
}
