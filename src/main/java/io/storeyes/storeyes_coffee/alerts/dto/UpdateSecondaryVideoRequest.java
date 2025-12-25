package io.storeyes.storeyes_coffee.alerts.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSecondaryVideoRequest {
    
    @NotNull(message = "Secondary video URL is required")
    private String secondaryVideoUrl;
}

