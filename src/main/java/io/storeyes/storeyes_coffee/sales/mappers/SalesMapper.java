package io.storeyes.storeyes_coffee.sales.mappers;

import io.storeyes.storeyes_coffee.sales.dto.SalesDTO;
import io.storeyes.storeyes_coffee.sales.entities.Sales;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SalesMapper {
    
    SalesDTO toDTO(Sales sales);
    
    List<SalesDTO> toDTOList(List<Sales> sales);
}

