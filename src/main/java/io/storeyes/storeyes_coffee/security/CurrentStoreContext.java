package io.storeyes.storeyes_coffee.security;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Holds the current request's store ID set by {@link StoreContextInterceptor}.
 * In "One Site" there is one store per user (resolved via RoleMapping).
 */
public final class CurrentStoreContext {

    /** Request attribute name for the current user's store ID (Long). */
    public static final String REQUEST_ATTR_STORE_ID = "currentStoreId";

    private CurrentStoreContext() {}

    /**
     * Returns the store ID for the current request, or null if not set
     * (e.g. no authenticated user, or user has no role mapping).
     */
    public static Long getCurrentStoreId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(REQUEST_ATTR_STORE_ID);
        return value instanceof Long ? (Long) value : null;
    }
}
