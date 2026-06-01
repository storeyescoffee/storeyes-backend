package io.storeyes.storeyes_coffee.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsKpiCardDTO {

    private double totalRevenueTtc;
    private long totalItemsSold;
    private long totalTransactions;

    /** Average revenue per period unit (day / week / month depending on granularity). */
    private double avgRevenuePerPeriod;

    /** Human-readable label for the average card: "Jour", "Sem." or "Mois". */
    private String avgLabel;

    // % changes vs previous equivalent period (null when previous period has no data)
    private Double revenuePct;
    private Double itemsSoldPct;
    private Double transactionsPct;
    private Double avgRevenuePerPeriodPct;
}
