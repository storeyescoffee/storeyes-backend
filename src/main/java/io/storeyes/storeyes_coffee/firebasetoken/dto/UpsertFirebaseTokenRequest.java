package io.storeyes.storeyes_coffee.firebasetoken.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpsertFirebaseTokenRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
