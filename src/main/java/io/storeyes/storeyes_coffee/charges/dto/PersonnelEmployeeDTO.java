package io.storeyes.storeyes_coffee.charges.dto;

import io.storeyes.storeyes_coffee.charges.entities.SalaryByPeriod;
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
public class PersonnelEmployeeDTO {
    private Long id;
    private String name;
    private String type;
    private String position;
    private LocalDate startDate;
    private BigDecimal salary;
    private Integer hours;
    private SalaryByPeriod salaryByPeriod;
    private BigDecimal monthSalary;
    private BigDecimal weekSalary; // Deprecated
    private Map<String, BigDecimal> weekSalaries;
    private BigDecimal daysLeftSalary;
}
