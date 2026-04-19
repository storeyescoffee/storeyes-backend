package io.storeyes.storeyes_coffee.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvertSupplierOrderRequest {

    /** Charge date for each generated variable charge; defaults to today. */
    private LocalDate chargeDate;

    /**
     * When true, each created stock variable charge may update catalog unit price
     * (same semantics as variable charge create).
     */
    private Boolean updateStockProductUnitPrice;
}
