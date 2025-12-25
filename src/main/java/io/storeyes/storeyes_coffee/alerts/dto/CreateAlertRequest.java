package io.storeyes.storeyes_coffee.alerts.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateAlertRequest {
    
    @NotNull(message = "Alert date is required")
    private LocalDateTime alertDate;
    
    @NotNull(message = "Video URL is required")
    private String mainVideoUrl;
    
    private String productName;
    
    private String imageUrl;
}

