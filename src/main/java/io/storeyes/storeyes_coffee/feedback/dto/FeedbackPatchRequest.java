package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.Data;

@Data
public class FeedbackPatchRequest {

    private String comment;
    private Boolean isVisiting;
}
