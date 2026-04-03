package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product that needs restocking: current (real) quantity &lt;= minimal threshold.
 * Used by GET /api/stock/inventory/to-buy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockToBuyItemResponse {

    private Long productId;
    private String productName;
    private Long subCategoryId;
    private String subCategoryName;
    private String unit;
    private String countingUnit;
    private BigDecimal basePerCountingUnit;

    /** Current quantity in base unit (real if available, else estimated). */
    private BigDecimal currentQuantity;
    /** Current quantity in human unit for display (e.g. kg, L, piece). */
    private BigDecimal currentQuantityCounting;
    /** Threshold below which restock is needed (base unit). */
    private BigDecimal minimalThreshold;
    /** Threshold in human unit for display when applicable. */
    private BigDecimal minimalThresholdCounting;

    /** Suppliers linked to this product (for ordering / filtering on mobile). */
    private List<StockProductSupplierBrief> suppliers;
}
