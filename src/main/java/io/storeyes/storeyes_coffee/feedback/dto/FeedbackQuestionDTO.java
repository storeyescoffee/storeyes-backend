package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackQuestionDTO {
    private Long id;
    private Long feedbackProfileId;
    private String labelAr;
    private String labelFr;
    private String labelEn;
    private int displayOrder;
    private boolean isActive;
}
