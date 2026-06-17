package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackQuestionStatsDTO {
    private Long questionId;
    private String labelAr;
    private String labelFr;
    private String labelEn;
    private int total;
    private int pos;
    private int neg;
    private int satisfactionPct;
}
