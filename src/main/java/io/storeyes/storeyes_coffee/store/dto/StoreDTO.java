package io.storeyes.storeyes_coffee.store.dto;

import io.storeyes.storeyes_coffee.store.entities.StoreStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreDTO {
    
    private Long id;
    private String code;
    private String name;
    private String address;
    private double[] coordinates;
    private String city;
    private String type;
    private StoreStatus status;
    private boolean feedbackOnlyMode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}



