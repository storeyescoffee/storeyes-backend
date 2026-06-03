package io.storeyes.storeyes_coffee.feedback.controllers;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackStatsResponse;
import io.storeyes.storeyes_coffee.feedback.services.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * GET /api/feedback/stats?from=YYYY-MM-DD&to=YYYY-MM-DD
     * Header: X-Store-Id: {storeId}
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam String from,
            @RequestParam String to,
            @RequestHeader("X-Store-Id") Long storeId) {

        try {
            FeedbackStatsResponse response = feedbackService.getStats(storeId, from, to);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            Map<String, Object> detail = new HashMap<>();
            detail.put("code", "FEEDBACK_ERROR");
            detail.put("message", e.getMessage() != null ? e.getMessage() : "An error occurred");
            error.put("error", detail);
            return ResponseEntity.status(400).body(error);
        }
    }
}
