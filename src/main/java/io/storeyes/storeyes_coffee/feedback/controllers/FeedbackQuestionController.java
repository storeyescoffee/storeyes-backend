package io.storeyes.storeyes_coffee.feedback.controllers;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionCreateRequest;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackQuestionUpdateRequest;
import io.storeyes.storeyes_coffee.feedback.services.FeedbackQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedback-profiles/{profileId}/questions")
@RequiredArgsConstructor
public class FeedbackQuestionController {

    private final FeedbackQuestionService questionService;

    /** GET /api/feedback-profiles/{profileId}/questions — admin, all questions including inactive */
    @GetMapping
    public ResponseEntity<List<FeedbackQuestionDTO>> getAll(@PathVariable Long profileId) {
        return ResponseEntity.ok(questionService.getAllByProfile(profileId));
    }

    /** POST /api/feedback-profiles/{profileId}/questions */
    @PostMapping
    public ResponseEntity<FeedbackQuestionDTO> create(
            @PathVariable Long profileId,
            @Valid @RequestBody FeedbackQuestionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.create(profileId, request));
    }

    /** PUT /api/feedback-profiles/{profileId}/questions/{questionId} */
    @PutMapping("/{questionId}")
    public ResponseEntity<FeedbackQuestionDTO> update(
            @PathVariable Long profileId,
            @PathVariable Long questionId,
            @RequestBody FeedbackQuestionUpdateRequest request) {
        return ResponseEntity.ok(questionService.update(questionId, request));
    }

    /** DELETE /api/feedback-profiles/{profileId}/questions/{questionId} */
    @DeleteMapping("/{questionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long profileId,
            @PathVariable Long questionId) {
        questionService.delete(profileId, questionId);
        return ResponseEntity.noContent().build();
    }
}
