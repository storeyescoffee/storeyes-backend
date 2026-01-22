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

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class FixedChargeResponse {
    private Long id;
    private ChargeCategory category;
    private BigDecimal amount;
    private ChargePeriod period;
    private String monthKey;
    private String weekKey;
    private TrendDirection trend;
    private BigDecimal trendPercentage;
    private Boolean abnormalIncrease;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
