package io.storeyes.storeyes_coffee.documents.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CreateDocumentRequest {
    
    @NotBlank(message = "Document name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "File is required")
    private MultipartFile file;
}

