package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockProductResponse {
    private Long id;
    private String name;
    /** Optional Arabic name (null if unset). */
    private String nameAr;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal minimalThreshold;
    private Long subCategoryId;
    private String subCategoryName;
    private String countingUnit;
    private BigDecimal basePerCountingUnit;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Active supplier links for this product (backoffice list/detail). */
    private List<StockProductSupplierBrief> suppliers;
}
