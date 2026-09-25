package com.pedidos360.users.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        Throwable root = exception;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        StringBuilder json = new StringBuilder("{");
        json.append("\"error\":\"").append(escape(root.getClass().getSimpleName())).append("\"");
        if (root.getMessage() != null) {
            json.append(",\"message\":\"").append(escape(root.getMessage())).append("\"");
        }
        if (exception instanceof OAuth2AuthenticationException oauth2AuthenticationException) {
            OAuth2Error error = oauth2AuthenticationException.getError();
            json.append(",\"oauth2_error\":\"").append(escape(error.getErrorCode())).append("\"");
            if (error.getDescription() != null) {
                json.append(",\"description\":\"").append(escape(error.getDescription())).append("\"");
            }
        }
        json.append("}");

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.toString());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "'")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}