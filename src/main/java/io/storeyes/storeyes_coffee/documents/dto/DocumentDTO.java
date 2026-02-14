package io.storeyes.storeyes_coffee.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDTO {
    
    private Long id;
    private Long storeId;
    private String storeCode;
    private String name;
    private String description;
    private String url;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


