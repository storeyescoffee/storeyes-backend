package io.storeyes.storeyes_coffee.charges.entities;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "personnel_employees",
    indexes = {
        @Index(name = "idx_personnel_employees_fixed_charge", columnList = "fixed_charge_id"),
        @Index(name = "idx_personnel_employees_employee", columnList = "employee_id"),
        @Index(name = "idx_personnel_employees_type", columnList = "type")
    }
)
public class PersonnelEmployee {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "personnel_employee_id_seq")
    @SequenceGenerator(name = "personnel_employee_id_seq", sequenceName = "personnel_employee_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fixed_charge_id", nullable = false)
    private FixedCharge fixedCharge;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "start_date")
    @Temporal(TemporalType.DATE)
    private LocalDate startDate;

    @Column(name = "salary", precision = 10, scale = 2)
    private BigDecimal salary;

    @Column(name = "hours")
    private Integer hours;

    @Column(name = "salary_by_period", length = 10)
    @Enumerated(EnumType.STRING)
    private SalaryByPeriod salaryByPeriod;

    @Column(name = "week_salary", precision = 10, scale = 2)
    private BigDecimal weekSalary; // Deprecated, kept for backward compatibility (for weekly period charges)

    @Column(name = "month_salary", precision = 10, scale = 2)
    private BigDecimal monthSalary;

    @Column(name = "days_left_salary", precision = 10, scale = 2)
    @Deprecated
    private BigDecimal daysLeftSalary; // Deprecated - not used in new structure (all weeks are full weeks)

    @Column(name = "week_salaries", columnDefinition = "TEXT")
    @Deprecated
    private String weekSalariesJson; // Deprecated - kept for backward compatibility, use PersonnelWeekSalary instead

    @OneToMany(mappedBy = "personnelEmployee", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PersonnelWeekSalary> weekSalariesList = new ArrayList<>();

    // Transient field for easier access (computed from PersonnelWeekSalary records)
    @Transient
    @Builder.Default
    private Map<String, BigDecimal> weekSalaries = new HashMap<>();

    /**
     * Convert weekSalaries map to JSON string before persisting (backward compatibility)
     * NOTE: This is deprecated. New implementation uses PersonnelWeekSalary table.
     */
    @PrePersist
    @PreUpdate
    public void convertWeekSalariesToJson() {
        try {
            // For backward compatibility, also store in JSON if weekSalaries map is manually set
            // But primary storage is in PersonnelWeekSalary table
            if (weekSalaries != null && !weekSalaries.isEmpty()) {
                this.weekSalariesJson = objectMapper.writeValueAsString(weekSalaries);
            } else if (weekSalariesJson == null) {
                this.weekSalariesJson = "{}";
            }
        } catch (Exception e) {
            // Silent fail for backward compatibility
            if (this.weekSalariesJson == null) {
                this.weekSalariesJson = "{}";
            }
        }
    }

    /**
     * Convert JSON string to weekSalaries map after loading (backward compatibility)
     * NOTE: New implementation loads from PersonnelWeekSalary records.
     * This method will be called after @PostLoad, so we compute from weekSalariesList first,
     * then fall back to JSON if weekSalariesList is empty.
     */
    @PostLoad
    public void convertJsonToWeekSalaries() {
        try {
            // First, try to compute from PersonnelWeekSalary records (new structure)
            if (weekSalariesList != null && !weekSalariesList.isEmpty()) {
                this.weekSalaries = weekSalariesList.stream()
                        .collect(Collectors.toMap(
                                PersonnelWeekSalary::getWeekKey,
                                PersonnelWeekSalary::getAmount,
                                (existing, replacement) -> replacement
                        ));
            } else if (weekSalariesJson != null && !weekSalariesJson.isEmpty()) {
                // Fallback to JSON for backward compatibility
                TypeReference<Map<String, BigDecimal>> typeRef = new TypeReference<Map<String, BigDecimal>>() {};
                this.weekSalaries = objectMapper.readValue(weekSalariesJson, typeRef);
            } else {
                this.weekSalaries = new HashMap<>();
            }
        } catch (Exception e) {
            this.weekSalaries = new HashMap<>();
        }
    }
}
