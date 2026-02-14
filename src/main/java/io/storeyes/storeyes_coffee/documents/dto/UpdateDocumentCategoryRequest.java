package io.storeyes.storeyes_coffee.documents.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDocumentCategoryRequest {

    @Size(max = 255)
    private String name;

    private String description;

    private Integer sortOrder;
}
