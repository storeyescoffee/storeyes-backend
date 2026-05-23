package io.storeyes.storeyes_coffee.stock.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "recipe_ingredients",
    indexes = {
        @Index(name = "idx_recipe_ingredients_article", columnList = "article_id"),
        @Index(name = "idx_recipe_ingredients_product", columnList = "product_id"),
        @Index(name = "idx_recipe_ingredients_ingredient_article", columnList = "ingredient_article_id")
    }
)
public class RecipeIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    /** Present when this line consumes a stock product directly. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true)
    private StockProduct product;

    /** Present when this line nests another article's recipe (expanded at consumption time). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_article_id", nullable = true)
    private Article ingredientArticle;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 4)
    private BigDecimal quantity;

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
