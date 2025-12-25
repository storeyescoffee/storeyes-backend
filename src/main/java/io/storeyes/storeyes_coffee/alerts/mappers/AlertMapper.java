package io.storeyes.storeyes_coffee.alerts.mappers;

import io.storeyes.storeyes_coffee.alerts.dto.AlertDTO;
import io.storeyes.storeyes_coffee.alerts.entities.Alert;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AlertMapper {
    
    AlertDTO toDTO(Alert alert);
    
    List<AlertDTO> toDTOList(List<Alert> alerts);
}

