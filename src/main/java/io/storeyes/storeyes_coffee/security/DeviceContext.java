package io.storeyes.storeyes_coffee.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Holds the store resolved from the calling device by {@link DeviceAuthInterceptor}, for requests
 * that authenticated by {@code X-DEVICE-ID} rather than by JWT. The device counterpart of
 * {@link CurrentStoreContext}.
 *
 * <p>Everything here is null when the request was authenticated as a user, so a non-null store code
 * is also the signal that the caller is a device.
 */
public final class DeviceContext {

    /** Request attribute holding the resolved store code (String). */
    public static final String REQUEST_ATTR_STORE_CODE = "deviceStoreCode";

    /** Request attribute holding the board id the device authenticated with (String). */
    public static final String REQUEST_ATTR_BOARD_ID = "deviceBoardId";

    private DeviceContext() {}

    /** Store code of the calling device, or null if this request was not device-authenticated. */
    public static String getStoreCode() {
        return attribute(REQUEST_ATTR_STORE_CODE);
    }

    /** Board id the caller authenticated with, or null if this request was not device-authenticated. */
    public static String getBoardId() {
        return attribute(REQUEST_ATTR_BOARD_ID);
    }

    /** True when the current request was authenticated as a device rather than as a user. */
    public static boolean isDeviceAuthenticated() {
        return getStoreCode() != null;
    }

    private static String attribute(String name) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(name);
        return value instanceof String s ? s : null;
    }
}
