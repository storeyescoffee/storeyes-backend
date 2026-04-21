package io.storeyes.storeyes_coffee.accesscontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ACEventByDateItemDTO {

    private String code;
    private String name;
    private LocalDateTime time;
}
