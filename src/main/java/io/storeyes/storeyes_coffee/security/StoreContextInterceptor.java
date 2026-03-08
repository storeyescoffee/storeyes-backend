package io.storeyes.storeyes_coffee.security;

import io.storeyes.storeyes_coffee.rolemapping.repositories.RoleMappingRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Global interceptor that resolves the current user's store ID from RoleMapping (any role).
 * Sets store ID on the request.
 */
@Component
@RequiredArgsConstructor
public class StoreContextInterceptor implements HandlerInterceptor {

    private final RoleMappingRepository roleMappingRepository;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String userId = KeycloakTokenUtils.getUserId();
        if (userId == null) {
            return true;
        }
        roleMappingRepository.findFirstByUser_Id(userId)
                .map(rm -> rm.getStore().getId())
                .ifPresent(id -> request.setAttribute(CurrentStoreContext.REQUEST_ATTR_STORE_ID, id));
        return true;
    }
}
