package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Hub-only aggregates for stock (same rules as full {@link StockInventoryItemResponse} list + to-buy filter).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSummaryResponse {

    /**
     * Sum over lines of {@code realValue != null ? realValue : (estimatedValue != null ? estimatedValue : 0)}.
     */
    private BigDecimal totalStockValue;

    /** Lines where both quantities exist and {@code |real - estimated| > 0.01}. */
    private long inventoryDiffCount;

    /** Same filter as GET /api/stock/inventory/to-buy (count only). */
    private long toBuyCount;

    /** When this snapshot was computed (server clock). */
    private OffsetDateTime generatedAt;
}
