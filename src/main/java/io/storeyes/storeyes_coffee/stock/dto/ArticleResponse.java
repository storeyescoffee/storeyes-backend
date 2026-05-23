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
public class ArticleResponse {
    private Long id;
    private String name;
    private BigDecimal salePrice;
    private String category;
    private Boolean allowAsSubRecipeArticle;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
