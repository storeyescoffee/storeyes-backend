package io.storeyes.storeyes_coffee.alerts.dto;

import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    
    private Long id;
    private LocalDateTime alertDate;
    private String mainVideoUrl;
    private String productName;
    private String imageUrl;
    private boolean isProcessed;
    private String secondaryVideoUrl;
    private HumanJudgement humanJudgement;
    private String humanJudgementComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

