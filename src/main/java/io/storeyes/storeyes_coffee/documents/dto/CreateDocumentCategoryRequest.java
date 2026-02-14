package io.storeyes.storeyes_coffee.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDocumentCategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 255)
    private String name;

    private String description;

    private Integer sortOrder;
}
