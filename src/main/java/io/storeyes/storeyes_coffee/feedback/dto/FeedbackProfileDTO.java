package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
