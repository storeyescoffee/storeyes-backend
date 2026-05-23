package io.storeyes.storeyes_coffee.firebase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseTokenActionRequest {

    @NotBlank
    private String mobileId;

    @NotBlank
    @Pattern(regexp = "login|logout", message = "action must be 'login' or 'logout'")
    private String action;

    /** Required when action=login, ignored on logout */
    private String firebaseToken;

    /** Required when action=login and the device is new (first insert) */
    private String platform;
}
