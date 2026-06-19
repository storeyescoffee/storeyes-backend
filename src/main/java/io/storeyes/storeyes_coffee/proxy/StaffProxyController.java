package io.storeyes.storeyes_coffee.proxy;

import io.storeyes.storeyes_coffee.rolemapping.repositories.RoleMappingRepository;
import io.storeyes.storeyes_coffee.security.KeycloakTokenUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StaffProxyController {

    private static final String TARGET_BASE = "http://10.0.48.56:8080";
    private static final String STORE_CODE_HEADER = "X-STORE-CODE";
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host"
    );

    private final RestTemplate restTemplate;
    private final RoleMappingRepository roleMappingRepository;

    @RequestMapping("/api/staff/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String targetPath = request.getRequestURI().replaceFirst("/api/staff", "/api");
        String qs = request.getQueryString();
        URI targetUri = URI.create(TARGET_BASE + targetPath + (qs != null ? "?" + qs : ""));

        HttpHeaders headers = copyHeaders(request);

        String storeCode = request.getHeader(STORE_CODE_HEADER);
        if (storeCode == null || storeCode.isBlank()) {
            storeCode = resolveStoreCode();
        }
        if (storeCode != null) {
            headers.set(STORE_CODE_HEADER, storeCode);
        }

        byte[] body = request.getInputStream().readAllBytes();
        HttpEntity<byte[]> entity = body.length > 0
                ? new HttpEntity<>(body, headers)
                : new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(targetUri, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray());
        }
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

    private String resolveStoreCode() {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) return null;
        return roleMappingRepository.findFirstByUser_IdOrderByStore_IdAsc(userId)
                .map(rm -> rm.getStore().getCode())
                .orElse(null);
    }
}
