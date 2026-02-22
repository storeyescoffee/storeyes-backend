package io.storeyes.storeyes_coffee.auth.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_preferences")
@IdClass(UserPreferenceId.class)
public class UserPreference {

    @Id
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Id
    @Column(name = "preference_key", nullable = false, length = 128)
    private String preferenceKey;

    @Column(name = "preference_value", length = 512)
    private String preferenceValue;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PreUpdate
    @PrePersist
    public void setUpdatedAt() {
        this.updatedAt = OffsetDateTime.now();
    }
}
