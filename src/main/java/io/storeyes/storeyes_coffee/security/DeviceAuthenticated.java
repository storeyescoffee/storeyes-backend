package io.storeyes.storeyes_coffee.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler (or a whole controller) as reachable by a registered device, identified by the
 * {@code X-DEVICE-ID} header. {@link DeviceAuthInterceptor} resolves that device's store and
 * publishes it on {@link DeviceContext} before the handler runs.
 *
 * <p>By default a JWT takes precedence: if the request carries a valid token the user's store
 * context applies and the device is ignored. Set {@link #deviceOnly()} to close the route to users
 * entirely, so that a known device is the only way in.
 *
 * <p>Routes annotated with this must also be {@code permitAll} in the security config — Spring
 * Security runs before interceptors and would otherwise reject the anonymous device request first.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DeviceAuthenticated {

    /**
     * When true, the route is device-authenticated and nothing else: a valid {@code X-DEVICE-ID} is
     * required and any JWT on the request is ignored, so a user token alone earns a 401. Use it for
     * endpoints that only ever make sense coming from a physical board, where honouring a (possibly
     * stale) token would silently act on the wrong store.
     *
     * <p>When false (the default), the device is a fallback: an authenticated user is served on the
     * JWT path and only an anonymous request is asked for a device.
     */
    boolean deviceOnly() default false;
}
