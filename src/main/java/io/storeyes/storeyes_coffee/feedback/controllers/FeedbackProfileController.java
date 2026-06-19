package io.storeyes.storeyes_coffee.feedback.controllers;

import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileCreateRequest;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileDTO;
import io.storeyes.storeyes_coffee.feedback.dto.FeedbackProfileUpdateRequest;
import io.storeyes.storeyes_coffee.feedback.services.FeedbackProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback-profiles")
@RequiredArgsConstructor
public class FeedbackProfileController {

    private final FeedbackProfileService feedbackProfileService;

    /** GET /api/feedback-profiles/{code} — public, used by the kiosk */
    @GetMapping("/{code}")
    public ResponseEntity<FeedbackProfileDTO> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(feedbackProfileService.getByCode(code));
    }

    /** POST /api/feedback-profiles */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<FeedbackProfileDTO> create(
            @Valid @ModelAttribute FeedbackProfileCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackProfileService.create(request));
    }

    /** PUT /api/feedback-profiles/{id} */
    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public ResponseEntity<FeedbackProfileDTO> update(
            @PathVariable Long id,
            @ModelAttribute FeedbackProfileUpdateRequest request) {
        return ResponseEntity.ok(feedbackProfileService.update(id, request));
    }

    /** GET /api/feedback-profiles/{id}/logo — proxies the S3 image to avoid browser CORS restrictions */
    @GetMapping("/{id}/logo")
    public ResponseEntity<byte[]> getLogo(@PathVariable Long id) {
        try {
            byte[] bytes = feedbackProfileService.getLogoBytes(id);
            String contentType = feedbackProfileService.getLogoContentType(id);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            headers.set(HttpHeaders.CACHE_CONTROL, "public, max-age=3600");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** DELETE /api/feedback-profiles/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        feedbackProfileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
