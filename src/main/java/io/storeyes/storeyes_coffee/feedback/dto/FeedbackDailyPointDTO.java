package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDailyPointDTO {
    /** Day-of-month label, e.g. "1", "15", "31" */
    private String label;
    private int pos;   // GOOD count
    private int neg;   // BAD count
}
