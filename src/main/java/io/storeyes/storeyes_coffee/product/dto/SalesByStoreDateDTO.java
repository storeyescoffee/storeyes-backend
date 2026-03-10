package io.storeyes.storeyes_coffee.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesByStoreDateDTO {

    private Long id;
    private String productCode;
    private String productName;
    private Integer quantity;
    private Double price;
    private Double totalPrice;
}
