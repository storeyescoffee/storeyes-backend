package io.storeyes.storeyes_coffee.firebase.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import io.storeyes.storeyes_coffee.auth.entities.UserInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "firebase_tokens_v2", indexes = {
    @Index(name = "idx_firebase_tokens_mobile_id", columnList = "mobile_id"),
    @Index(name = "idx_firebase_tokens_user_id", columnList = "user_id")
})
public class FirebaseToken2 {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "firebase_token_id_seq")
    @SequenceGenerator(name = "firebase_token_id_seq", sequenceName = "firebase_token_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo user;

    @Column(name = "mobile_id", unique = true, nullable = false)
    private String mobileId;

    @Column(name = "firebase_token", nullable = false)
    private String firebaseToken;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "is_logged_in", nullable = false)
    private boolean isLoggedIn;

    @Column(name = "last_logged_in_timestamp", nullable = false)
    private LocalDateTime lastLoggedInTimestamp;

}