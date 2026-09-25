package com.pedidos360.users.config;

import com.pedidos360.users.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class SecurityConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();

        for (Map.Entry<String, OAuth2ClientProperties.Registration> entry : properties.getRegistration().entrySet()) {
            String registrationId = entry.getKey();
            OAuth2ClientProperties.Registration reg = entry.getValue();

            // Proveedor sin credenciales (p. ej. el google local si GOOGLE_CLIENT_ID
            // está vacío) se omite en lugar de romper el arranque.
            if (reg.getClientId() == null || reg.getClientId().isBlank()) {
                continue;
            }

            OAuth2ClientProperties.Provider prov = properties.getProvider().get(registrationId);
            if (prov == null) {
                throw new IllegalStateException(
                        "No se encontró spring.security.oauth2.client.provider." + registrationId);
            }
            if (prov.getIssuerUri() == null || prov.getIssuerUri().isBlank()) {
                throw new IllegalStateException(
                        "Falta issuer-uri para el proveedor OAuth2: " + registrationId);
            }

            ClientAuthenticationMethod authMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST;
            String method = reg.getClientAuthenticationMethod();
            if (method != null) {
                switch (method) {
                    case "client_secret_basic" -> authMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
                    case "client_secret_post" -> authMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST;
                    case "none" -> authMethod = ClientAuthenticationMethod.NONE;
                    case "private_key_jwt" -> authMethod = ClientAuthenticationMethod.PRIVATE_KEY_JWT;
                    case "client_secret_jwt" -> authMethod = ClientAuthenticationMethod.CLIENT_SECRET_JWT;
                }
            }

            ClientRegistration.Builder builder = ClientRegistrations.fromIssuerLocation(prov.getIssuerUri())
                    .registrationId(registrationId)
                    .clientId(reg.getClientId())
                    .clientSecret(reg.getClientSecret())
                    .clientAuthenticationMethod(authMethod)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(reg.getRedirectUri())
                    .scope(reg.getScope().toArray(new String[0]))
                    .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(true).build());

            if (prov.getUserNameAttribute() != null) {
                builder.userNameAttributeName(prov.getUserNameAttribute());
            }

            registrations.add(builder.build());
        }

        if (registrations.isEmpty()) {
            throw new IllegalStateException(
                    "No hay proveedores OAuth2 configurados (falta client-id en spring.security.oauth2.client.registration.*)");
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
        // Fuerza el selector de cuentas en cada login (Microsoft y Google), para que
        // no reutilice la sesión de la cuenta anterior sin preguntar.
        resolver.setAuthorizationRequestCustomizer(customizer ->
                customizer.additionalParameters(params -> params.put("prompt", "select_account")));
        return resolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            OAuth2AuthorizationRequestResolver authorizationRequestResolver
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/login/**", "/oauth2/**", "/error").permitAll()
                        .requestMatchers("/api/users/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestResolver(authorizationRequestResolver))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    "{\"error\":\"Unauthorized\",\"message\":\"Se requiere token JWT\"}");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}