package io.storeyes.storeyes_coffee.feedback.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FeedbackProfileUpdateRequest {

    private String storeName;
    private String googleReviewUrl;
    private MultipartFile logo;
}
