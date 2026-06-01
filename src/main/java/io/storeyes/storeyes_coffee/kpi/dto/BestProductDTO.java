package io.storeyes.storeyes_coffee.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BestProductDTO {

    private int rank;
    private String productName;
    private long quantitySold;
    private double revenue;

    /**
     * Relative performance score in [0.0, 1.0].
     * The top-revenue product always has 1.0; others are proportional.
     */
    private double performanceScore;
}
