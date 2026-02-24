package io.storeyes.storeyes_coffee.documents.mappers;

import io.storeyes.storeyes_coffee.documents.dto.DocumentDTO;
import io.storeyes.storeyes_coffee.documents.entities.Document;
import io.storeyes.storeyes_coffee.documents.entities.DocumentCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "storeCode", source = "store.code")
    @Mapping(target = "categoryId", expression = "java(mapCategoryId(document))")
    @Mapping(target = "categoryName", expression = "java(mapCategoryName(document))")
    DocumentDTO toDTO(Document document);

    default Long mapCategoryId(Document document) {
        DocumentCategory c = document != null ? document.getCategory() : null;
        return c != null ? c.getId() : null;
    }

    default String mapCategoryName(Document document) {
        DocumentCategory c = document != null ? document.getCategory() : null;
        return c != null ? c.getName() : null;
    }
    
    List<DocumentDTO> toDTOList(List<Document> documents);
}



