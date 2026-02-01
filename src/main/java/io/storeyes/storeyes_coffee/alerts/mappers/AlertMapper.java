package io.storeyes.storeyes_coffee.alerts.mappers;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.dto.AlertDetailsDTO;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {io.storeyes.storeyes_coffee.sales.mappers.SalesMapper.class})
public interface AlertMapper {
    
    @Mapping(target = "isProcessed", source = "processed")
    AlertDTO toDTO(Alert alert);
    
    List<AlertDTO> toDTOList(List<Alert> alerts);
    
    /**
     * Map Alert entity to AlertDetailsDTO with sales
     * Uses SalesMapper to map the nested sales list
     */
    @Mapping(target = "sales", source = "sales")
    @Mapping(target = "isProcessed", source = "processed")
    AlertDetailsDTO toDetailsDTO(Alert alert);
}

