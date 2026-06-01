package io.storeyes.storeyes_coffee.kpi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsResponseDTO {

    private StatisticsKpiCardDTO kpiCards;
    private List<RevenueDataPointDTO> revenueChart;
    private List<BestProductDTO> bestSales;
    private List<WorstProductDTO> worstSales;
}
