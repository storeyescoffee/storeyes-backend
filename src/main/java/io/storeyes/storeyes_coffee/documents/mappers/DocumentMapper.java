package io.storeyes.storeyes_coffee.documents.mappers;

import io.storeyes.storeyes_coffee.documents.dto.DocumentDTO;
import io.storeyes.storeyes_coffee.documents.entities.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    
    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "storeCode", source = "store.code")
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    DocumentDTO toDTO(Document document);
    
    List<DocumentDTO> toDTOList(List<Document> documents);
}

