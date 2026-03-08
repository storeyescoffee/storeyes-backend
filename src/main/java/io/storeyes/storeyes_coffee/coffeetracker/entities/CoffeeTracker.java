package io.storeyes.storeyes_coffee.coffeetracker.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.storeyes.storeyes_coffee.store.entities.Store;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@JsonIgnoreProperties({"previous"})
@Table(name = "coffee_tracker", indexes = {
    @Index(name = "idx_coffee_tracker_store_id", columnList = "store_id"),
    @Index(name = "idx_coffee_tracker_store_date", columnList = "store_id, date"),
    @Index(name = "idx_coffee_tracker_store_timestamp", columnList = "store_id, timestamp")
})
public class CoffeeTracker {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "coffee_tracker_id_seq")
    @SequenceGenerator(name = "coffee_tracker_id_seq", sequenceName = "coffee_tracker_id_seq", allocationSize = 1)
    private Long id;


    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;


    @OneToOne
    @JoinColumn(name = "previous_id")
    private CoffeeTracker previous;


    @Column(name = "date", nullable = false)
    private LocalDate date;


    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;


    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "status", nullable = false)
    private TrackerStatus status;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

}
