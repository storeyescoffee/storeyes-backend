package io.storeyes.storeyes_coffee.kpi.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRevenueBreakdownRequest {
    @NotNull(message = "TPE value is required")
    @DecimalMin(value = "0.0", message = "TPE must be >= 0")
    private Double tpe;
}
