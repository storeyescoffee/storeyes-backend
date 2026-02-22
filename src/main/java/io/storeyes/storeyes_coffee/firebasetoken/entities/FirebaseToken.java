package io.storeyes.storeyes_coffee.firebasetoken.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.storeyes.storeyes_coffee.auth.entities.UserInfo;
import io.storeyes.storeyes_coffee.store.entities.Store;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "firebase_tokens")
public class FirebaseToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "firebase_token_id_seq")
    @SequenceGenerator(name = "firebase_token_id_seq", sequenceName = "firebase_token_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo user;
    
    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
