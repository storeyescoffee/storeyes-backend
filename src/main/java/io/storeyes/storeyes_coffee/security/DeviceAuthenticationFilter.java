package io.storeyes.storeyes_coffee.security;

import io.storeyes.storeyes_coffee.device.entities.Device;
import io.storeyes.storeyes_coffee.device.repositories.DeviceRepository;
import io.storeyes.storeyes_coffee.store.entities.Store;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates a registered board by its {@code X-DEVICE-ID} header, so device routes can sit behind
 * {@code authenticated()} like any other endpoint instead of being opened up with {@code permitAll}.
 * A successful lookup puts a {@link PreAuthenticatedAuthenticationToken} carrying {@link #ROLE_DEVICE}
 * in the security context, and publishes the device's store on {@link DeviceContext} /
 * {@link CurrentStoreContext} for the handlers downstream.
 *
 * <p>The filter only runs on the routes listed below. That restriction is load-bearing: the chain
 * ends in {@code anyRequest().authenticated()}, which any authentication satisfies, so a device
 * authenticated everywhere would be a device authorised everywhere. Add a path here only when that
 * route is genuinely meant to be reachable from a shop-floor board.
 *
 * <p>A route is device-authenticated in one of two ways:
 * <ul>
 *   <li>{@link #DEVICE_PATHS} — the device is a <em>fallback</em>: a JWT wins when the request
 *       carries one, and only an anonymous request is asked for a device id.</li>
 *   <li>{@link #DEVICE_ONLY_PATHS} — the device is the <em>only</em> credential: any JWT is ignored
 *       and dropped, so a board carrying a stale token still acts on its own store rather than on
 *       whatever store that token belongs to.</li>
 * </ul>
 *
 * <p>The filter never rejects: it either sets an authentication or leaves none, and the chain's
 * normal 401/403 handling does the rest. Pair each path with an authorization rule in
 * {@code SecurityConfig} — {@code authenticated()} for a fallback route, {@code hasRole("DEVICE")}
 * for a device-only one.
 */
@Slf4j
@RequiredArgsConstructor
public class DeviceAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEVICE_ID_HEADER = "X-DEVICE-ID";

    /** Authority granted to a device, distinguishing it from a real user in authorization rules. */
    public static final String ROLE_DEVICE = "ROLE_DEVICE";

    /** Routes where a device may authenticate, but a JWT takes precedence. See the class javadoc. */
    public static final Set<String> DEVICE_PATHS = Set.of("/api/staff/work-modes");

    /** Routes closed to users: a registered device is the only way in. See the class javadoc. */
    public static final Set<String> DEVICE_ONLY_PATHS = Set.of("/api/staff/employee-logs/punch");

    private final DeviceRepository deviceRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        log.debug("Device filter: path={} deviceHeader={} alreadyAuthenticated={}",
                path, request.getHeader(DEVICE_ID_HEADER), isAuthenticated());

        if (DEVICE_ONLY_PATHS.contains(path)) {
            // Whatever the request arrived with, only the device counts here: drop any JWT so a stale
            // token on a board cannot authenticate, then let hasRole("DEVICE") reject if no device.
            SecurityContextHolder.clearContext();
            authenticateDevice(request);
        } else if (DEVICE_PATHS.contains(path) && !isAuthenticated()) {
            authenticateDevice(request);
        } else {
            log.debug("Device filter: no device auth for path={} (device paths: {}, device-only paths: {})",
                    path, DEVICE_PATHS, DEVICE_ONLY_PATHS);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateDevice(HttpServletRequest request) {
        String boardId = request.getHeader(DEVICE_ID_HEADER);
        if (boardId == null || boardId.isBlank()) {
            log.warn("Device auth failed for {}: no {} header", request.getRequestURI(), DEVICE_ID_HEADER);
            return;
        }
        boardId = boardId.trim();

        Store store = deviceRepository.findByBoardId(boardId)
                .map(Device::getStore)
                .orElse(null);
        if (store == null) {
            log.warn("Device auth failed for {}: no device registered for boardId={}",
                    request.getRequestURI(), boardId);
            return;
        }

        PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                boardId, null, List.of(new SimpleGrantedAuthority(ROLE_DEVICE)));
        authentication.setDetails(store.getCode());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        request.setAttribute(DeviceContext.REQUEST_ATTR_STORE_CODE, store.getCode());
        request.setAttribute(DeviceContext.REQUEST_ATTR_BOARD_ID, boardId);
        request.setAttribute(CurrentStoreContext.REQUEST_ATTR_STORE_ID, store.getId());
        log.debug("Authenticated device boardId={} for store={}", boardId, store.getCode());
    }

    /** True when a real (non-anonymous) authentication is already in place — a JWT beats the device. */
    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
