package io.storeyes.storeyes_coffee.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorstProductDTO {

    private int rank;
    private String productName;
    private long quantitySold;
    private double revenue;

    /**
     * % change in revenue vs the previous equivalent period.
     * Null when the product did not exist in the previous period.
     */
    private Double trendPct;
}
