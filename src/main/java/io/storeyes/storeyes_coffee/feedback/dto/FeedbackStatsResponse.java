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
    private int pos;              // GOOD count
    private int neg;              // BAD count
    private int satisfactionPct;  // pos * 100 / total  (0 when total == 0)
    private List<FeedbackDailyPointDTO> daily;
    private List<FeedbackItemDTO> reviews;
}
