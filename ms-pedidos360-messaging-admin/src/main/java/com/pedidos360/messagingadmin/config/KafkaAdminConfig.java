package com.pedidos360.messagingadmin.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** AdminClient de Kafka para consultar y crear topicos y consumer groups. */
@Configuration
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    public AdminClient kafkaAdminClient(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }
}
