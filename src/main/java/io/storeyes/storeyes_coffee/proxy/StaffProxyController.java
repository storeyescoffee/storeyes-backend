package io.storeyes.storeyes_coffee.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.storeyes.storeyes_coffee.firebase.repositories.FirebaseToken2Repository;
import io.storeyes.storeyes_coffee.notification.services.FcmNotificationService;
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StaffProxyController {

    private static final String TARGET_BASE = "http://10.0.48.56:8080";
    private static final String STORE_CODE_HEADER = "X-STORE-CODE";
    private static final String EMPLOYEE_LOGS_PATH = "/api/staff/employee-logs";
    private static final String ATTENDANCE_OWNER_ID = "f2e75dab-2812-40d1-9f90-25f66675b311";
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host"
    );

    private final RestTemplate restTemplate;
    private final RoleMappingRepository roleMappingRepository;
    private final FirebaseToken2Repository firebaseToken2Repository;
    private final Optional<FcmNotificationService> fcmNotificationService;
    private final ObjectMapper objectMapper;

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
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    targetUri, HttpMethod.valueOf(request.getMethod()), entity, byte[].class);

            boolean isEmployeeLogs = HttpMethod.POST.matches(request.getMethod())
                    && EMPLOYEE_LOGS_PATH.equals(request.getRequestURI());
            if (isEmployeeLogs && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                dispatchAttendanceNotifications(response.getBody());
            }

            return response;
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray());
        }
    }

    private void dispatchAttendanceNotifications(byte[] responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode notif = root.path("notifications");
            if (notif.isMissingNode() || !notif.path("send").asBoolean(false)) return;
            if (notif.path("dndSuppressed").asBoolean(false)) {
                log.debug("Attendance notifications suppressed by DND window — skipping dispatch");
                return;
            }

            if (fcmNotificationService.isEmpty()) {
                log.debug("FCM disabled — skipping attendance notifications");
                return;
            }

            List<String> tokens = firebaseToken2Repository.findActiveTokensByUserId(ATTENDANCE_OWNER_ID);
            if (tokens.isEmpty()) {
                log.debug("No active tokens for userId={}", ATTENDANCE_OWNER_ID);
                return;
            }

            boolean grouped = notif.path("grouped").asBoolean(false);
            if (grouped) {
                String summary = notif.path("summary").asText("Attendance update");
                String lateCount = String.valueOf(notif.path("lateCount").asInt(0));
                String absenceCount = String.valueOf(notif.path("absenceCount").asInt(0));
                fcmNotificationService.get().sendToTokens(
                        tokens, "Attendance", summary,
                        Map.of("type", "attendance_summary",
                                "lateCount", lateCount,
                                "absenceCount", absenceCount));
                log.debug("Sent grouped attendance notification: {} (late={}, absent={})", summary, lateCount, absenceCount);
            } else {
                JsonNode items = notif.path("items");
                List<String> sent = new ArrayList<>();
                for (JsonNode item : items) {
                    String name = item.path("employeeName").asText();
                    String status = item.path("status").asText();
                    String code = item.path("employeeCode").asText();
                    String body = buildStatusBody(name, status);
                    fcmNotificationService.get().sendToTokens(
                            tokens, "Attendance", body,
                            Map.of("type", "attendance_item",
                                    "employeeCode", code,
                                    "status", status));
                    sent.add(name + ":" + status);
                }
                log.debug("Sent {} per-employee attendance notification(s): {}", sent.size(), sent);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch attendance notifications: {}", e.getMessage(), e);
        }
    }

    private String buildStatusBody(String name, String status) {
        return switch (status) {
            case "LATE" -> name + " arrived late";
            case "ABSENT" -> name + " is absent";
            case "PRESENT" -> name + " is present";
            default -> name + " — " + status;
        };
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
