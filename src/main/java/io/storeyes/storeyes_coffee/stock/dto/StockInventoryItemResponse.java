package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product with estimated (system) and real (validated count) stock.
 * Value is amount-based (average cost from PURCHASE movements), not product.unit_price.
 * unit = base unit (g, cl, ml, piece). countingUnit = human unit (kg, bottle, plateau).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockInventoryItemResponse {
    private Long productId;
    private String productName;
    private String unit;
    private Long subCategoryId;
    private String subCategoryName;
    /** Base unit (g, cl, ml, piece). Match mobile unit mapping. */
    private String countingUnit;
    /** Conversion: 1 counting unit = basePerCountingUnit base units. Null when no counting unit. */
    private BigDecimal basePerCountingUnit;
    private BigDecimal minimalThreshold;

    /** Estimated quantity (sum of all movements, base unit). */
    private BigDecimal estimatedQuantity;
    /** Estimated in human unit for display. */
    private BigDecimal estimatedQuantityCounting;
    /** Estimated value (MAD): estimatedQuantity * averageUnitCost. */
    private BigDecimal estimatedValue;

    /** Real quantity (last snapshot + movements after, base unit). Null if no snapshot. */
    private BigDecimal realQuantity;
    /** Real in human unit for display. */
    private BigDecimal realQuantityCounting;
    /** Real value (MAD): realQuantity * averageUnitCost. */
    private BigDecimal realValue;

    /** Variance value (MAD): realValue - estimatedValue. */
    private BigDecimal varianceValue;

    private BigDecimal totalPurchaseAmount;
    private BigDecimal averageUnitCost;
    /** Unit price from stock product (per base unit). Used for amount = quantity × unitPrice. */
    private BigDecimal unitPrice;

    /** Suppliers linked to this stock product in the store (same as backoffice). */
    private List<StockProductSupplierBrief> suppliers;

}
