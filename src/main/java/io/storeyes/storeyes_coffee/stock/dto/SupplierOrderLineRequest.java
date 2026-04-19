package io.storeyes.storeyes_coffee.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierOrderLineRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "Unit price snapshot is required")
    @DecimalMin(value = "0", inclusive = true, message = "Unit price must be 0 or positive")
    private BigDecimal unitPriceSnapshot;

    /**
     * Optional line total; when omitted, computed as quantity × unitPriceSnapshot (2 decimal places).
     */
    @DecimalMin(value = "0", inclusive = true, message = "Line amount must be 0 or positive when provided")
    private BigDecimal lineAmount;
}
