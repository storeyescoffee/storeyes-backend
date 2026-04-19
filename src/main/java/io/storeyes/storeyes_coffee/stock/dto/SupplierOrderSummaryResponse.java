package io.storeyes.storeyes_coffee.stock.dto;

import io.storeyes.storeyes_coffee.stock.entities.SupplierOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierOrderSummaryResponse {

    private Long id;
    private SupplierOrderStatus status;
    private LocalDate orderDate;
    private Long supplierId;
    private String supplierName;
    private int lineCount;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
