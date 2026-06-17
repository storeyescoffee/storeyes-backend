package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FeedbackAnswerRequest {

    @NotNull
    private Long questionId;

    @NotNull
    @Pattern(regexp = "GOOD|BAD", message = "rating must be GOOD or BAD")
    private String rating;
}
