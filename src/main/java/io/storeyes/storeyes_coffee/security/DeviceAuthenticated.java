package io.storeyes.storeyes_coffee.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler (or a whole controller) as reachable by a registered device when there is no
 * authenticated user.
 *
 * <p>A JWT still takes precedence: if the request carries a valid token, the user's store context
 * applies and the device is ignored entirely. Only when no user is authenticated does
 * {@link DeviceAuthInterceptor} fall back to the {@code X-DEVICE-ID} header, resolve the registered
 * device's store, and publish it on {@link DeviceContext}. A request with neither a JWT nor a known
 * device is rejected with 401 before the handler runs.
 *
 * <p>Routes annotated with this must also be {@code permitAll} in the security config — Spring
 * Security runs before interceptors and would otherwise reject the anonymous device request first.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DeviceAuthenticated {
}
