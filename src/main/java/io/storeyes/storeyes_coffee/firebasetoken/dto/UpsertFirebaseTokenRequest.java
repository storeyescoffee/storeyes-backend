package io.storeyes.storeyes_coffee.firebasetoken.dto;

import io.storeyes.storeyes_coffee.firebasetoken.entities.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpsertFirebaseTokenRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotNull(message = "Platform is required (IOS or ANDROID)")
    private Platform platform;
}
