package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.product.entities.SalesProduct;
import io.storeyes.storeyes_coffee.product.repositories.SalesProductRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.stock.entities.Article;
import io.storeyes.storeyes_coffee.stock.repositories.ArticleRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockMovementRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.repositories.StoreRepository;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sync daily sales (SalesProduct) into stock consumption movements.
 * For each SalesProduct row, we:
 * - Find the matching Article (by name, case-insensitive, same store)
 * - Create ARTICLE_SALE CONSUMPTION movements using StockConsumptionService and recipe_ingredients
 * - Mark the movement with referenceType=ARTICLE_SALE and referenceId=sales_product.id for idempotency
 *
 * This drives the "estimated" stock in getInventorySummary (PURCHASE + ADJUSTMENT + ARTICLE_SALE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSalesSyncService {

    private static final String REFERENCE_TYPE_ARTICLE_SALE = "ARTICLE_SALE";

    private final SalesProductRepository salesProductRepository;
    private final ArticleRepository articleRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockConsumptionService stockConsumptionService;
    private final StoreService storeService;
    private final StoreRepository storeRepository;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    private Long getStoreIdFromContext() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        return storeService.getStoreByOwnerId(userId).getId();
    }

    /**
     * Apply sales for a given date and store: create ARTICLE_SALE movements for each SalesProduct row.
     * Idempotent: if movements already exist for a SalesProduct (referenceId), it is skipped.
     *
     * @param storeId store to process
     * @param date sale date (matches SalesProduct.date)
     * @return number of SalesProduct rows that were converted into stock consumption (i.e. newly processed)
     */
    @Transactional
    public int applySalesForDateForStore(Long storeId, LocalDate date) {
        Long dataStoreId = demoStoreDataSourceResolver.resolveStockDataStoreId(storeId);
        List<SalesProduct> sales = salesProductRepository.findByStoreIdAndDate(dataStoreId, date);
        if (sales.isEmpty()) {
            log.info("No SalesProduct rows for store {} (data store {}) and date {}", storeId, dataStoreId, date);
            return 0;
        }

        List<Long> salesIds = sales.stream()
                .filter(Objects::nonNull)
                .map(SalesProduct::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<Long> alreadySynced = salesIds.isEmpty()
                ? Set.of()
                : new HashSet<>(stockMovementRepository.findReferenceIdsByReferenceTypeAndReferenceIdIn(
                        REFERENCE_TYPE_ARTICLE_SALE, salesIds));

        Set<String> nameKeys = sales.stream()
                .filter(sp -> sp != null && sp.getId() != null && sp.getProduct() != null && !alreadySynced.contains(sp.getId()))
                .map(sp -> sp.getProduct().getName())
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toLowerCase())
                .collect(Collectors.toSet());

        Map<String, Article> articleByLowerName = new HashMap<>();
        if (!nameKeys.isEmpty()) {
            for (Article a : articleRepository.findByStoreIdAndNameLowerTrimmedIn(dataStoreId, nameKeys)) {
                if (a.getName() == null) {
                    continue;
                }
                String key = a.getName().trim().toLowerCase();
                articleByLowerName.putIfAbsent(key, a);
            }
        }

        int processed = 0;
        for (SalesProduct sp : sales) {
            if (sp == null || sp.getId() == null || sp.getProduct() == null) {
                continue;
            }
            if (alreadySynced.contains(sp.getId())) {
                continue;
            }

            String productName = sp.getProduct().getName();
            if (productName == null || productName.isBlank()) {
                continue;
            }

            Article article = articleByLowerName.get(productName.trim().toLowerCase());
            if (article == null) {
                log.warn("No Article found for SalesProduct id={} name='{}' dataStoreId={}", sp.getId(), productName, dataStoreId);
                continue;
            }

            Integer qtyInt = sp.getQuantity();
            if (qtyInt == null || qtyInt <= 0) {
                continue;
            }
            BigDecimal quantitySold = BigDecimal.valueOf(qtyInt);

            stockConsumptionService.createConsumptionForArticleSale(
                    dataStoreId,
                    article.getId(),
                    quantitySold,
                    sp.getDate() != null ? sp.getDate() : date,
                    sp.getId()
            );
            processed++;
        }

        log.info("Applied sales for store {} (data store {}) and date {}: {} SalesProduct rows processed",
                storeId, dataStoreId, date, processed);
        return processed;
    }

    /**
     * Apply sales for a given date across all stores. Used by the cron job (no user context).
     *
     * @param date sale date (matches SalesProduct.date)
     * @return total number of SalesProduct rows processed across all stores
     */
    @Transactional
    public int applySalesForAllStores(LocalDate date) {
        List<Store> stores = storeRepository.findAll();
        int totalProcessed = 0;
        for (Store store : stores) {
            try {
                totalProcessed += applySalesForDateForStore(store.getId(), date);
            } catch (Exception e) {
                log.error("Failed to apply sales for store {} and date {}: {}", store.getId(), date, e.getMessage(), e);
            }
        }
        return totalProcessed;
    }

    /**
     * Apply sales for a given date for the current user's store. Used by the controller (requires auth).
     *
     * @param date sale date (matches SalesProduct.date)
     * @return number of SalesProduct rows processed
     */
    public int applySalesForDate(LocalDate date) {
        Long storeId = getStoreIdFromContext();
        return applySalesForDateForStore(storeId, date);
    }
}

