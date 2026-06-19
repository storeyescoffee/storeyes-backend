package io.storeyes.storeyes_coffee.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SendNotificationRequest {

    @NotNull
    private Long storeId;

    private List<String> roles;

    @NotBlank
    private String title;

    @NotBlank
    private String body;
}
