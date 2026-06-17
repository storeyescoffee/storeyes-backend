package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackItemDTO {
    private Long id;
    /** "GOOD" or "BAD" */
    private String rating;
    private String comment;
    /** ISO-8601 string, e.g. "2026-05-24T14:32:00" */
    private String submittedAt;
    private Boolean isVisiting;
    private Boolean isMobile;
    /** "AR", "FR", or "EN" */
    private String language;
    private List<FeedbackAnswerItemDTO> answers;
}
