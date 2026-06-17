package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackQuestionCreateRequest {

    @NotBlank
    private String labelAr;

    @NotBlank
    private String labelFr;

    @NotBlank
    private String labelEn;

    @NotNull
    private Integer displayOrder;
}
