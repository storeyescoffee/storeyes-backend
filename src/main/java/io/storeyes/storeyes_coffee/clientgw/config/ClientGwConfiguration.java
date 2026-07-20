package io.storeyes.storeyes_coffee.clientgw.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ClientGwProperties.class)
public class ClientGwConfiguration {

    /**
     * Dedicated RestTemplate for the client-gw proxy, backed by the JDK HttpClient. The app-wide
     * {@code restTemplate} bean (auth/config/RestTemplateConfig) uses SimpleClientHttpRequestFactory,
     * which wraps java.net.HttpURLConnection — that class rejects PATCH ("Invalid HTTP method: PATCH"),
     * which client-gw's role-update endpoint needs. Scoped to this feature so other RestTemplate
     * consumers are unaffected.
     */
    @Bean
    public RestTemplate clientGwRestTemplate() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
