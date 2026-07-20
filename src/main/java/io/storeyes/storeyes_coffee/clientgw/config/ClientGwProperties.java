package io.storeyes.storeyes_coffee.clientgw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstream client-gw gateway (panel.storeyes.io) used to proxy staff/client management calls.
 */
@Data
@ConfigurationProperties(prefix = "app.client-gw")
public class ClientGwProperties {

    /** Base URL of the upstream client-gw API, no trailing slash. */
    private String baseUrl = "https://panel.storeyes.io/api/client-gw";

    /** Shared X-API-KEY value expected by the upstream gateway. */
    private String apiKey = "";
}
