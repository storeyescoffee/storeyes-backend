package io.storeyes.storeyes_coffee.notification.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DailyKpiNotificationRequest {

    @NotNull
    private Double raz;
}
