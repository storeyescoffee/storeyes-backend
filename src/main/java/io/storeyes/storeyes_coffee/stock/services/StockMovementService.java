package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.charges.entities.VariableCharge;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.stock.dto.ManualConsumptionRequest;
import io.storeyes.storeyes_coffee.stock.dto.SetStockRequest;
import io.storeyes.storeyes_coffee.stock.dto.SupplementStockItemRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryItemRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryRequest;
import io.storeyes.storeyes_coffee.stock.dto.StockInventoryItemResponse;
import io.storeyes.storeyes_coffee.stock.dto.StockProductSupplierBrief;
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
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private static final String REFERENCE_TYPE_VARIABLE_CHARGE = "VARIABLE_CHARGE";

    private final StockMovementRepository stockMovementRepository;
    private final StockProductRepository stockProductRepository;
    private final StockInventorySessionRepository stockInventorySessionRepository;
    private final StockInventorySnapshotRepository stockInventorySnapshotRepository;
    private final SupplierStockProductRepository supplierStockProductRepository;
    private final StoreService storeService;

    private Long getStoreId() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        return storeService.getStoreByOwnerId(userId).getId();
    }

    private Map<Long, List<StockProductSupplierBrief>> loadSuppliersByProductId(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<SupplierStockProduct> links =
                supplierStockProductRepository.findByStockProduct_IdInWithSupplier(productIds);
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
        // Stock movement amount = quantity × unit price (for stock valuation).
        // Charge service keeps the manual amount for accounting; stock uses calculated for consistency.
        BigDecimal movementAmount = BigDecimal.ZERO;
        if (charge.getProduct() != null && charge.getProduct().getUnitPrice() != null
                && charge.getProduct().getUnitPrice().compareTo(BigDecimal.ZERO) > 0 && baseQty != null) {
            if (charge.getProduct().getBasePerCountingUnit() != null
                    && charge.getProduct().getBasePerCountingUnit().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal countingQty = baseQty.divide(charge.getProduct().getBasePerCountingUnit(), 4, RoundingMode.HALF_UP);
                movementAmount = countingQty.multiply(charge.getProduct().getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            } else {
                movementAmount = baseQty.multiply(charge.getProduct().getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
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
     * Inventory summary: all store products with estimated and real quantity/value.
     * <p>
     * Estimated: PURCHASE + ADJUSTMENT + ARTICLE_SALE consumption (sales-driven).
     * Real: last snapshot + PURCHASE + ADJUSTMENT + MANUAL_CONSUMPTION (user-validated baseline + manual movements).
     * Real value = realQuantity * averageUnitCost when realQuantity exists.
     */
    public List<StockInventoryItemResponse> getInventorySummary() {
        Long storeId = getStoreId();
        List<StockProduct> allProducts = stockProductRepository.findByStoreIdOrderByNameAsc(storeId).stream()
                .filter(p -> {
                    if (p.getSubCategory() == null || p.getSubCategory().getCode() == null) return false;
                    String code = p.getSubCategory().getCode().toLowerCase();
                    return "bar".equals(code) || "kitchen".equals(code) || "freezer".equals(code) || "soda".equals(code);
                })
                .toList();
        List<Object[]> estimatedRows = stockMovementRepository.getEstimatedSummaryByStore(storeId);
        Map<Long, Object[]> estimatedByProductId = estimatedRows.stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> r));

        List<StockInventorySnapshot> allSnapshots = stockInventorySnapshotRepository.findBySessionStoreIdOrderByCreatedAtDesc(storeId);
        Map<Long, StockInventorySnapshot> latestSnapshotByProductId = new LinkedHashMap<>();
        for (StockInventorySnapshot s : allSnapshots) {
            latestSnapshotByProductId.putIfAbsent(s.getProduct().getId(), s);
        }

        Map<Long, List<StockProductSupplierBrief>> suppliersByProductId =
                loadSuppliersByProductId(allProducts.stream().map(StockProduct::getId).toList());

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
                        java.time.LocalDateTime afterCreatedAt = lastSnapshot.getCreatedAt();
                        BigDecimal driftQty = stockMovementRepository.sumQuantityAfterCreatedAtForReal(storeId, product.getId(), afterCreatedAt);
                        BigDecimal driftAmt = stockMovementRepository.sumAmountAfterCreatedAtForReal(storeId, product.getId(), afterCreatedAt);
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
     * Products that need restocking: real current quantity &lt;= minimal threshold.
     * Includes products with no real quantity yet (null treated as 0).
     * Also includes products with minimalThreshold = 0 when current = 0 (out of stock).
     */
    public List<StockToBuyItemResponse> getToBuyList() {
        List<StockInventoryItemResponse> inventory = getInventorySummary();
        BigDecimal zero = BigDecimal.ZERO;
        return inventory.stream()
                .filter(item -> {
                    BigDecimal current = item.getRealQuantity() != null ? item.getRealQuantity() : zero;
                    BigDecimal threshold = item.getMinimalThreshold() != null ? item.getMinimalThreshold() : zero;
                    return (threshold.compareTo(zero) > 0 && current.compareTo(threshold) <= 0)
                            || (threshold.compareTo(zero) <= 0 && current.compareTo(zero) == 0);
                })
                .map(item -> {
                    BigDecimal currentQty = item.getRealQuantity() != null ? item.getRealQuantity() : zero;
                    BigDecimal currentCounting = item.getRealQuantityCounting() != null
                            ? item.getRealQuantityCounting()
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
        Long storeId = getStoreId();
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
        Long storeId = getStoreId();
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
        Long storeId = getStoreId();
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
        Long storeId = getStoreId();
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
        Long storeId = getStoreId();
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
