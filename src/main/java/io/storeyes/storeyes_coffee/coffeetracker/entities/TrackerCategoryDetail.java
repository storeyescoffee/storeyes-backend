package io.storeyes.storeyes_coffee.coffeetracker.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tracker_category_details")
public class TrackerCategoryDetail {
    

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "category_details_id_seq")
    @SequenceGenerator(name = "category_details_id_seq", sequenceName = "category_details_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "coffee_tracker_id", nullable = false)
    private CoffeeTracker coffeeTracker;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
}
