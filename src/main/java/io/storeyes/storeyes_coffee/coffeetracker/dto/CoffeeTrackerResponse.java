package io.storeyes.storeyes_coffee.coffeetracker.dto;

import io.storeyes.storeyes_coffee.coffeetracker.entities.CoffeeTracker;
import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * API projection of {@link CoffeeTracker} without nested {@code store} (store is implied by auth context).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoffeeTrackerResponse {

    private Long id;
    private LocalDate date;
    private LocalDateTime timestamp;
    private Integer quantity;
    private TrackerStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TrackerCategoryDetailResponse> details;

    public static CoffeeTrackerResponse from(CoffeeTracker entity) {
        List<TrackerCategoryDetailResponse> details = entity.getDetails() == null
                ? Collections.emptyList()
                : entity.getDetails().stream().map(TrackerCategoryDetailResponse::from).toList();
        return CoffeeTrackerResponse.builder()
                .id(entity.getId())
                .date(entity.getDate())
                .timestamp(entity.getTimestamp())
                .quantity(entity.getQuantity())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .details(details)
                .build();
    }
}
