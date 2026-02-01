package io.storeyes.storeyes_coffee.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesDTO {
    
    private Long id;
    private LocalDateTime soldAt;
    private String productName;
    private Double quantity;
    private Double price;
    private Double totalPrice;
    private String category;
    private LocalDateTime createdAt;
}

