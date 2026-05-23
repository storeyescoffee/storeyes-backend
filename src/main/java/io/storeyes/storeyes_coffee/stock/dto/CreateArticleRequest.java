package io.storeyes.storeyes_coffee.stock.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateArticleRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @NotNull(message = "Sale price is required")
    @DecimalMin(value = "0", inclusive = true, message = "Sale price must be 0 or positive")
    private BigDecimal salePrice;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    /** When true, this article may be used as a nested ingredient in other articles' recipes. */
    private Boolean allowAsSubRecipeArticle;
}
