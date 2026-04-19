package io.storeyes.storeyes_coffee.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class CreateSupplierOrderRequest {

    /** Full WhatsApp-style message; stored as TEXT (unbounded in practice). */
    private String messageText;

    private Long supplierId;

    /** Used when supplierId is null or for display snapshot. */
    private String supplierNameSnapshot;

    private LocalDate orderDate;

    @NotEmpty(message = "At least one order line is required")
    @Valid
    private List<SupplierOrderLineRequest> lines;
}
