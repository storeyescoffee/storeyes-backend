package io.storeyes.storeyes_coffee.accesscontrol.entities;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import io.storeyes.storeyes_coffee.store.entities.Store;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
@Table(name = "access_control_staff")
public class ACStaff {
    

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "access_control_staff_id_seq")
    @SequenceGenerator(name = "access_control_staff_id_seq", sequenceName = "access_control_staff_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    private String code;

    private String name;

}
