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
    name = "top_product_kpi_facts",
    indexes = {
        @Index(name = "idx_fact_kpi_product_daily_store", columnList = "store_id"),
        @Index(name = "idx_fact_kpi_product_daily_date", columnList = "date_id"),
        @Index(name = "idx_fact_kpi_product_daily_store_date", columnList = "store_id,date_id"),
        @Index(name = "idx_fact_kpi_product_daily_product", columnList = "product_name")
    }
)
public class FactKpiProductDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fact_kpi_product_daily_id_seq")
    @SequenceGenerator(name = "fact_kpi_product_daily_id_seq", sequenceName = "fact_kpi_product_daily_id_seq", allocationSize = 1)
    @Column(name = "id", columnDefinition = "BIGINT DEFAULT nextval('fact_kpi_product_daily_id_seq')")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "date_id", nullable = false)
    private DateDimension date;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false)
    private Double unitPrice;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "revenue", nullable = false)
    private Double revenue;

   
}
