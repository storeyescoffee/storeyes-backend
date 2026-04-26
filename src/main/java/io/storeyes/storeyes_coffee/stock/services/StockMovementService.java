package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.charges.entities.VariableCharge;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import io.storeyes.storeyes_coffee.stock.dto.ManualConsumptionRequest;
import io.storeyes.storeyes_coffee.stock.dto.SetStockRequest;
import io.storeyes.storeyes_coffee.stock.dto.SupplementStockItemRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryItemRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryRequest;
import io.storeyes.storeyes_coffee.stock.dto.StockInventoryItemResponse;
import io.storeyes.storeyes_coffee.stock.dto.StockProductSupplierBrief;
import io.storeyes.storeyes_coffee.stock.dto.StockSummaryResponse;
import io.storeyes.storeyes_coffee.stock.dto.StockToBuyItemResponse;
import io.storeyes.storeyes_coffee.stock.entities.StockMovement;
import io.storeyes.storeyes_coffee.stock.entities.StockMovementType;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.entities.SupplierStockProduct;
import io.storeyes.storeyes_coffee.stock.entities.StockInventorySession;
import io.storeyes.storeyes_coffee.stock.entities.StockInventorySnapshot;
import io.storeyes.storeyes_coffee.stock.repositories.StockInventorySessionRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockInventorySnapshotRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockMovementRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import io.storeyes.storeyes_coffee.stock.repositories.SupplierStockProductRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.services.DemoStoreDataSourceResolver;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private static final BigDecimal INVENTORY_DIFF_EPSILON = new BigDecimal("0.01");

    private static final String REFERENCE_TYPE_VARIABLE_CHARGE = "VARIABLE_CHARGE";

    private final StockMovementRepository stockMovementRepository;
    private final StockProductRepository stockProductRepository;
    private final StockInventorySessionRepository stockInventorySessionRepository;
    private final StockInventorySnapshotRepository stockInventorySnapshotRepository;
    private final SupplierStockProductRepository supplierStockProductRepository;
    private final StoreService storeService;
    private final DemoStoreDataSourceResolver demoStoreDataSourceResolver;

    @PersistenceContext
    private EntityManager entityManager;

    private Long getStoreId() {
        return CurrentStoreContext.requireCurrentStoreId();
    }

    /** Stock rows and movements for the context store, or the mapped source when the store is a demo. */
    private Long getStockDataStoreId() {
        return demoStoreDataSourceResolver.resolveStockDataStoreId(getStoreId());
    }

    private Map<Long, List<StockProductSupplierBrief>> loadSuppliersByProductId(
            Long storeId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<SupplierStockProduct> links =
                supplierStockProductRepository.findByStoreIdAndStockProduct_IdInWithSupplier(storeId, productIds);
        Map<Long, List<StockProductSupplierBrief>> map = new HashMap<>();
        for (SupplierStockProduct l : links) {
            Long pid = l.getStockProduct().getId();
            map.computeIfAbsent(pid, k -> new ArrayList<>())
                    .add(StockProductSupplierBrief.builder()
                            .id(l.getSupplier().getId())
                            .name(l.getSupplier().getName())
                            .isPreferred(l.getIsPreferred())
                            .build());
        }
        return map;
    }

    /**
     * Create or update the PURCHASE movement linked to a variable charge.
     * - One movement per variable charge (reference_type + reference_id).
     * - If the charge has no valid product/quantity, the movement is removed.
     * - Only raw-material stock products (bar, kitchen, freezer, soda) are tracked in stock.
     */
    @Transactional
    public void syncPurchaseForVariableCharge(VariableCharge charge) {
        if (charge == null || charge.getId() == null) {
            return;
        }

        Optional<StockMovement> existingOpt =
                stockMovementRepository.findByReferenceTypeAndReferenceId(REFERENCE_TYPE_VARIABLE_CHARGE, charge.getId());

        boolean hasProductAndQuantity =
                charge.getProduct() != null
                        && charge.getQuantity() != null
                        && charge.getQuantity().compareTo(BigDecimal.ZERO) > 0;

        // If we no longer have a valid product/quantity, remove the movement (undo its effect on stock)
        if (!hasProductAndQuantity) {
            existingOpt.ifPresent(stockMovementRepository::delete);
            return;
        }

        // Only track raw-material stock products in stock tables (bar, kitchen, freezer, soda).
        String subCategoryCode = null;
        if (charge.getProduct() != null
                && charge.getProduct().getSubCategory() != null
                && charge.getProduct().getSubCategory().getCode() != null) {
            subCategoryCode = charge.getProduct().getSubCategory().getCode().toLowerCase();
        }
        boolean isRawMaterialProduct = "bar".equals(subCategoryCode)
                || "kitchen".equals(subCategoryCode)
                || "freezer".equals(subCategoryCode)
                || "soda".equals(subCategoryCode);

        // If product is not a raw material, ensure no purchase movement exists and exit.
        if (!isRawMaterialProduct) {
            existingOpt.ifPresent(stockMovementRepository::delete);
            return;
        }

        StockMovement movement = existingOpt.orElseGet(() ->
                StockMovement.builder()
                        .referenceType(REFERENCE_TYPE_VARIABLE_CHARGE)
                        .referenceId(charge.getId())
                        .build()
        );

        movement.setStore(charge.getStore());
        movement.setProduct(charge.getProduct());
        movement.setType(StockMovementType.PURCHASE);
        // Charge quantity is entered in the product's display/counting unit in the UI.
        // Stock movements are stored in BASE unit for consistent calculations.
        BigDecimal baseQty = charge.getQuantity();
        if (baseQty != null
                && charge.getProduct() != null
                && charge.getProduct().getCountingUnit() != null
                && !charge.getProduct().getCountingUnit().isBlank()
                && charge.getProduct().getBasePerCountingUnit() != null
                && charge.getProduct().getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
            baseQty = baseQty.multiply(charge.getProduct().getBasePerCountingUnit());
        }
        movement.setQuantity(baseQty);
        // Prefer the charge line unit price (actual purchase) for valuation; fall back to catalog product price.
        BigDecimal unitPriceForValuation = null;
        if (charge.getUnitPrice() != null && charge.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            unitPriceForValuation = charge.getUnitPrice();
        } else if (charge.getProduct() != null && charge.getProduct().getUnitPrice() != null
                && charge.getProduct().getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            unitPriceForValuation = charge.getProduct().getUnitPrice();
        }
        BigDecimal movementAmount = BigDecimal.ZERO;
        if (unitPriceForValuation != null && baseQty != null && charge.getProduct() != null) {
            if (charge.getProduct().getBasePerCountingUnit() != null
                    && charge.getProduct().getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal countingQty = baseQty.divide(charge.getProduct().getBasePerCountingUnit(), 4, RoundingMode.HALF_UP);
                movementAmount = countingQty.multiply(unitPriceForValuation).setScale(2, RoundingMode.HALF_UP);
            } else {
                movementAmount = baseQty.multiply(unitPriceForValuation).setScale(2, RoundingMode.HALF_UP);
            }
        }
        movement.setAmount(movementAmount);
        movement.setMovementDate(charge.getDate() != null ? charge.getDate() : LocalDate.now());

        stockMovementRepository.save(movement);
    }

    /**
     * Delete all movements linked to a variable charge. Used when the charge is deleted.
     */
    @Transactional
    public void deleteMovementsForVariableCharge(Long variableChargeId) {
        if (variableChargeId == null) {
            return;
        }
        stockMovementRepository.deleteByReferenceTypeAndReferenceId(
                REFERENCE_TYPE_VARIABLE_CHARGE,
                variableChargeId
        );
    }

    /**
     * One query: per product, sum quantity and signed amount for real drift after each product's snapshot {@code createdAt}.
     */
    private Map<Long, BigDecimal[]> loadRealDriftAfterCutoffs(Long storeId, Map<Long, LocalDateTime> productIdToCutoff) {
        if (productIdToCutoff.isEmpty()) {
            return Map.of();
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<StockMovement> m = cq.from(StockMovement.class);
        Join<StockMovement, Store> storeJoin = m.join("store");
        Join<StockMovement, StockProduct> productJoin = m.join("product");

        Predicate storeEq = cb.equal(storeJoin.get("id"), storeId);
        Predicate refOk = cb.or(
                cb.isNull(m.get("referenceType")),
                cb.notEqual(m.get("referenceType"), "INVENTORY_VALIDATION"));
        Predicate typeOk = cb.or(
                m.get("type").in(Arrays.asList(StockMovementType.PURCHASE, StockMovementType.ADJUSTMENT)),
                cb.and(
                        cb.equal(m.get("type"), StockMovementType.CONSUMPTION),
                        cb.equal(m.get("referenceType"), REFERENCE_TYPE_MANUAL_CONSUMPTION)));

        List<Predicate> cutoffPreds = new ArrayList<>();
        for (Map.Entry<Long, LocalDateTime> e : productIdToCutoff.entrySet()) {
            cutoffPreds.add(cb.and(
                    cb.equal(productJoin.get("id"), e.getKey()),
                    cb.greaterThan(m.get("createdAt"), cb.literal(e.getValue()))));
        }
        Predicate cutoffOr = cb.or(cutoffPreds.toArray(Predicate[]::new));

        Expression<BigDecimal> zero = cb.literal(BigDecimal.ZERO);
        CriteriaBuilder.Case<BigDecimal> amountCase = cb.selectCase();
        Expression<BigDecimal> amountLine = amountCase
                .when(cb.or(
                                cb.equal(m.get("type"), StockMovementType.PURCHASE),
                                cb.equal(m.get("type"), StockMovementType.ADJUSTMENT)),
                        cb.coalesce(m.get("amount"), zero))
                .when(cb.and(
                                cb.equal(m.get("type"), StockMovementType.CONSUMPTION),
                                cb.equal(m.get("referenceType"), REFERENCE_TYPE_MANUAL_CONSUMPTION)),
                        cb.neg(cb.coalesce(m.get("amount"), zero)))
                .otherwise(zero);

        cq.multiselect(productJoin.get("id"), cb.sum(m.get("quantity")), cb.sum(amountLine));
        cq.where(storeEq, refOk, typeOk, cutoffOr);
        cq.groupBy(productJoin.get("id"));

        Map<Long, BigDecimal[]> out = new HashMap<>();
        for (Tuple t : entityManager.createQuery(cq).getResultList()) {
            Long pid = ((Number) t.get(0)).longValue();
            BigDecimal qty = (BigDecimal) t.get(1);
            BigDecimal amt = (BigDecimal) t.get(2);
            out.put(pid, new BigDecimal[]{
                    qty != null ? qty : BigDecimal.ZERO,
                    amt != null ? amt : BigDecimal.ZERO
            });
        }
        return out;
    }

    /**
     * Inventory summary: all store products with estimated and real quantity/value.
     * <p>
     * Estimated: PURCHASE + ADJUSTMENT + ARTICLE_SALE consumption (sales-driven).
     * Real: last snapshot + PURCHASE + ADJUSTMENT + MANUAL_CONSUMPTION (user-validated baseline + manual movements).
     * Real value = realQuantity * averageUnitCost when realQuantity exists.
     */
    public List<StockInventoryItemResponse> getInventorySummary() {
        Long storeId = getStockDataStoreId();
        List<StockProduct> allProducts = stockProductRepository.findRawMaterialProductsByStoreIdOrderByNameAsc(storeId);
        List<Object[]> estimatedRows = stockMovementRepository.getEstimatedSummaryByStore(storeId);
        Map<Long, Object[]> estimatedByProductId = estimatedRows.stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> r));

        List<Long> latestSnapshotIds = stockInventorySnapshotRepository.findLatestSnapshotIdsByStoreId(storeId);
        List<StockInventorySnapshot> latestSnapshots = latestSnapshotIds.isEmpty()
                ? List.of()
                : stockInventorySnapshotRepository.findByIdInWithProductAndSubCategory(latestSnapshotIds);
        Map<Long, StockInventorySnapshot> latestSnapshotByProductId = new LinkedHashMap<>();
        for (StockInventorySnapshot s : latestSnapshots) {
            latestSnapshotByProductId.put(s.getProduct().getId(), s);
        }

        Map<Long, LocalDateTime> cutoffByProduct = new HashMap<>();
        for (Map.Entry<Long, StockInventorySnapshot> e : latestSnapshotByProductId.entrySet()) {
            cutoffByProduct.put(e.getKey(), e.getValue().getCreatedAt());
        }
        Map<Long, BigDecimal[]> driftByProduct = loadRealDriftAfterCutoffs(storeId, cutoffByProduct);

        Map<Long, List<StockProductSupplierBrief>> suppliersByProductId =
                loadSuppliersByProductId(storeId, allProducts.stream().map(StockProduct::getId).toList());

        return allProducts.stream()
                .map(product -> {
                    Object[] r = estimatedByProductId.get(product.getId());
                    BigDecimal estimatedQuantity = BigDecimal.ZERO;
                    BigDecimal estimatedValue = BigDecimal.ZERO;
                    if (r != null) {
                        estimatedQuantity = r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO;
                        estimatedValue = r[2] != null ? new BigDecimal(r[2].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                    }

                    BigDecimal basePerCounting = product.getBasePerCountingUnit();
                    BigDecimal estimatedQuantityCounting = null;
                    if (basePerCounting != null && basePerCounting.compareTo(BigDecimal.ZERO) > 0) {
                        estimatedQuantityCounting = estimatedQuantity.divide(basePerCounting, 4, RoundingMode.HALF_UP);
                    }

                    BigDecimal averageUnitCost = BigDecimal.ZERO;
                    if (estimatedQuantity.compareTo(BigDecimal.ZERO) > 0 && estimatedValue.compareTo(BigDecimal.ZERO) > 0) {
                        averageUnitCost = estimatedValue.divide(estimatedQuantity, 4, RoundingMode.HALF_UP);
                    }

                    BigDecimal realQuantity = null;
                    BigDecimal realQuantityCounting = null;
                    BigDecimal realValue = null;
                    StockInventorySnapshot lastSnapshot = latestSnapshotByProductId.get(product.getId());
                    if (lastSnapshot != null) {
                        // Real = last snapshot + incoming/manual movements after snapshot date.
                        // We intentionally exclude ARTICLE_SALE consumption from real (sales only affect estimated),
                        // so variance appears until the owner counts and validates.
                        BigDecimal[] drift = driftByProduct.getOrDefault(
                                product.getId(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                        BigDecimal driftQty = drift[0];
                        BigDecimal driftAmt = drift[1];
                        realQuantity = lastSnapshot.getBaseQuantity().add(driftQty);
                        if (basePerCounting != null && basePerCounting.compareTo(BigDecimal.ZERO) > 0) {
                            // Always derive from realQuantity so purchases/drift are reflected (snapshot counting qty is stale).
                            realQuantityCounting = realQuantity.divide(basePerCounting, 4, RoundingMode.HALF_UP);
                        }
                        if (lastSnapshot.getAmount() != null && lastSnapshot.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                            realValue = lastSnapshot.getAmount().add(driftAmt).setScale(2, RoundingMode.HALF_UP);
                        } else if (averageUnitCost.compareTo(BigDecimal.ZERO) > 0) {
                            realValue = realQuantity.multiply(averageUnitCost).setScale(2, RoundingMode.HALF_UP);
                        }
                    } else {
                        // No validated snapshot yet: treat estimated as real so that
                        // purchases/adjustments affect both real and estimated immediately.
                        if (estimatedQuantity.compareTo(BigDecimal.ZERO) > 0) {
                            realQuantity = estimatedQuantity;
                            if (basePerCounting != null && basePerCounting.compareTo(BigDecimal.ZERO) > 0) {
                                realQuantityCounting = estimatedQuantity.divide(basePerCounting, 4, RoundingMode.HALF_UP);
                            }
                            if (estimatedValue.compareTo(BigDecimal.ZERO) > 0) {
                                realValue = estimatedValue;
                            }
                        }
                    }

                    BigDecimal varianceValue = null;
                    if (realValue != null && estimatedValue != null) {
                        varianceValue = realValue.subtract(estimatedValue).setScale(2, RoundingMode.HALF_UP);
                    }

                    return StockInventoryItemResponse.builder()
                            .productId(product.getId())
                            .productName(product.getName())
                            .unit(product.getUnit())
                            .subCategoryId(product.getSubCategory() != null ? product.getSubCategory().getId() : null)
                            .subCategoryName(product.getSubCategory() != null ? product.getSubCategory().getName() : null)
                            .countingUnit(product.getCountingUnit())
                            .basePerCountingUnit(basePerCounting)
                            .minimalThreshold(product.getMinimalThreshold())
                            .estimatedQuantity(estimatedQuantity)
                            .estimatedQuantityCounting(estimatedQuantityCounting)
                            .estimatedValue(estimatedValue)
                            .realQuantity(realQuantity)
                            .realQuantityCounting(realQuantityCounting)
                            .realValue(realValue)
                            .varianceValue(varianceValue)
                            .totalPurchaseAmount(BigDecimal.ZERO)
                            .averageUnitCost(averageUnitCost)
                            .unitPrice(product.getUnitPrice() != null ? product.getUnitPrice() : BigDecimal.ZERO)
                            .suppliers(suppliersByProductId.getOrDefault(product.getId(), List.of()))
                            .build();
                })
                .toList();
    }

    /**
     * Products that need restocking: estimated (system) quantity &lt;= minimal threshold.
     * Estimated stock follows movements and sales (same basis as inventory “system” column), not physical count.
     * Also includes products with minimalThreshold = 0 when estimated = 0 (out of stock).
     */
    public List<StockToBuyItemResponse> getToBuyList() {
        List<StockInventoryItemResponse> inventory = getInventorySummary();
        BigDecimal zero = BigDecimal.ZERO;
        return inventory.stream()
                .filter(this::isToBuyLine)
                .map(item -> {
                    BigDecimal currentQty = item.getEstimatedQuantity() != null ? item.getEstimatedQuantity() : zero;
                    BigDecimal currentCounting = item.getEstimatedQuantityCounting() != null
                            ? item.getEstimatedQuantityCounting()
                            : (item.getBasePerCountingUnit() != null && item.getBasePerCountingUnit().compareTo(zero) > 0
                                    ? currentQty.divide(item.getBasePerCountingUnit(), 4, RoundingMode.HALF_UP)
                                    : currentQty);
                    BigDecimal basePerCounting = item.getBasePerCountingUnit();
                    BigDecimal thresholdCounting = null;
                    if (basePerCounting != null && basePerCounting.compareTo(zero) > 0 && item.getMinimalThreshold() != null) {
                        thresholdCounting = item.getMinimalThreshold().divide(basePerCounting, 4, RoundingMode.HALF_UP);
                    }
                    return StockToBuyItemResponse.builder()
                            .productId(item.getProductId())
                            .productName(item.getProductName())
                            .subCategoryId(item.getSubCategoryId())
                            .subCategoryName(item.getSubCategoryName())
                            .unit(item.getUnit())
                            .countingUnit(item.getCountingUnit())
                            .basePerCountingUnit(basePerCounting)
                            .currentQuantity(currentQty)
                            .currentQuantityCounting(currentCounting)
                            .minimalThreshold(item.getMinimalThreshold())
                            .minimalThresholdCounting(thresholdCounting)
                            .suppliers(item.getSuppliers() != null ? item.getSuppliers() : List.of())
                            .build();
                })
                .sorted((a, b) -> {
                    int cat = String.valueOf(a.getSubCategoryName()).compareTo(String.valueOf(b.getSubCategoryName()));
                    return cat != 0 ? cat : String.valueOf(a.getProductName()).compareTo(String.valueOf(b.getProductName()));
                })
                .toList();
    }

    /**
     * Stock hub: total value (realValue ?? estimatedValue per line), quantity mismatch count, to-buy count.
     * Uses one {@link #getInventorySummary()} pass — same rules as the full inventory and to-buy list endpoints.
     */
    public StockSummaryResponse getStockHubSummary() {
        List<StockInventoryItemResponse> inventory = getInventorySummary();
        BigDecimal totalValue = BigDecimal.ZERO;
        long diffCount = 0;
        long toBuy = 0;
        for (StockInventoryItemResponse item : inventory) {
            totalValue = totalValue.add(hubLineValue(item));
            if (hasHubInventoryQuantityDiff(item)) {
                diffCount++;
            }
            if (isToBuyLine(item)) {
                toBuy++;
            }
        }
        return StockSummaryResponse.builder()
                .totalStockValue(totalValue.setScale(2, RoundingMode.HALF_UP))
                .inventoryDiffCount(diffCount)
                .toBuyCount(toBuy)
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    private static BigDecimal hubLineValue(StockInventoryItemResponse item) {
        if (item.getRealValue() != null) {
            return item.getRealValue();
        }
        if (item.getEstimatedValue() != null) {
            return item.getEstimatedValue();
        }
        return BigDecimal.ZERO;
    }

    private static boolean hasHubInventoryQuantityDiff(StockInventoryItemResponse item) {
        if (item.getRealQuantity() == null || item.getEstimatedQuantity() == null) {
            return false;
        }
        return item.getRealQuantity().subtract(item.getEstimatedQuantity()).abs().compareTo(INVENTORY_DIFF_EPSILON) > 0;
    }

    private boolean isToBuyLine(StockInventoryItemResponse item) {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal current = item.getEstimatedQuantity() != null ? item.getEstimatedQuantity() : zero;
        BigDecimal threshold = item.getMinimalThreshold() != null ? item.getMinimalThreshold() : zero;
        return (threshold.compareTo(zero) > 0 && current.compareTo(threshold) <= 0)
                || (threshold.compareTo(zero) <= 0 && current.compareTo(zero) == 0);
    }

    /**
     * Get current stock quantity for one product (sum of all movement quantities in base unit).
     */
    public BigDecimal getCurrentQuantity(Long storeId, Long productId) {
        List<StockMovement> movements = stockMovementRepository.findByStoreIdAndProductIdOrderByMovementDateDescIdDesc(storeId, productId);
        return movements.stream()
                .map(StockMovement::getQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final String REFERENCE_TYPE_MANUAL_ADJUSTMENT = "MANUAL_ADJUSTMENT";

    /**
     * Set current stock for a product by creating an ADJUSTMENT movement.
     * Resolves target quantity from request: quantityInBaseUnit if set, else countingQuantity * product.basePerCountingUnit.
     */
    @Transactional
    public void setStockQuantity(SetStockRequest request) {
        Long productId = request.getProductId();
        Long storeId = getStockDataStoreId();
        StockProduct product = stockProductRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Stock product not found with id: " + productId));
        if (!product.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Stock product not found with id: " + productId);
        }
        BigDecimal targetQuantityInBaseUnit = request.getQuantityInBaseUnit();
        if (targetQuantityInBaseUnit == null && request.getCountingQuantity() != null) {
            if (product.getBasePerCountingUnit() == null || product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Product has no counting unit conversion; use quantityInBaseUnit");
            }
            targetQuantityInBaseUnit = request.getCountingQuantity().multiply(product.getBasePerCountingUnit());
        }
        if (targetQuantityInBaseUnit == null) {
            throw new RuntimeException("Either quantityInBaseUnit or countingQuantity is required");
        }
        if (targetQuantityInBaseUnit.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Quantity must be 0 or positive");
        }
        BigDecimal current = getCurrentQuantity(storeId, productId);
        BigDecimal delta = targetQuantityInBaseUnit.subtract(current);
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;
        StockMovement movement = StockMovement.builder()
                .store(product.getStore())
                .product(product)
                .type(StockMovementType.ADJUSTMENT)
                .quantity(delta)
                .amount(amount)
                .movementDate(LocalDate.now())
                .referenceType(REFERENCE_TYPE_MANUAL_ADJUSTMENT)
                .referenceId(null)
                .notes("Manual stock set to " + targetQuantityInBaseUnit + " " + product.getUnit())
                .build();
        stockMovementRepository.save(movement);
    }

    private static final String REFERENCE_TYPE_MANUAL_CONSUMPTION = "MANUAL_CONSUMPTION";

    /**
     * Record manual consumption (waste, spillage, etc.).
     * Creates a CONSUMPTION movement with negative quantity.
     */
    @Transactional
    public void createManualConsumption(ManualConsumptionRequest request) {
        Long productId = request.getProductId();
        Long storeId = getStockDataStoreId();
        StockProduct product = stockProductRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Stock product not found with id: " + productId));
        if (!product.getStore().getId().equals(storeId)) {
            throw new RuntimeException("Stock product not found with id: " + productId);
        }

        BigDecimal quantityToConsume = request.getQuantityInBaseUnit();
        if (quantityToConsume == null && request.getCountingQuantity() != null) {
            if (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
                quantityToConsume = request.getCountingQuantity().multiply(product.getBasePerCountingUnit());
            } else {
                quantityToConsume = request.getCountingQuantity();
            }
        }
        if (quantityToConsume == null) {
            throw new RuntimeException("Either quantityInBaseUnit or countingQuantity is required");
        }
        if (quantityToConsume.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Quantity must be positive for consumption");
        }

        BigDecimal amount = request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;
        String notes = request.getNotes() != null && !request.getNotes().isBlank()
                ? request.getNotes().trim()
                : "Manual consumption: " + quantityToConsume + " " + product.getUnit();

        StockMovement movement = StockMovement.builder()
                .store(product.getStore())
                .product(product)
                .type(StockMovementType.CONSUMPTION)
                .quantity(quantityToConsume.negate())
                .amount(amount)
                .movementDate(LocalDate.now())
                .referenceType(REFERENCE_TYPE_MANUAL_CONSUMPTION)
                .referenceId(null)
                .notes(notes)
                .build();
        stockMovementRepository.save(movement);
    }

    private static final String REFERENCE_TYPE_MANUAL_SUPPLEMENT = "MANUAL_SUPPLEMENT";

    /**
     * Supplement stock: record incoming goods outside the variable-charge purchase flow.
     * Creates an ADJUSTMENT movement with a POSITIVE delta for each item that has deltaQty > 0.
     *
     * Real stock is computed as: last snapshot + incoming movements (PURCHASE + ADJUSTMENT + MANUAL_CONSUMPTION),
     * excluding ARTICLE_SALE consumption. Therefore, this operation increases BOTH real and estimated without variance.
     */
    @Transactional
    public void supplementStock(List<SupplementStockItemRequest> items) {
        Long storeId = getStockDataStoreId();
        Store store = storeService.getStoreEntityById(storeId);

        for (SupplementStockItemRequest item : items) {
            StockProduct product = stockProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Stock product not found: " + item.getProductId()));
            if (!product.getStore().getId().equals(storeId)) {
                throw new RuntimeException("Product not in store: " + item.getProductId());
            }

            BigDecimal deltaBase = item.getDeltaQuantityInBaseUnit();
            if (deltaBase == null && item.getDeltaCountingQuantity() != null) {
                if (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
                    deltaBase = item.getDeltaCountingQuantity().multiply(product.getBasePerCountingUnit());
                } else {
                    deltaBase = item.getDeltaCountingQuantity();
                }
            }
            if (deltaBase == null || deltaBase.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // skip products with no positive delta
            }

            // Amount: use provided value, or compute as quantity × unitPrice.
            // unitPrice is per counting unit when product has counting unit, else per base unit.
            BigDecimal deltaCountingQty = (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? deltaBase.divide(product.getBasePerCountingUnit(), 4, RoundingMode.HALF_UP)
                    : deltaBase;
            BigDecimal qtyForAmount = (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? deltaCountingQty : deltaBase;
            BigDecimal amount = item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) >= 0
                    ? item.getAmount()
                    : (product.getUnitPrice() != null && product.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                            ? qtyForAmount.multiply(product.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
            StockMovement movement = StockMovement.builder()
                    .store(store)
                    .product(product)
                    .type(StockMovementType.ADJUSTMENT)
                    .quantity(deltaBase)
                    .amount(amount)
                    .movementDate(LocalDate.now())
                    .referenceType(REFERENCE_TYPE_MANUAL_SUPPLEMENT)
                    .referenceId(null)
                    .notes("Stock supplement: +" + deltaBase + " " + product.getUnit())
                    .build();
            stockMovementRepository.save(movement);
        }
    }

    /**
     * Save physical inventory counts only: create session and snapshots, but do NOT create ADJUSTMENT movements.
     * Use this for the "Fill out the form" / "Save" step – it updates the real quantities/values visible in inventory
     * screens, while leaving estimated stock unchanged until the owner explicitly accepts validation.
     */
    @Transactional
    public void saveInventoryCounts(ValidateInventoryRequest request) {
        Long storeId = getStockDataStoreId();
        Store store = storeService.getStoreEntityById(storeId);
        LocalDateTime now = LocalDateTime.now();

        StockInventorySession session = StockInventorySession.builder()
                .store(store)
                .startedAt(now)
                .finishedAt(now)
                .notes("Inventory count (save only)")
                .build();
        session = stockInventorySessionRepository.save(session);

        for (ValidateInventoryItemRequest item : request.getItems()) {
            StockProduct product = stockProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Stock product not found: " + item.getProductId()));
            if (!product.getStore().getId().equals(storeId)) {
                throw new RuntimeException("Product not in store: " + item.getProductId());
            }

            BigDecimal targetBase = item.getQuantityInBaseUnit();
            if (targetBase == null && item.getCountingQuantity() != null) {
                if (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
                    targetBase = item.getCountingQuantity().multiply(product.getBasePerCountingUnit());
                } else {
                    // No counting unit: treat countingQuantity as base quantity (1:1, e.g. for unit/piece products)
                    targetBase = item.getCountingQuantity();
                }
            }
            if (targetBase == null) {
                throw new RuntimeException("Either quantityInBaseUnit or countingQuantity required for product: " + product.getId());
            }
            if (targetBase.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Quantity must be >= 0 for product: " + product.getId());
            }

            BigDecimal countingQty = (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? targetBase.divide(product.getBasePerCountingUnit(), 4, RoundingMode.HALF_UP)
                    : targetBase;

            // Amount: use provided value, or compute as quantity × unitPrice.
            // unitPrice is per counting unit when product has counting unit, else per base unit.
            BigDecimal qtyForAmount = (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? countingQty : targetBase;
            BigDecimal targetValue = item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) >= 0
                    ? item.getAmount()
                    : (product.getUnitPrice() != null && product.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                            ? qtyForAmount.multiply(product.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
            StockInventorySnapshot snapshot = StockInventorySnapshot.builder()
                    .session(session)
                    .product(product)
                    .countingQuantity(countingQty)
                    .baseQuantity(targetBase)
                    .amount(targetValue)
                    .build();
            stockInventorySnapshotRepository.save(snapshot);
        }
    }

    /**
     * Batch validate inventory: create session, snapshots, and ADJUSTMENT movements.
     * Sets real stock from physical count; after this, real and estimated will match until new movements occur.
     * ADJUSTMENT amount is set to (targetValue - currentEstimatedValue) so that estimated value equals real after validation.
     *
     * This is used for the explicit "Accept and validate" step.
     */
    @Transactional
    public void validateInventory(ValidateInventoryRequest request) {
        Long storeId = getStockDataStoreId();
        Store store = storeService.getStoreEntityById(storeId);
        LocalDateTime now = LocalDateTime.now();

        // Current estimated quantity and value per product (before we add new movements).
        // We use these – not getCurrentQuantity() which sums all movement types – so that the ADJUSTMENT
        // delta only corrects the estimated stock (PURCHASE + ADJUSTMENT + ARTICLE_SALE), not manual consumptions.
        List<Object[]> estimatedRows = stockMovementRepository.getEstimatedSummaryByStore(storeId);
        Map<Long, BigDecimal> currentEstimatedQtyByProductId = estimatedRows.stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).longValue(),
                        r -> r[1] != null ? new BigDecimal(r[1].toString()) : BigDecimal.ZERO
                ));
        Map<Long, BigDecimal> currentEstimatedValueByProductId = estimatedRows.stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).longValue(),
                        r -> r[2] != null ? new BigDecimal(r[2].toString()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO
                ));

        StockInventorySession session = StockInventorySession.builder()
                .store(store)
                .startedAt(now)
                .finishedAt(now)
                .notes("Inventory validation")
                .build();
        session = stockInventorySessionRepository.save(session);

        for (ValidateInventoryItemRequest item : request.getItems()) {
            StockProduct product = stockProductRepository.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Stock product not found: " + item.getProductId()));
            if (!product.getStore().getId().equals(storeId)) {
                throw new RuntimeException("Product not in store: " + item.getProductId());
            }

            BigDecimal targetBase = item.getQuantityInBaseUnit();
            if (targetBase == null && item.getCountingQuantity() != null) {
                if (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
                    targetBase = item.getCountingQuantity().multiply(product.getBasePerCountingUnit());
                } else {
                    // No counting unit: treat countingQuantity as base quantity (1:1, e.g. for unit/piece products)
                    targetBase = item.getCountingQuantity();
                }
            }
            if (targetBase == null) {
                throw new RuntimeException("Either quantityInBaseUnit or countingQuantity required for product: " + product.getId());
            }
            if (targetBase.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Quantity must be >= 0 for product: " + product.getId());
            }

            BigDecimal countingQty = (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? targetBase.divide(product.getBasePerCountingUnit(), 4, RoundingMode.HALF_UP)
                    : targetBase;

            // Amount: use provided value, or compute as quantity × unitPrice.
            // unitPrice is per counting unit when product has counting unit, else per base unit.
            BigDecimal qtyForAmount = (product.getBasePerCountingUnit() != null && product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0)
                    ? countingQty : targetBase;
            BigDecimal targetValue = item.getAmount() != null && item.getAmount().compareTo(BigDecimal.ZERO) >= 0
                    ? item.getAmount()
                    : (product.getUnitPrice() != null && product.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                            ? qtyForAmount.multiply(product.getUnitPrice()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
            StockInventorySnapshot snapshot = StockInventorySnapshot.builder()
                    .session(session)
                    .product(product)
                    .countingQuantity(countingQty)
                    .baseQuantity(targetBase)
                    .amount(targetValue)
                    .build();
            stockInventorySnapshotRepository.save(snapshot);

            // Use estimated qty/value (PURCHASE + ADJUSTMENT + ARTICLE_SALE only) as the baseline
            // so the ADJUSTMENT brings estimated = target without being skewed by manual consumptions.
            BigDecimal currentEstimatedQty = currentEstimatedQtyByProductId.getOrDefault(product.getId(), BigDecimal.ZERO);
            BigDecimal deltaQty = targetBase.subtract(currentEstimatedQty);
            BigDecimal currentEstimatedValue = currentEstimatedValueByProductId.getOrDefault(product.getId(), BigDecimal.ZERO);
            // Amount delta so that after adding this ADJUSTMENT: estimated value = target value (same as real)
            BigDecimal amountDelta = targetValue.subtract(currentEstimatedValue).setScale(2, RoundingMode.HALF_UP);
            // Create ADJUSTMENT when quantity and/or value need to change
            if (deltaQty.compareTo(BigDecimal.ZERO) != 0 || amountDelta.compareTo(BigDecimal.ZERO) != 0) {
                StockMovement movement = StockMovement.builder()
                        .store(store)
                        .product(product)
                        .type(StockMovementType.ADJUSTMENT)
                        .quantity(deltaQty)
                        .amount(amountDelta)
                        .movementDate(LocalDate.now())
                        .referenceType("INVENTORY_VALIDATION")
                        .referenceId(session.getId())
                        .notes("Inventory validation: " + targetBase + " " + product.getUnit())
                        .build();
                stockMovementRepository.save(movement);
            }
        }
    }
}
