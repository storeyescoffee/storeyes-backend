package io.storeyes.storeyes_coffee.rolemapping.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

import io.storeyes.storeyes_coffee.store.entities.Store;
import io.storeyes.storeyes_coffee.auth.entities.UserInfo;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "role_mappings")
public class RoleMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "role_mapping_id_seq")
    @SequenceGenerator(name = "role_mapping_id_seq", sequenceName = "role_mapping_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo user;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
}
