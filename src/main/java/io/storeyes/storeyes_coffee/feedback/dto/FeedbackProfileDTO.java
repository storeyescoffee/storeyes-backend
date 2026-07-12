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
public class FeedbackProfileDTO {
    private Long id;
    private Long storeId;
    private String code;
    private String storeName;
    private String logoUrl;
    private String googleReviewUrl;
    /** Active questions in display order — populated for the kiosk (getByCode), null elsewhere. */
    private List<FeedbackQuestionDTO> questions;
    /** Per-store availability of the multi-questions feature (see Store.multipleQuestionsEnabled). */
    private boolean multipleQuestionsEnabled;
}
