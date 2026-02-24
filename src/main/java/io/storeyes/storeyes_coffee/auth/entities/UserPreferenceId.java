package io.storeyes.storeyes_coffee.auth.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceId implements Serializable {

    private String userId;
    private String preferenceKey;
}
