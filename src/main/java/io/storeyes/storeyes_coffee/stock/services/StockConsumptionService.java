package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.entities.RecipeIngredient;
import io.storeyes.storeyes_coffee.stock.entities.StockMovement;
import io.storeyes.storeyes_coffee.stock.entities.StockMovementType;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.repositories.RecipeIngredientRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockMovementRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates CONSUMPTION movements when articles (sales products) are sold.
 * Recipe lines may reference stock products directly or nest other articles; nested lines are
 * expanded to stock products before posting movements.
 */
@Service
@RequiredArgsConstructor
public class StockConsumptionService {

    private static final String REFERENCE_TYPE_ARTICLE_SALE = "ARTICLE_SALE";
    private static final int MAX_RECIPE_DEPTH = 32;

    private final RecipeIngredientRepository recipeIngredientRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockProductRepository stockProductRepository;

    @Transactional
    public void createConsumptionForArticleSale(Long storeId, Long articleId, BigDecimal quantitySold, LocalDate saleDate, Long referenceId) {
        if (quantitySold == null || quantitySold.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<Long, BigDecimal> productIdToConsumedAbs = new LinkedHashMap<>();
        accumulateStockConsumption(storeId, articleId, quantitySold, productIdToConsumedAbs, new HashSet<>(), 0);

        for (Map.Entry<Long, BigDecimal> e : productIdToConsumedAbs.entrySet()) {
            Long productId = e.getKey();
            BigDecimal consumedAbs = e.getValue();
            if (consumedAbs == null || consumedAbs.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            StockProduct product = stockProductRepository.findById(productId).orElse(null);
            if (product == null || product.getStore() == null || !product.getStore().getId().equals(storeId)) {
                continue;
            }

            BigDecimal consumed = consumedAbs.negate();

            BigDecimal avgCost = stockMovementRepository.getAveragePurchaseCostPerUnit(storeId, productId);
            BigDecimal movementAmount = null;
            if (avgCost != null && avgCost.compareTo(BigDecimal.ZERO) > 0) {
                movementAmount = consumedAbs.multiply(avgCost).setScale(2, RoundingMode.HALF_UP);
            }

            StockMovement movement = StockMovement.builder()
                    .store(product.getStore())
                    .product(product)
                    .type(StockMovementType.CONSUMPTION)
                    .quantity(consumed)
                    .amount(movementAmount)
                    .movementDate(saleDate)
                    .referenceType(REFERENCE_TYPE_ARTICLE_SALE)
                    .referenceId(referenceId)
                    .notes("Consumption from article sale (expanded): product " + product.getName() + " x " + consumedAbs)
                    .build();
            stockMovementRepository.save(movement);
        }
    }

    /**
     * Walks the recipe tree for {@code articleId}, merging absolute base-unit quantities per stock product.
     * Empty nested recipes contribute nothing. Cycles in data are skipped defensively.
     */
    private void accumulateStockConsumption(
            Long storeId,
            Long articleId,
            BigDecimal multiplier,
            Map<Long, BigDecimal> productIdToConsumedAbs,
            Set<Long> pathArticleIds,
            int depth
    ) {
        if (depth > MAX_RECIPE_DEPTH || multiplier == null || multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!pathArticleIds.add(articleId)) {
            return;
        }
        List<RecipeIngredient> ingredients = recipeIngredientRepository.findByArticleIdOrderByProductName(articleId);
        for (RecipeIngredient ri : ingredients) {
            if (ri.getQuantity() == null || ri.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal branchMult = ri.getQuantity().multiply(multiplier);
            if (ri.getProduct() != null) {
                if (ri.getProduct().getStore() == null || !ri.getProduct().getStore().getId().equals(storeId)) {
                    continue;
                }
                Long pid = ri.getProduct().getId();
                productIdToConsumedAbs.merge(pid, branchMult, BigDecimal::add);
            } else if (ri.getIngredientArticle() != null) {
                Article nested = ri.getIngredientArticle();
                if (nested.getStore() == null || !nested.getStore().getId().equals(storeId)) {
                    continue;
                }
                accumulateStockConsumption(storeId, nested.getId(), branchMult, productIdToConsumedAbs, pathArticleIds, depth + 1);
            }
        }
        pathArticleIds.remove(articleId);
    }
}
