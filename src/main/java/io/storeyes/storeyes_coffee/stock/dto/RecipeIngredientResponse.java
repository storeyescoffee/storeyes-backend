package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeIngredientResponse {
    private Long id;
    private Long articleId;
    private RecipeIngredientType ingredientType;
    private Long productId;
    private String productName;
    private String productUnit;
    private Long ingredientArticleId;
    private String ingredientArticleName;
    private BigDecimal quantity;
    /**
     * Cost in MAD for this recipe line quantity using the stock product’s current {@code unit_price},
     * with counting-unit conversion when {@code base_per_counting_unit} is set (same rules as purchases / supplements).
     */
    private BigDecimal lineCostAtCurrentPrice;
    private LocalDateTime createdAt;
}
