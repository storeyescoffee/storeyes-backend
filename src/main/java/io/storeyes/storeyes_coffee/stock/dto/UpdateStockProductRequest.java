package io.storeyes.storeyes_coffee.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStockProductRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Arabic name must not exceed 255 characters")
    private String nameAr;

    private Long subCategoryId;

    @Size(max = 50, message = "Unit must not exceed 50 characters")
    private String unit;

    @DecimalMin(value = "0", inclusive = true, message = "Unit price must be 0 or positive when set")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0", inclusive = true, message = "Minimal threshold must be 0 or positive")
    private BigDecimal minimalThreshold;

    @Size(max = 50, message = "Counting unit must not exceed 50 characters")
    private String countingUnit;

    @DecimalMin(value = "0", inclusive = false, message = "Base per counting unit must be positive when set")
    private BigDecimal basePerCountingUnit;

    private Boolean isActive;
}
