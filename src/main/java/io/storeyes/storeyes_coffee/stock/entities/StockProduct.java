package io.storeyes.storeyes_coffee.stock.entities;

import io.storeyes.storeyes_coffee.charges.entities.VariableChargeSubCategory;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "stock_products",
    indexes = {
        @Index(name = "idx_stock_products_store_id", columnList = "store_id"),
        @Index(name = "idx_stock_products_sub_category_id", columnList = "sub_category_id"),
        @Index(name = "idx_stock_products_store_name", columnList = "store_id,name")
    }
)
public class StockProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_category_id", nullable = false)
    private VariableChargeSubCategory subCategory;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Optional Arabic name for display and search (nullable). */
    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "unit", nullable = false, length = 50)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Builder.Default
    @Column(name = "minimal_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimalThreshold = BigDecimal.ZERO;

    @Column(name = "counting_unit", length = 50)
    private String countingUnit;

    @Column(name = "base_per_counting_unit", precision = 12, scale = 4)
    private BigDecimal basePerCountingUnit;

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
