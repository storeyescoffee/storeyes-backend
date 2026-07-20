package io.storeyes.storeyes_coffee.proxy;

import io.storeyes.storeyes_coffee.clientgw.config.ClientGwProperties;
import io.storeyes.storeyes_coffee.security.CurrentStoreContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.Set;

/**
 * Raw byte pass-through proxy for the upstream client-gw gateway (panel.storeyes.io), following
 * {@link StaffProxyController}'s pattern. The frontend calls {@code /api/client-gw/**} with its
 * normal auth; this controller strips that prefix, forwards the request to the configured
 * upstream base URL, and injects the {@code X-API-KEY} / {@code X-STORE-ID} headers the upstream
 * gateway requires — the frontend never sees or sends those.
 */
@RestController
public class ClientGwProxyController {

    private static final String PREFIX = "/api/client-gw";
    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String STORE_ID_HEADER = "X-STORE-ID";
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host",
            // client-gw is keyed on X-API-KEY/X-STORE-ID only (no JWT/cookie auth); forwarding this
            // backoffice's own bearer token makes panel.storeyes.io try to validate it as its own JWT
            // and reject it with 401 before ever looking at X-API-KEY.
            "authorization", "cookie"
    );
    // panel.storeyes.io sends its own Access-Control-* headers (e.g. "*"). Forwarding those verbatim
    // stacks a second value alongside this server's own CORS filter response, which browsers reject
    // ("Access-Control-Allow-Origin contains multiple values"). CORS for this backoffice origin is
    // this server's job, not the upstream's — strip upstream's CORS headers from the response.
    private static final Set<String> STRIP_RESPONSE_HEADERS = Set.of(
            "access-control-allow-origin", "access-control-allow-credentials",
            "access-control-allow-methods", "access-control-allow-headers",
            "access-control-expose-headers", "access-control-max-age", "vary"
    );

    private final RestTemplate restTemplate;
    private final ClientGwProperties clientGwProperties;

    public ClientGwProxyController(
            @Qualifier("clientGwRestTemplate") RestTemplate restTemplate,
            ClientGwProperties clientGwProperties) {
        this.restTemplate = restTemplate;
        this.clientGwProperties = clientGwProperties;
    }

    @RequestMapping(PREFIX + "/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String targetPath = request.getRequestURI().replaceFirst(PREFIX, "");
        String qs = request.getQueryString();
        URI targetUri = URI.create(clientGwProperties.getBaseUrl() + targetPath + (qs != null ? "?" + qs : ""));

        HttpHeaders headers = copyHeaders(request);
        headers.set(API_KEY_HEADER, clientGwProperties.getApiKey());
        headers.set(STORE_ID_HEADER, String.valueOf(CurrentStoreContext.requireCurrentStoreId()));

        byte[] body = request.getInputStream().readAllBytes();
        HttpEntity<byte[]> entity = body.length > 0
                ? new HttpEntity<>(body, headers)
                : new HttpEntity<>(headers);

        try {
            ResponseEntity<byte[]> response =
                    restTemplate.exchange(targetUri, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(stripCorsHeaders(response.getHeaders()))
                    .body(response.getBody());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(stripCorsHeaders(e.getResponseHeaders()))
                    .body(e.getResponseBodyAsByteArray());
        }
    }

    private HttpHeaders stripCorsHeaders(HttpHeaders headers) {
        if (headers == null) return new HttpHeaders();
        HttpHeaders filtered = new HttpHeaders();
        headers.forEach((name, values) -> {
            if (!STRIP_RESPONSE_HEADERS.contains(name.toLowerCase())) {
                filtered.addAll(name, values);
            }
        });
        return filtered;
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) return headers;
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (HOP_BY_HOP.contains(name.toLowerCase())) continue;
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        return headers;
    }
}
