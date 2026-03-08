package io.storeyes.storeyes_coffee.coffeetracker.dto;

import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoffeeTrackerEventDTO {
    private Long id;
    private LocalDate date;
    private LocalDateTime timestamp;
    private Integer quantity;
    private TrackerStatus status;
    private Long previousId;
}
