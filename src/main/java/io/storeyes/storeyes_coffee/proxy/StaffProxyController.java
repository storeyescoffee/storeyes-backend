package io.storeyes.storeyes_coffee.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.storeyes.storeyes_coffee.firebase.repositories.FirebaseToken2Repository;
import io.storeyes.storeyes_coffee.notification.services.FcmNotificationService;
import io.storeyes.storeyes_coffee.rolemapping.repositories.RoleMappingRepository;
import io.storeyes.storeyes_coffee.security.DeviceAuthInterceptor;
import io.storeyes.storeyes_coffee.security.DeviceAuthenticated;
import io.storeyes.storeyes_coffee.security.DeviceContext;
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
    private static final String PUNCH_PATH = "/api/staff/employee-logs/punch";
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

    /**
     * Punch is reachable from the shop-floor board, which has no user session: with no JWT the
     * caller falls back to device auth and {@link DeviceAuthInterceptor} has already resolved the
     * store (or rejected the request with 401) by the time we get here.
     */
    @DeviceAuthenticated
    @RequestMapping(PUNCH_PATH)
    public ResponseEntity<byte[]> punch(HttpServletRequest request) throws IOException {
        return forward(request);
    }

    @RequestMapping("/api/staff/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        return forward(request);
    }

    private ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException {
        String targetPath = request.getRequestURI().replaceFirst("/api/staff", "/api");
        String qs = request.getQueryString();
        URI targetUri = URI.create(TARGET_BASE + targetPath + (qs != null ? "?" + qs : ""));

        HttpHeaders headers = copyHeaders(request);

        String storeCode = resolveStoreCode(request);
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
                    && request.getRequestURI().startsWith(EMPLOYEE_LOGS_PATH);
            if (isEmployeeLogs && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return augmentWithNotificationResult(response);
            }

            return response;
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsByteArray());
        }
    }

    /**
     * Dispatches attendance notifications then merges the outcome into the upstream JSON body as
     * {@code "notificationResult": { "tokens": N, "notified": M }} so the caller can see how many
     * devices were reached. Returns the original response untouched if anything goes wrong.
     */
    private ResponseEntity<byte[]> augmentWithNotificationResult(ResponseEntity<byte[]> response) {
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            NotificationResult result = dispatchAttendanceNotifications(root);

            if (!(root instanceof ObjectNode obj)) {
                return response;
            }
            obj.putObject("notificationResult")
                    .put("tokens", result.tokens())
                    .put("notified", result.notified());

            byte[] newBody = objectMapper.writeValueAsBytes(obj);
            HttpHeaders headers = new HttpHeaders();
            headers.addAll(response.getHeaders());
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headers.setContentLength(newBody.length);
            return new ResponseEntity<>(newBody, headers, response.getStatusCode());
        } catch (Exception e) {
            log.error("Failed to augment employee-logs response with notification result: {}", e.getMessage(), e);
            return response;
        }
    }

    /**
     * Sends the attendance push(es) described by the {@code notifications} block of the upstream body.
     *
     * @return the number of target device tokens and how many sends succeeded. For grouped mode
     *         {@code notified} is the count of devices reached (≤ tokens); for per-employee mode it is
     *         the sum of successful sends across all items (one push per item per device).
     */
    private NotificationResult dispatchAttendanceNotifications(JsonNode root) {
        JsonNode notif = root.path("notifications");
        if (notif.isMissingNode() || !notif.path("send").asBoolean(false)) return NotificationResult.NONE;
        if (notif.path("dndSuppressed").asBoolean(false)) {
            log.debug("Attendance notifications suppressed by DND window — skipping dispatch");
            return NotificationResult.NONE;
        }

        if (fcmNotificationService.isEmpty()) {
            log.debug("FCM disabled — skipping attendance notifications");
            return NotificationResult.NONE;
        }

        List<String> tokens = firebaseToken2Repository.findActiveTokensByUserId(ATTENDANCE_OWNER_ID);
        if (tokens.isEmpty()) {
            log.debug("No active tokens for userId={}", ATTENDANCE_OWNER_ID);
            return NotificationResult.NONE;
        }

        int notified;
        boolean grouped = notif.path("grouped").asBoolean(false);
        if (grouped) {
            String summary = notif.path("summary").asText("Attendance update");
            String lateCount = String.valueOf(notif.path("lateCount").asInt(0));
            String absenceCount = String.valueOf(notif.path("absenceCount").asInt(0));
            notified = fcmNotificationService.get().sendToTokens(
                    tokens, "Attendance", summary,
                    Map.of("type", "attendance_summary",
                            "lateCount", lateCount,
                            "absenceCount", absenceCount));
            log.debug("Sent grouped attendance notification: {} (late={}, absent={})", summary, lateCount, absenceCount);
        } else {
            JsonNode items = notif.path("items");
            List<String> sent = new ArrayList<>();
            notified = 0;
            for (JsonNode item : items) {
                String name = item.path("employeeName").asText();
                String status = item.path("status").asText();
                String code = item.path("employeeCode").asText();
                String body = buildStatusBody(name, status);
                notified += fcmNotificationService.get().sendToTokens(
                        tokens, "Attendance", body,
                        Map.of("type", "attendance_item",
                                "employeeCode", code,
                                "status", status));
                sent.add(name + ":" + status);
            }
            log.debug("Sent {} per-employee attendance notification(s): {}", sent.size(), sent);
        }
        return new NotificationResult(tokens.size(), notified);
    }

    private record NotificationResult(int tokens, int notified) {
        private static final NotificationResult NONE = new NotificationResult(0, 0);
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

    /**
     * Store the request acts on: the calling device's store when it authenticated as a device
     * (a client-supplied X-STORE-CODE is not trusted in that case), otherwise the caller's own
     * header, otherwise the store mapped to the authenticated user.
     */
    private String resolveStoreCode(HttpServletRequest request) {
        String deviceStoreCode = DeviceContext.getStoreCode();
        if (deviceStoreCode != null) {
            return deviceStoreCode;
        }

        String headerStoreCode = request.getHeader(STORE_CODE_HEADER);
        if (headerStoreCode != null && !headerStoreCode.isBlank()) {
            return headerStoreCode;
        }

        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) return null;
        return roleMappingRepository.findFirstByUser_IdOrderByStore_IdAsc(userId)
                .map(rm -> rm.getStore().getCode())
                .orElse(null);
    }
}
