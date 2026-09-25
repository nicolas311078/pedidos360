package com.pedidos360.users.config;

import com.pedidos360.users.model.Role;
import com.pedidos360.users.model.User;
import com.pedidos360.users.repository.UserRepository;
import com.pedidos360.users.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AzureRolesProperties azureRoles;

    @Value("${frontend.base-url}")
    private String spaBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        String email = extractEmail(authentication);
        Role role = resolveRole(authentication);

        User user = userRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (existing.getRole() != role) {
                        existing.setRole(role);
                        userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .username(email)
                            .email(email)
                            .password(UUID.randomUUID().toString())
                            .role(role)
                            .build();
                    return userRepository.save(newUser);
                });

        String token = jwtService.generateToken(user);

        response.setStatus(HttpServletResponse.SC_FOUND);
        response.sendRedirect(spaBaseUrl + "/auth/callback?token=" + token);
    }

    /**
     * Determina el rol interno según los App Roles (claim "roles") que trae el
     * token de Microsoft Entra ID. Los proveedores sin claim de roles
     * (p. ej. Google) caen siempre en CLIENTE.
     */
    private Role resolveRole(Authentication authentication) {
        Collection<String> roleClaims = extractRoles(authentication);
        if (roleClaims.isEmpty()) {
            return Role.CLIENTE;
        }
        for (Role role : Role.values()) {
            for (String claim : roleClaims) {
                if (azureRoles.roleIdsFor(role).contains(claim)) {
                    return role;
                }
            }
        }
        return Role.CLIENTE;
    }

    private Collection<String> extractRoles(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        Object roles = null;
        if (principal instanceof OidcUser oidcUser) {
            roles = oidcUser.getClaim("roles");
        } else if (principal instanceof OAuth2User oAuth2User) {
            roles = oAuth2User.getAttribute("roles");
        }
        if (roles instanceof Collection<?> col) {
            return col.stream().map(String::valueOf).toList();
        }
        if (roles instanceof String single) {
            return List.of(single);
        }
        return List.of();
    }

    private String extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OidcUser oidcUser) {
            if (oidcUser.getEmail() != null) return oidcUser.getEmail();
            Object preferredUsername = oidcUser.getAttribute("preferred_username");
            if (preferredUsername != null) return String.valueOf(preferredUsername);
        } else if (principal instanceof OAuth2User oAuth2User) {
            Object email = oAuth2User.getAttribute("email");
            if (email != null) return String.valueOf(email);
        }
        throw new IllegalArgumentException("No se pudo obtener el email del usuario del proveedor OAuth2");
    }
}