package io.storeyes.storeyes_coffee.charges.entities;

import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "personnel_types",
    indexes = {
        @Index(name = "idx_personnel_types_store", columnList = "store_id"),
        @Index(name = "idx_personnel_types_store_active", columnList = "store_id,is_active")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_personnel_type_store_name", columnNames = {"store_id", "name"})
    }
)
public class PersonnelType {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "personnel_type_id_seq")
    @SequenceGenerator(name = "personnel_type_id_seq", sequenceName = "personnel_type_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
