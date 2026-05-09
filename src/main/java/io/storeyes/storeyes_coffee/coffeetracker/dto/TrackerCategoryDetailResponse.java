package io.storeyes.storeyes_coffee.coffeetracker.dto;

import io.storeyes.storeyes_coffee.coffeetracker.entities.TrackerCategoryDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackerCategoryDetailResponse {

    private Long id;
    private String category;
    private Integer quantity;

    public static TrackerCategoryDetailResponse from(TrackerCategoryDetail entity) {
        return TrackerCategoryDetailResponse.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .quantity(entity.getQuantity())
                .build();
    }
}
