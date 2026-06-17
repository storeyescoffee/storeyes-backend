package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FeedbackQuestionCreateRequest {

    @NotBlank
    private String labelAr;

    @NotBlank
    private String labelFr;

    @NotBlank
    private String labelEn;
}
