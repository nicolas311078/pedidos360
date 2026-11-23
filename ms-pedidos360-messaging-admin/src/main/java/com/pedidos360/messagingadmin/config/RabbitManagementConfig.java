package com.pedidos360.messagingadmin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Cliente HTTP para la Management API de RabbitMQ. */
@Configuration
public class RabbitManagementConfig {

    @Bean
    public RestClient rabbitManagementClient(
            @Value("${rabbit.management.url}") String url,
            @Value("${rabbit.management.user}") String user,
            @Value("${rabbit.management.password}") String password) {

        String basic = Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));

        return RestClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }
}
