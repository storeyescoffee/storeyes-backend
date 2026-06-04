package io.storeyes.storeyes_coffee.feedback.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@Table(name = "feedbacks")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feedback_id_seq")
    @SequenceGenerator(name = "feedback_id_seq", sequenceName = "feedback_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    @JsonIgnoreProperties({"feedbacks"})
    private Store store;

    /** Boolean rating: GOOD or BAD */
    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private FeedbackRating rating;

    /** Optional free-text comment from the customer */
    @Column(name = "comment", length = 2048)
    private String comment;

    /** True if the customer is a visiting/tourist customer, false if regular */
    @Column(name = "is_visiting", nullable = false)
    private boolean isVisiting;

    /** True if the feedback was submitted from a mobile device */
    @Column(name = "is_mobile", nullable = false)
    private boolean isMobile;

    /** Language used by the customer: AR, FR, or EN */
    @Enumerated(EnumType.STRING)
    @Column(name = "language", length = 2)
    private FeedbackLanguage language;

    /** When the customer submitted the feedback (set automatically) */
    @Column(name = "submitted_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
