package io.storeyes.storeyes_coffee.documents.mappers;

import io.storeyes.storeyes_coffee.documents.dto.DocumentCategoryDTO;
import io.storeyes.storeyes_coffee.documents.entities.DocumentCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DocumentCategoryMapper {

    @Mapping(target = "storeId", source = "store.id")
    DocumentCategoryDTO toDTO(DocumentCategory category);

    List<DocumentCategoryDTO> toDTOList(List<DocumentCategory> categories);
}
