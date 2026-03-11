package io.storeyes.storeyes_coffee.stock.services;

import io.storeyes.storeyes_coffee.charges.entities.VariableCharge;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import io.storeyes.storeyes_coffee.stock.dto.ManualConsumptionRequest;
import io.storeyes.storeyes_coffee.stock.dto.SetStockRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryItemRequest;
import io.storeyes.storeyes_coffee.stock.dto.ValidateInventoryRequest;
import io.storeyes.storeyes_coffee.stock.dto.StockInventoryItemResponse;
import io.storeyes.storeyes_coffee.stock.dto.StockToBuyItemResponse;
import io.storeyes.storeyes_coffee.stock.entities.StockMovement;
import io.storeyes.storeyes_coffee.stock.entities.StockMovementType;
import io.storeyes.storeyes_coffee.stock.entities.StockProduct;
import io.storeyes.storeyes_coffee.stock.entities.StockInventorySession;
import io.storeyes.storeyes_coffee.stock.entities.StockInventorySnapshot;
import io.storeyes.storeyes_coffee.stock.repositories.StockInventorySessionRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockInventorySnapshotRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockMovementRepository;
import io.storeyes.storeyes_coffee.stock.repositories.StockProductRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.store.services.StoreService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final StoreService storeService;

    private Long getStoreId() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            throw new RuntimeException("User is not authenticated");
        }
        return storeService.getStoreByOwnerId(userId).getId();
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
        movement.setQuantity(charge.getQuantity());
        movement.setAmount(charge.getAmount() != null ? charge.getAmount() : null);
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
                        LocalDate snapshotDate = lastSnapshot.getCreatedAt().toLocalDate();
                        BigDecimal movementsAfterQty = stockMovementRepository.sumQuantityAfterDateForReal(storeId, product.getId(), snapshotDate);
                        realQuantity = lastSnapshot.getBaseQuantity().add(movementsAfterQty != null ? movementsAfterQty : BigDecimal.ZERO);
                        if (basePerCounting != null && basePerCounting.compareTo(BigDecimal.ZERO) > 0) {
                            realQuantityCounting = realQuantity.divide(basePerCounting, 4, RoundingMode.HALF_UP);
                        }
                        // Real value: from stored amounts when snapshot has amount, else realQuantity * averageUnitCost
                        if (lastSnapshot.getAmount() != null) {
                            BigDecimal amountAfter = stockMovementRepository.sumAmountAfterDateForReal(storeId, product.getId(), snapshotDate);
                            realValue = lastSnapshot.getAmount().add(amountAfter != null ? amountAfter : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                        } else if (averageUnitCost.compareTo(BigDecimal.ZERO) > 0) {
                            realValue = realQuantity.multiply(averageUnitCost).setScale(2, RoundingMode.HALF_UP);
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
                            .build();
                })
                .toList();
    }

    /**
     * Products that need restocking: real current quantity &lt;= minimal threshold only.
     * Products without a real quantity (no snapshot yet) are excluded. Used by To Buy screen.
     */
    public List<StockToBuyItemResponse> getToBuyList() {
        List<StockInventoryItemResponse> inventory = getInventorySummary();
        BigDecimal zero = BigDecimal.ZERO;
        return inventory.stream()
                .filter(item -> item.getMinimalThreshold() != null && item.getMinimalThreshold().compareTo(zero) > 0)
                .filter(item -> item.getRealQuantity() != null && item.getRealQuantity().compareTo(item.getMinimalThreshold()) <= 0)
                .map(item -> {
                    BigDecimal currentQty = item.getRealQuantity();
                    BigDecimal currentCounting = item.getRealQuantityCounting();
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
            if (product.getBasePerCountingUnit() == null || product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Product has no counting unit conversion; use quantityInBaseUnit");
            }
            quantityToConsume = request.getCountingQuantity().multiply(product.getBasePerCountingUnit());
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

    /**
     * Batch validate inventory: create session, snapshots, and ADJUSTMENT movements.
     * Sets real stock from physical count; after this, real and estimated will match until new movements occur.
     */
    @Transactional
    public void validateInventory(ValidateInventoryRequest request) {
        Long storeId = getStoreId();
        Store store = storeService.getStoreEntityById(storeId);
        LocalDateTime now = LocalDateTime.now();

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
                if (product.getBasePerCountingUnit() == null || product.getBasePerCountingUnit().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new RuntimeException("Product has no counting unit; use quantityInBaseUnit: " + product.getId());
                }
                targetBase = item.getCountingQuantity().multiply(product.getBasePerCountingUnit());
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

            BigDecimal snapshotAmount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
            StockInventorySnapshot snapshot = StockInventorySnapshot.builder()
                    .session(session)
                    .product(product)
                    .countingQuantity(countingQty)
                    .baseQuantity(targetBase)
                    .amount(snapshotAmount)
                    .build();
            stockInventorySnapshotRepository.save(snapshot);

            BigDecimal current = getCurrentQuantity(storeId, product.getId());
            BigDecimal delta = targetBase.subtract(current);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                StockMovement movement = StockMovement.builder()
                        .store(store)
                        .product(product)
                        .type(StockMovementType.ADJUSTMENT)
                        .quantity(delta)
                        .amount(amount)
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
