package io.storeyes.storeyes_coffee.charges.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePersonnelTypeRequest {

    @NotBlank(message = "Type name is required")
    @Size(max = 100, message = "Type name must not exceed 100 characters")
    private String name;
}
