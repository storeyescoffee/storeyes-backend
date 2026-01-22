package io.storeyes.storeyes_coffee.charges.dto;

import io.storeyes.storeyes_coffee.charges.entities.ChargeCategory;
import io.storeyes.storeyes_coffee.charges.entities.ChargePeriod;
import io.storeyes.storeyes_coffee.charges.entities.TrendDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FixedChargeDetailResponse extends FixedChargeResponse {
    private BigDecimal previousAmount;
    private String notes;
    private List<PersonnelDataDTO> personnelData;
    private List<ChartDataDTO> chartData;
}
