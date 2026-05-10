package io.storeyes.storeyes_coffee.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiDTO {
    private BigDecimal revenue;
    private BigDecimal charges;
    private BigDecimal profit;
    private BigDecimal profitTpe;
    private BigDecimal profitCash;
    private BigDecimal revenueEvolution;
    private BigDecimal chargesPercentage;
    private BigDecimal profitPercentage;
    private String chargesStatus;
    private String profitStatus;
}
