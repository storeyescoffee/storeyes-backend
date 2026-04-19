package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierOrderLineResponse {

    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPriceSnapshot;
    private BigDecimal lineAmount;
    private Integer sortIndex;
}
