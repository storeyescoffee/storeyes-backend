package io.storeyes.storeyes_coffee.charges.entities;

import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "fixed_charges",
    indexes = {
        @Index(name = "idx_fixed_charges_store", columnList = "store_id"),
        @Index(name = "idx_fixed_charges_category", columnList = "category"),
        @Index(name = "idx_fixed_charges_month_key", columnList = "month_key"),
        @Index(name = "idx_fixed_charges_period", columnList = "period"),
        @Index(name = "idx_fixed_charges_store_category_month_period", columnList = "store_id,category,month_key,period")
    }
)
public class FixedCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fixed_charge_id_seq")
    @SequenceGenerator(name = "fixed_charge_id_seq", sequenceName = "fixed_charge_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "category", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChargeCategory category;

    /**
     * Custom name for fixed charges when category is OTHER (e.g. "Rent", "Insurance").
     * Null for predefined categories (PERSONNEL, WATER, ELECTRICITY, WIFI).
     */
    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "period", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private ChargePeriod period;

    @Column(name = "month_key", length = 7)
    private String monthKey;

    @Column(name = "week_key", length = 15)
    private String weekKey;

    @Column(name = "trend", length = 10)
    @Enumerated(EnumType.STRING)
    private TrendDirection trend;

    @Column(name = "trend_percentage", precision = 5, scale = 2)
    private BigDecimal trendPercentage;

    @Column(name = "abnormal_increase", columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean abnormalIncrease = false;

    @Column(name = "previous_amount", precision = 10, scale = 2)
    private BigDecimal previousAmount;

    @Column(name = "notes", length = 1000)
    private String notes;

    @OneToMany(mappedBy = "fixedCharge", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PersonnelEmployee> employees = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
