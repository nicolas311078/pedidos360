package com.pedidos360.users.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de Kafka del microservicio de usuarios (EP5).
 *
 * Lee el bloque "kafka:" raíz de application.yaml:
 *  - spring.kafka.*  -> cliente (brokers, deserializadores, ACK manual)
 *  - kafka.*         -> tópicos y consumer groups
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kafka")
public class KafkaTopicsProperties {

    private Topics topics = new Topics();

    /** Consumer group del evento de pedido creado. */
    private String createdGroup = "pedidos-created-group";

    /** Consumer group del evento de cambio de estado. */
    private String statusGroup = "pedidos-status-group";

    @Getter
    @Setter
    public static class Topics {
        private String created = "pedidos.creados.v1";
        private String status = "pedidos.estados.v1";
    }
}
