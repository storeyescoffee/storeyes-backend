package io.storeyes.storeyes_coffee.alerts.dto;

import io.storeyes.storeyes_coffee.alerts.entities.HumanJudgement;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateHumanJudgementRequest {
    
    @NotNull(message = "Human judgement is required")
    private HumanJudgement humanJudgement;
}

