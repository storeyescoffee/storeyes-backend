package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FeedbackCreateRequest {

    @NotBlank
    private String feedbackProfileCode;

    @NotNull
    @Pattern(regexp = "GOOD|BAD", message = "rating must be GOOD or BAD")
    private String rating;

    @NotNull
    @Pattern(regexp = "AR|FR|EN", message = "language must be AR, FR, or EN")
    private String language;

    @NotNull
    private Boolean isMobile;
}
