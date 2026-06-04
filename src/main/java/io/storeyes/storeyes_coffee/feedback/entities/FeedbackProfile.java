package io.storeyes.storeyes_coffee.feedback.entities;

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
@Table(name = "feedback_profiles")
public class FeedbackProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feedback_profile_id_seq")
    @SequenceGenerator(name = "feedback_profile_id_seq", sequenceName = "feedback_profile_id_seq", allocationSize = 1)
    private Long id;

    @OneToOne
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "google_review_url", nullable = false, length = 2048)
    private String googleReviewUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
