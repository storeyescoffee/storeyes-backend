package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackItemDTO {
    private Long id;
    private int stars;
    private String comment;
    /** "positive" | "negative" | "neutral" */
    private String type;
    /** ISO-8601 string, e.g. "2026-05-24T14:32:00" */
    private String submittedAt;
}
