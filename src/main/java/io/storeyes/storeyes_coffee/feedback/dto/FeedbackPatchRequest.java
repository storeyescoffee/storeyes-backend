package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.Data;

import java.util.List;

@Data
public class FeedbackPatchRequest {

    private String comment;
    private Boolean isVisiting;
    private List<FeedbackAnswerRequest> answers;
}
