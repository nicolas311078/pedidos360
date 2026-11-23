package com.pedidos360.orders.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion de Kafka del microservicio de ordenes (EP5).
 *
 * Lee el bloque "kafka:" raíz de application.yaml. Se mantienen dos prefijos
 * distintos a proposito:
 *  - spring.kafka.*  -> configuracion del cliente (brokers, serializadores)
 *  - kafka.*         -> nombres de tópicos y parametros de administracion
 *
 * Asi ningun nombre de infraestructura queda escrito a mano en las clases.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "kafka")
public class KafkaTopicsProperties {

    /** Tópicos del evento de pedido creado y de cambio de estado. */
    private Topics topics = new Topics();

    private int partitions = 3;
    private int replicationFactor = 3;
    private long retentionMs = 604800000L;
    private String cleanupPolicy = "delete";

    @Getter
    @Setter
    public static class Topics {
        private String created = "pedidos.creados.v1";
        private String status = "pedidos.estados.v1";
    }
}
