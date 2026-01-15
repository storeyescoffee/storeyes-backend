package io.storeyes.storeyes_coffee.kpi.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import io.storeyes.storeyes_coffee.store.entities.Store;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "fact_kpi_daily",
    indexes = {
        @Index(name = "idx_fact_kpi_daily_store", columnList = "store_id"),
        @Index(name = "idx_fact_kpi_daily_date", columnList = "date_id"),
        @Index(name = "idx_fact_kpi_daily_store_date", columnList = "store_id,date_id")
    }
)
public class FactKpiDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fact_kpi_daily_id_seq")
    @SequenceGenerator(name = "fact_kpi_daily_id_seq", sequenceName = "fact_kpi_daily_id_seq", allocationSize = 1)
    @Column(name = "id", columnDefinition = "BIGINT DEFAULT nextval('fact_kpi_daily_id_seq')")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "date_id", nullable = false)
    private DateDimension date;

    @Column(name = "total_revenue_ht", nullable = false)
    private Double totalRevenueHt;

    @Column(name = "total_revenue_ttc", nullable = false)
    private Double totalRevenueTtc;

    @Column(name = "total_tax", nullable = false)
    private Double totalTax;

    @Column(name = "transactions", nullable = false)
    private Integer transactions;

    @Column(name = "total_revenue", nullable = false)
    private Double totalRevenue;

    @Column(name = "total_items_sold", nullable = false)
    private Integer totalItemsSold;

    @Column(name = "avg_transaction_value", nullable = false)
    private Double averageTransactionValue;
    
}

