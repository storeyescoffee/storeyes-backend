package io.storeyes.storeyes_coffee.charges.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
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
