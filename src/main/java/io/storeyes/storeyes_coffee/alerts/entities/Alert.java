package io.storeyes.storeyes_coffee.alerts.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "alert_id_seq")
    @SequenceGenerator(name = "alert_id_seq", sequenceName = "alert_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "alert_date", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private LocalDateTime alertDate;

    @Column(name = "video_url", nullable = false)
    private String mainVideoUrl;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_processed", columnDefinition = "boolean default false")
    private boolean isProcessed;
    
    @Column(name = "secondary_video_url")
    private String secondaryVideoUrl;

    @Column(name = "human_judgement", nullable = false)
    @Enumerated(EnumType.STRING)
    private HumanJudgement humanJudgement;

    @Column(name = "human_judgement_comment")
    private String humanJudgementComment;

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
        this.humanJudgement = HumanJudgement.NEW;
    }


}
