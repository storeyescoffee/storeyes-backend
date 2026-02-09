package io.storeyes.storeyes_coffee.charges.dto;

import io.storeyes.storeyes_coffee.charges.entities.ChargePeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedChargeUpdateRequest {
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    @Positive(message = "Amount must be positive if provided")
    private BigDecimal amount;
    
    private ChargePeriod period;
    
    @Size(min = 7, max = 7, message = "Month key must be in format YYYY-MM")
    private String monthKey;
    
    @Size(max = 15, message = "Week key format invalid")
    private String weekKey;
    
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    @Valid
    private List<PersonnelEmployeeRequest> employees;
}
