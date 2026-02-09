package io.storeyes.storeyes_coffee.charges.dto;

import io.storeyes.storeyes_coffee.charges.entities.ChargeCategory;
import io.storeyes.storeyes_coffee.charges.entities.ChargePeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class FixedChargeCreateRequest {
    
    @NotNull(message = "Category is required")
    private ChargeCategory category;

    /**
     * Custom name for fixed charge when category is OTHER (e.g. "Rent", "Insurance").
     * Required when category is OTHER; ignored otherwise.
     */
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;
    
    @Positive(message = "Amount must be positive")
    private BigDecimal amount; // Optional, can be calculated from employees
    
    @NotNull(message = "Period is required")
    private ChargePeriod period;
    
    @NotBlank(message = "Month key is required")
    @Size(min = 7, max = 7, message = "Month key must be in format YYYY-MM")
    private String monthKey;
    
    private String weekKey; // Required if period is WEEK
    
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    @Valid
    private List<PersonnelEmployeeRequest> employees; // Required if category is PERSONNEL
}
