package io.storeyes.storeyes_coffee.feedback.controllers;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackStatsResponse;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackSubmitRequest;
import io.storeyes.storeyes_coffee.feedback.services.FeedbackService;
import jakarta.validation.Valid;
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
            return errorResponse(e.getMessage());
        }
    }

    /**
     * POST /api/feedback/submit
     * Body: { "storeCode": "tachfine", "rating": "GOOD", "comment": "..." }
     * Public endpoint — no auth header required.
     */
    @PostMapping("/submit")
    public ResponseEntity<Map<String, Object>> submit(
            @Valid @RequestBody FeedbackSubmitRequest request) {

        try {
            feedbackService.submit(request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return errorResponse(e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        Map<String, String> detail = new HashMap<>();
        detail.put("message", message != null ? message : "An error occurred");
        error.put("error", detail);
        return ResponseEntity.badRequest().body(error);
    }
}
