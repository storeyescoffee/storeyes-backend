package io.storeyes.storeyes_coffee.charges.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonnelTypeResponse {
    private Long id;
    private String name;
    private boolean isActive;
    private LocalDateTime createdAt;
}
