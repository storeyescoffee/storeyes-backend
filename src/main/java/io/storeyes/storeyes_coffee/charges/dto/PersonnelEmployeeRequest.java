package io.storeyes.storeyes_coffee.charges.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonnelEmployeeRequest {
    private Long id; // If provided, reuse existing employee

    @NotBlank(message = "Employee name is required")
    @Size(max = 100, message = "Employee name must not exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Type must not exceed 100 characters")
    private String type;
    
    @Size(max = 100, message = "Position must not exceed 100 characters")
    private String position;
    
    private LocalDate startDate;
    
    @Positive(message = "Salary must be positive if provided")
    private BigDecimal salary;
    
    @Positive(message = "Hours must be positive if provided")
    private Integer hours;
    
    /**
     * Map of week keys to salary amounts for updating specific week salaries.
     * Key format: "YYYY-MM-DD" (Monday date)
     * Example: { "2024-01-22": 800.00 }
     */
    private Map<String, BigDecimal> weekSalaries;
}
