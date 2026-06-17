package io.storeyes.storeyes_coffee.feedback.entities;

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
@Table(name = "feedback_questions")
public class FeedbackQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feedback_question_id_seq")
    @SequenceGenerator(name = "feedback_question_id_seq", sequenceName = "feedback_question_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "feedback_profile_id", nullable = false)
    private FeedbackProfile feedbackProfile;

    @Column(name = "label_ar", nullable = false, length = 512)
    private String labelAr;

    @Column(name = "label_fr", nullable = false, length = 512)
    private String labelFr;

    @Column(name = "label_en", nullable = false, length = 512)
    private String labelEn;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
