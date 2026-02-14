package io.storeyes.storeyes_coffee.documents.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateDocumentRequest {
    
    private String name;
    
    private String description;
    
    private Long categoryId;
    
    /** When true, unlink document from any category. */
    private Boolean unsetCategory;
    
    private MultipartFile file;
}

