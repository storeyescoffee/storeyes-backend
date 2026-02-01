package io.storeyes.storeyes_coffee.sales.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessSalesResponse {
    
    private String message;
    private Long storeId;
    private LocalDate date;
    private String status; // "PROCESSING" or "ACCEPTED"
}

