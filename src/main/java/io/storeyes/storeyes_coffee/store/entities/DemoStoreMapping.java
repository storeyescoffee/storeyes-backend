package io.storeyes.storeyes_coffee.store.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Configuration for a store shown as a demo: each data domain can be sourced from a different real store.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "demo_store_mappings",
        uniqueConstraints = @UniqueConstraint(name = "uk_demo_store_mappings_demo_store", columnNames = "demo_store_id")
)
public class DemoStoreMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "demo_store_mapping_id_seq")
    @SequenceGenerator(name = "demo_store_mapping_id_seq", sequenceName = "demo_store_mapping_id_seq", allocationSize = 1)
    private Long id;

    /** Store that acts as the demo (UI / tenant context). */
    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "demo_store_id", nullable = false)
    private Store demoStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alerts_source_store_id")
    private Store alertsSourceStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kpi_source_store_id")
    private Store kpiSourceStore;

    /**
     * Augmentation factor added to 1 when scaling KPI revenue and quantities in demo (e.g. 0.1 → multiply by 1.1).
     */
    @Column(name = "kpi_augmentation_percent", precision = 7, scale = 3)
    private BigDecimal kpiAugmentationPercent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_source_store_id")
    private Store stockSourceStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charges_source_store_id")
    private Store chargesSourceStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "access_source_store_id")
    private Store accessSourceStore;

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
