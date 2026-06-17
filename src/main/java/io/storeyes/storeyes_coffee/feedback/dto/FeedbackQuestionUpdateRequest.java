package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.Data;

@Data
public class FeedbackQuestionUpdateRequest {

    private String labelAr;
    private String labelFr;
    private String labelEn;
    private Integer displayOrder;
    private Boolean isActive;
}
