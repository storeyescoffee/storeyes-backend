package io.storeyes.storeyes_coffee.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FeedbackProfileCreateRequest {

    @NotNull
    private Long storeId;

    @NotBlank
    private String storeName;

    @NotBlank
    private String googleReviewUrl;

    private MultipartFile logo;
}
