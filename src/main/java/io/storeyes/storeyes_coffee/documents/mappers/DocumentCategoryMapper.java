package io.storeyes.storeyes_coffee.documents.mappers;

import io.storeyes.storeyes_coffee.documents.dto.DocumentCategoryDTO;
import io.storeyes.storeyes_coffee.documents.entities.DocumentCategory;
import io.storeyes.storeyes_coffee.store.entities.Store;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentCategoryMapper {

    @Mapping(target = "storeId", expression = "java(mapStoreId(category))")
    DocumentCategoryDTO toDTO(DocumentCategory category);

    default Long mapStoreId(DocumentCategory category) {
        Store store = category != null ? category.getStore() : null;
        return store != null ? store.getId() : null;
    }

    List<DocumentCategoryDTO> toDTOList(List<DocumentCategory> categories);
}
