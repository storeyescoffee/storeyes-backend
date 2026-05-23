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
public class CreateRecipeIngredientRequest {

    /** Set for a direct stock ingredient (exclusive with {@link #ingredientArticleId}). */
    private Long productId;

    /** Set for a nested article ingredient (exclusive with {@link #productId}). */
    private Long ingredientArticleId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0", inclusive = false, message = "Quantity must be positive")
    private BigDecimal quantity;
}
