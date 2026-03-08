package io.storeyes.storeyes_coffee.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request to set current stock for a product (creates an ADJUSTMENT movement).
 * Provide either quantityInBaseUnit (in product's base unit: g, cl, piece, etc.)
 * or countingQuantity (e.g. 2 kg, 3 bottles) when product has counting_unit and base_per_counting_unit.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetStockRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    /** Target quantity in the product's base unit (e.g. grams for g, centilitres for cl). */
    @DecimalMin(value = "0", inclusive = true, message = "Quantity must be 0 or positive")
    private BigDecimal quantityInBaseUnit;

    /**
     * Alternative: quantity in counting unit (e.g. 2 for "2 kg", 3 for "3 bottles").
     * Used only when product has counting_unit and base_per_counting_unit; converted to base unit internally.
     */
    @DecimalMin(value = "0", inclusive = true, message = "Counting quantity must be 0 or positive")
    private BigDecimal countingQuantity;

    /** Amount (MAD) for the adjustment. Required for inventory value. */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0", inclusive = true, message = "Amount must be 0 or positive")
    private BigDecimal amount;
}
