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
    name = "fact_kpi_category_daily",
    indexes = {
        @Index(name = "idx_fact_kpi_category_daily_store", columnList = "store_id"),
        @Index(name = "idx_fact_kpi_category_daily_date", columnList = "date_id"),
        @Index(name = "idx_fact_kpi_category_daily_store_date", columnList = "store_id,date_id"),
        @Index(name = "idx_fact_kpi_category_daily_category", columnList = "category")
    }
)
public class FactKpiCategoryDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fact_kpi_category_daily_id_seq")
    @SequenceGenerator(name = "fact_kpi_category_daily_id_seq", sequenceName = "fact_kpi_category_daily_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "date_id", nullable = false)
    private DateDimension date;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "transactions", nullable = false)
    private Integer transactions;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "revenue", nullable = false)
    private Double revenue;

}
