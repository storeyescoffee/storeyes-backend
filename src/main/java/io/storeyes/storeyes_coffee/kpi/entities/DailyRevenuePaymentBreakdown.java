package io.storeyes.storeyes_coffee.kpi.entities;

import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "daily_revenue_payment_breakdown",
    indexes = {
        @Index(name = "idx_daily_revenue_breakdown_store", columnList = "store_id"),
        @Index(name = "idx_daily_revenue_breakdown_date", columnList = "date_id"),
        @Index(name = "idx_daily_revenue_breakdown_store_date", columnList = "store_id,date_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_revenue_breakdown_store_date", columnNames = {"store_id", "date_id"})
    }
)
public class DailyRevenuePaymentBreakdown {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "daily_revenue_breakdown_id_seq")
    @SequenceGenerator(name = "daily_revenue_breakdown_id_seq", sequenceName = "daily_revenue_payment_breakdown_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "date_id", nullable = false)
    private DateDimension date;

    @Column(name = "tpe", nullable = false)
    private Double tpe;
}
