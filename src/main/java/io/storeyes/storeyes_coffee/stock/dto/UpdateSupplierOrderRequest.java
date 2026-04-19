package io.storeyes.storeyes_coffee.stock.dto;

import io.storeyes.storeyes_coffee.stock.entities.SupplierOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSupplierOrderRequest {

    @NotNull(message = "Message text is required")
    private String messageText;

    private Long supplierId;

    private String supplierNameSnapshot;

    private LocalDate orderDate;

    /** When set, only DRAFT→SENT is allowed (service validates). */
    private SupplierOrderStatus status;

    @NotEmpty(message = "At least one order line is required")
    @Valid
    private List<SupplierOrderLineRequest> lines;
}
