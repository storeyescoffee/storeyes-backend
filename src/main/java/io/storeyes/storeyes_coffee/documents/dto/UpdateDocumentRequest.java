package io.storeyes.storeyes_coffee.documents.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UpdateDocumentRequest {
    
    private String name;
    
    private String description;
    
    private MultipartFile file;
}



