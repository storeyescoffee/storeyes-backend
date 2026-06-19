package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FeedbackSubmitRequest {

    @NotBlank
    private String storeCode;

    @NotBlank
    @Pattern(regexp = "GOOD|BAD", message = "rating must be GOOD or BAD")
    private String rating;

    private String comment;

    private boolean isVisiting;

    @Pattern(regexp = "AR|FR|EN", message = "language must be AR, FR, or EN")
    private String language;
}
