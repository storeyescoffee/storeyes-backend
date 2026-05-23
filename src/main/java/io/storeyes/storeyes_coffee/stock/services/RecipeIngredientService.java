package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.stock.dto.CreateRecipeIngredientRequest;
import io.storeyes.storeyes_coffee.stock.dto.RecipeIngredientResponse;
import io.storeyes.storeyes_coffee.stock.dto.RecipeIngredientType;
import io.storeyes.storeyes_coffee.stock.dto.UpdateRecipeIngredientRequest;
import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.entities.RecipeIngredient;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.repositories.RecipeIngredientRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipeIngredientService {

    private static final int MAX_NESTED_LINE_COST_DEPTH = 32;

    private final RecipeIngredientRepository recipeIngredientRepository;
    private final StockProductRepository stockProductRepository;
    private final ArticleService articleService;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    private Long getStoreId() {
        return CurrentStoreContext.requireCurrentStoreId();
    }

    private Long getStockDataStoreId() {
        return demoStoreDataSourceResolver.resolveStockDataStoreId(getStoreId());
    }

    public List<RecipeIngredientResponse> getRecipeByArticleId(Long articleId) {
        Long storeId = getStockDataStoreId();
        Article article = articleService.getArticleEntity(articleId, storeId);
        List<RecipeIngredient> list = new ArrayList<>(recipeIngredientRepository.findByArticleIdOrderByProductName(article.getId()));
        list.sort(Comparator
                .comparing(this::getIngredientDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RecipeIngredient::getId, Comparator.nullsLast(Long::compareTo)));
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public RecipeIngredientResponse getRecipeIngredientById(Long articleId, Long id) {
        Long storeId = getStockDataStoreId();
        articleService.getArticleEntity(articleId, storeId);
        RecipeIngredient ri = recipeIngredientRepository.findByIdAndArticleId(id, articleId)
                .orElseThrow(() -> new RuntimeException("Recipe ingredient not found with id: " + id));
        return toResponse(ri);
    }

    @Transactional
    public RecipeIngredientResponse createRecipeIngredient(Long articleId, CreateRecipeIngredientRequest request) {
        Long storeId = getStockDataStoreId();
        Article article = articleService.getArticleEntity(articleId, storeId);

        boolean hasProduct = request.getProductId() != null;
        boolean hasIngredientArticle = request.getIngredientArticleId() != null;
        if (hasProduct == hasIngredientArticle) {
            throw new RuntimeException("Provide exactly one of productId or ingredientArticleId");
        }

        if (hasProduct) {
            StockProduct product = stockProductRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException("Stock product not found with id: " + request.getProductId()));
            if (!product.getStore().getId().equals(storeId)) {
                throw new RuntimeException("Stock product not found with id: " + request.getProductId());
            }
            if (recipeIngredientRepository.existsByArticleIdAndProductId(articleId, request.getProductId())) {
                throw new RuntimeException("This product is already in the recipe for this article");
            }
            RecipeIngredient ri = RecipeIngredient.builder()
                    .article(article)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            RecipeIngredient saved = recipeIngredientRepository.save(ri);
            return toResponse(saved);
        }

        Article child = articleService.getArticleEntity(request.getIngredientArticleId(), storeId);
        if (!Boolean.TRUE.equals(child.getAllowAsSubRecipeArticle())) {
            throw new RuntimeException("This article is not allowed as a nested recipe ingredient");
        }
        if (child.getId().equals(articleId)) {
            throw new RuntimeException("An article cannot be an ingredient of itself");
        }
        if (recipeIngredientRepository.existsByArticleIdAndIngredientArticle_Id(articleId, child.getId())) {
            throw new RuntimeException("This article is already in the recipe as a nested ingredient");
        }
        if (introducesIngredientCycle(articleId, child.getId())) {
            throw new RuntimeException("This nested ingredient would create a recipe cycle");
        }

        RecipeIngredient ri = RecipeIngredient.builder()
                .article(article)
                .ingredientArticle(child)
                .quantity(request.getQuantity())
                .build();
        RecipeIngredient saved = recipeIngredientRepository.save(ri);
        return toResponse(saved);
    }

    /**
     * True if following nested-article edges from {@code startArticleId} can reach {@code targetArticleId}.
     */
    private boolean introducesIngredientCycle(Long recipeOwnerArticleId, Long newIngredientArticleId) {
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(newIngredientArticleId);
        Set<Long> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (cur.equals(recipeOwnerArticleId)) {
                return true;
            }
            if (!visited.add(cur)) {
                continue;
            }
            List<Long> nextIds = recipeIngredientRepository.findNestedIngredientArticleIdsByArticleId(cur);
            for (Long n : nextIds) {
                if (n != null) {
                    stack.push(n);
                }
            }
        }
        return false;
    }

    @Transactional
    public RecipeIngredientResponse updateRecipeIngredient(Long articleId, Long id, UpdateRecipeIngredientRequest request) {
        Long storeId = getStockDataStoreId();
        articleService.getArticleEntity(articleId, storeId);
        RecipeIngredient ri = recipeIngredientRepository.findByIdAndArticleId(id, articleId)
                .orElseThrow(() -> new RuntimeException("Recipe ingredient not found with id: " + id));
        if (request.getQuantity() != null) {
            ri.setQuantity(request.getQuantity());
        }
        RecipeIngredient updated = recipeIngredientRepository.save(ri);
        return toResponse(updated);
    }

    @Transactional
    public void deleteRecipeIngredient(Long articleId, Long id) {
        Long storeId = getStockDataStoreId();
        articleService.getArticleEntity(articleId, storeId);
        if (!recipeIngredientRepository.findByIdAndArticleId(id, articleId).isPresent()) {
            throw new RuntimeException("Recipe ingredient not found with id: " + id);
        }
        recipeIngredientRepository.deleteById(id);
    }

    /**
     * Recipe quantities are stored in base units. {@code unitPrice} is per counting unit when
     * {@code basePerCountingUnit} is set and positive; otherwise per base unit — aligned with
     * {@code StockMovementService} supplement / inventory amount logic.
     */
    private BigDecimal computeLineCostAtCurrentPrice(StockProduct p, BigDecimal quantityBase) {
        if (quantityBase == null || quantityBase.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal unitPrice = p.getUnitPrice() != null ? p.getUnitPrice() : BigDecimal.ZERO;
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal basePer = p.getBasePerCountingUnit();
        BigDecimal qtyForAmount;
        if (basePer != null && basePer.compareTo(BigDecimal.ZERO) > 0) {
            qtyForAmount = quantityBase.divide(basePer, 4, RoundingMode.HALF_UP);
        } else {
            qtyForAmount = quantityBase;
        }
        return qtyForAmount.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Rolls up nested article lines to stock line costs (empty nested recipe → zero).
     */
    private BigDecimal computeNestedArticleLineCost(Long ingredientArticleId, BigDecimal parentLineQuantity, Set<Long> path, int depth) {
        if (depth > MAX_NESTED_LINE_COST_DEPTH
                || parentLineQuantity == null
                || parentLineQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (!path.add(ingredientArticleId)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        List<RecipeIngredient> lines = recipeIngredientRepository.findByArticleIdOrderByProductName(ingredientArticleId);
        for (RecipeIngredient line : lines) {
            if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal scaled = line.getQuantity().multiply(parentLineQuantity);
            if (line.getProduct() != null) {
                sum = sum.add(computeLineCostAtCurrentPrice(line.getProduct(), scaled));
            } else if (line.getIngredientArticle() != null && line.getIngredientArticle().getId() != null) {
                sum = sum.add(computeNestedArticleLineCost(line.getIngredientArticle().getId(), scaled, path, depth + 1));
            }
        }
        path.remove(ingredientArticleId);
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private RecipeIngredientResponse toResponse(RecipeIngredient ri) {
        if (ri.getProduct() != null) {
            StockProduct p = ri.getProduct();
            BigDecimal lineCost = computeLineCostAtCurrentPrice(p, ri.getQuantity());
            return RecipeIngredientResponse.builder()
                    .id(ri.getId())
                    .articleId(ri.getArticle().getId())
                    .ingredientType(RecipeIngredientType.STOCK)
                    .productId(p.getId())
                    .productName(p.getName())
                    .productUnit(p.getUnit())
                    .ingredientArticleId(null)
                    .ingredientArticleName(null)
                    .quantity(ri.getQuantity())
                    .lineCostAtCurrentPrice(lineCost)
                    .createdAt(ri.getCreatedAt())
                    .build();
        }
        Article nested = ri.getIngredientArticle();
        BigDecimal lineCost = computeNestedArticleLineCost(nested.getId(), ri.getQuantity(), new HashSet<>(), 0);
        return RecipeIngredientResponse.builder()
                .id(ri.getId())
                .articleId(ri.getArticle().getId())
                .ingredientType(RecipeIngredientType.ARTICLE)
                .productId(null)
                .productName(null)
                .productUnit(null)
                .ingredientArticleId(nested.getId())
                .ingredientArticleName(nested.getName())
                .quantity(ri.getQuantity())
                .lineCostAtCurrentPrice(lineCost)
                .createdAt(ri.getCreatedAt())
                .build();
    }

    private String getIngredientDisplayName(RecipeIngredient ri) {
        if (ri.getProduct() != null && ri.getProduct().getName() != null) {
            return ri.getProduct().getName();
        }
        if (ri.getIngredientArticle() != null && ri.getIngredientArticle().getName() != null) {
            return ri.getIngredientArticle().getName();
        }
        return "";
    }
}
