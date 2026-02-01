package io.storeyes.storeyes_coffee.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoffeeSalesHourlyDTO {
    
    private Long id;
    private LocalDate saleDate;
    private Integer hour;
    private LocalTime saleTime;
    private String coffeeName;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
    private String category;
    private LocalDateTime createdAt;
    private String coffeeShopName;
    
    /**
     * Get soldAt timestamp by combining saleDate and saleTime
     */
    public LocalDateTime getSoldAt() {
        if (saleDate == null || saleTime == null) {
            return null;
        }
        return LocalDateTime.of(saleDate, saleTime);
    }
}

