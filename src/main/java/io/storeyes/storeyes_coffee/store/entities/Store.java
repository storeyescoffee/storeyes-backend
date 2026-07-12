package io.storeyes.storeyes_coffee.store.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "stores")
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "store_id_seq")
    @SequenceGenerator(name = "store_id_seq", sequenceName = "store_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "coordinates", nullable = false)
    private double[] coordinates;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "status", nullable = false)
    private StoreStatus status;

    // Per-store visibility of alert types shown in the mobile app (see V26 migration)
    @Builder.Default
    @Column(name = "not_tapped_alerts_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean notTappedAlertsEnabled = true;

    @Builder.Default
    @Column(name = "return_alerts_enabled", nullable = false, columnDefinition = "boolean not null default true")
    private boolean returnAlertsEnabled = true;

    // Per-store availability of the multi-questions feedback feature (see V30 migration)
    @Builder.Default
    @Column(name = "multiple_questions_enabled", nullable = false, columnDefinition = "boolean not null default false")
    private boolean multipleQuestionsEnabled = false;

    /**
     * Real-world date the store's hardware/cameras were installed (see V28 migration).
     * Anchor for {@link #alertsActivationDate}'s default and for the activation progress
     * calculation; independent from {@link #createdAt} since the DB row may be created
     * before or after the actual installation. Defaults to now() in {@link #prePersist()}.
     * Managed via SQL, e.g. {@code UPDATE stores SET installation_date = '2026-06-20' WHERE code = '...';}
     */
    @Column(name = "installation_date")
    private LocalDateTime installationDate;

    /**
     * Alerts stay locked until this date (default: {@link #installationDate} + 3 weeks, set
     * in {@link #prePersist()}). Null is treated as active (legacy stores created before V27).
     * Managed via SQL, e.g. {@code UPDATE stores SET alerts_activation_date = NOW() WHERE code = '...';}
     */
    @Column(name = "alerts_activation_date")
    private LocalDateTime alertsActivationDate;

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @UpdateTimestamp
    private LocalDateTime updatedAt;


    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = StoreStatus.NEW;
        }
        if (this.installationDate == null) {
            this.installationDate = LocalDateTime.now();
        }
        if (this.alertsActivationDate == null) {
            this.alertsActivationDate = this.installationDate.plusWeeks(3);
        }
    }

}