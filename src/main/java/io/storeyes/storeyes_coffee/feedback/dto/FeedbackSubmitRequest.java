package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FeedbackSubmitRequest {

    /** The store's short code, e.g. "tachfine" */
    @NotBlank
    private String storeCode;

    /** Must be "GOOD" or "BAD" */
    @NotNull
    @Pattern(regexp = "GOOD|BAD", message = "rating must be GOOD or BAD")
    private String rating;

    /** Optional free-text comment (max 2048 chars) */
    private String comment;
}
