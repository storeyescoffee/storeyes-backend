package io.storeyes.storeyes_coffee.security;

import io.storeyes.storeyes_coffee.device.repositories.DeviceRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Enforces device authentication on handlers marked {@link DeviceAuthenticated}.
 *
 * <p>An authenticated user wins: when a JWT is present the handler runs untouched and store context
 * comes from {@link StoreContextInterceptor} as usual. Otherwise the {@code X-DEVICE-ID} header is
 * looked up against the registered devices; the matching device's store is published on
 * {@link DeviceContext} (and as the current store id, so store-scoped code works either way).
 * A missing or unknown device id is rejected with 401 and the handler never runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAuthInterceptor implements HandlerInterceptor {

    public static final String DEVICE_ID_HEADER = "X-DEVICE-ID";

    private final DeviceRepository deviceRepository;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws IOException {
        if (!isDeviceAuthenticated(handler)) {
            return true;
        }

        // A real user beats the device: leave the request on the JWT path entirely.
        if (KeycloakTokenUtils.getUserId() != null) {
            return true;
        }

        String boardId = request.getHeader(DEVICE_ID_HEADER);
        if (boardId == null || boardId.isBlank()) {
            log.warn("Device auth rejected for {}: missing {} header", request.getRequestURI(), DEVICE_ID_HEADER);
            return reject(response, "Device identification required");
        }

        Store store = deviceRepository.findByBoardId(boardId.trim())
                .map(device -> device.getStore())
                .orElse(null);
        if (store == null) {
            log.warn("Device auth rejected for {}: no device registered for boardId={}",
                    request.getRequestURI(), boardId);
            return reject(response, "Unknown device");
        }

        request.setAttribute(DeviceContext.REQUEST_ATTR_STORE_CODE, store.getCode());
        request.setAttribute(DeviceContext.REQUEST_ATTR_BOARD_ID, boardId.trim());
        request.setAttribute(CurrentStoreContext.REQUEST_ATTR_STORE_ID, store.getId());
        return true;
    }

    private boolean isDeviceAuthenticated(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), DeviceAuthenticated.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), DeviceAuthenticated.class);
    }

    private boolean reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
        return false;
    }
}
