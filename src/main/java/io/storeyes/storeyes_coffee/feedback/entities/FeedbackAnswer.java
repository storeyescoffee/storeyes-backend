package io.storeyes.storeyes_coffee.feedback.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "feedback_answers")
public class FeedbackAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "feedback_answer_id_seq")
    @SequenceGenerator(name = "feedback_answer_id_seq", sequenceName = "feedback_answer_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "feedback_id", nullable = false)
    private Feedback feedback;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private FeedbackQuestion question;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private FeedbackRating rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
