package io.storeyes.storeyes_coffee.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetVerifyBody {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code;
}
