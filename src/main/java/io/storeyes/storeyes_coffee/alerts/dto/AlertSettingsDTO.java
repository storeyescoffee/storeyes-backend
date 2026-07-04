package io.storeyes.storeyes_coffee.alerts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-store visibility of alert types, resolved for the current user's selected store.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertSettingsDTO {

    private boolean notTappedEnabled;
    private boolean returnEnabled;

    /** Whether the alerts feature is active for this store (activation date reached). */
    private boolean alertsActive;
}
