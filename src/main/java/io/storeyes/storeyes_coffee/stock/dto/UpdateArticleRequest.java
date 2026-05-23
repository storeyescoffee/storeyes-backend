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
public class UpdateArticleRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @DecimalMin(value = "0", inclusive = true, message = "Sale price must be 0 or positive")
    private BigDecimal salePrice;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    private Boolean allowAsSubRecipeArticle;
}
