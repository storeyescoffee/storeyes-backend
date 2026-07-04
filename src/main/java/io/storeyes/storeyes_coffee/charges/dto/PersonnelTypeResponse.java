package io.storeyes.storeyes_coffee.charges.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isActive")
    private boolean isActive;
    private LocalDateTime createdAt;
}
