package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackStatsResponse {
    private int total;
    private int pos;
    private int neg;
    private int neu;
    private double avg;
    private List<FeedbackDailyPointDTO> daily;
    private List<FeedbackItemDTO> reviews;
}
