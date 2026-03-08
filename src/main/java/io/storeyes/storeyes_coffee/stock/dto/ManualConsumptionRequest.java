package io.storeyes.storeyes_coffee.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to record manual consumption (e.g. waste, spillage).
 * Creates a CONSUMPTION movement with negative quantity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualConsumptionRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    /** Quantity consumed in base unit (positive value – will be stored as negative). */
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = true, message = "Quantity must be 0 or positive")
    private BigDecimal quantityInBaseUnit;

    /**
     * Alternative: quantity in counting unit (e.g. 2 kg, 3 bottles).
     * Used when product has counting_unit and base_per_counting_unit.
     */
    @DecimalMin(value = "0", inclusive = true, message = "Counting quantity must be 0 or positive")
    private BigDecimal countingQuantity;

    /** Amount (MAD) for the consumed stock value. Required. */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0", inclusive = true, message = "Amount must be 0 or positive")
    private BigDecimal amount;

    /** Optional notes (e.g. "Waste", "Spillage"). */
    private String notes;
}
