package io.storeyes.adminpanel.accesscontrol.entities;

import jakarta.persistence.*;

import io.storeyes.storeyes_coffee.store.entities.Store;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "access_control_events")
public class ACEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "access_control_event_id_seq")
    @SequenceGenerator(name = "access_control_event_id_seq", sequenceName = "access_control_event_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne
    @JoinColumn(name = "access_control_staff_id", nullable = false)
    private ACStaff accessControlStaff;

    @Column(name = "event_type", nullable = false)
    @Builder.Default
    private String eventType = "FINGERPRINT";

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;


}
