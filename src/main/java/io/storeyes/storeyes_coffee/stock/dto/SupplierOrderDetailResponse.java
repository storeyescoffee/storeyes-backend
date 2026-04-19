package io.storeyes.storeyes_coffee.stock.dto;

import io.storeyes.storeyes_coffee.stock.entities.SupplierOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierOrderDetailResponse {

    private Long id;
    private SupplierOrderStatus status;
    private LocalDate orderDate;
    private Long supplierId;
    private String supplierName;
    private String messageText;
    private BigDecimal totalAmount;
    private LocalDateTime convertedAt;
    private List<SupplierOrderLineResponse> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
