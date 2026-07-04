package io.storeyes.storeyes_coffee.charges.entities;

import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "employees",
    indexes = {
        @Index(name = "idx_employees_store", columnList = "store_id"),
        @Index(name = "idx_employees_type", columnList = "type"),
        @Index(name = "idx_employees_store_name_type", columnList = "store_id,name,type")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_store_name_type", columnNames = {"store_id", "name", "type", "start_date"})
    }
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "employee_id_seq")
    @SequenceGenerator(name = "employee_id_seq", sequenceName = "employee_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", length = 100)
    private String type;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "start_date")
    @Temporal(TemporalType.DATE)
    private LocalDate startDate;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<ChargeEmployee> chargeEmployees = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @org.hibernate.annotations.CreationTimestamp
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @org.hibernate.annotations.UpdateTimestamp
    private java.time.LocalDateTime updatedAt;
}
