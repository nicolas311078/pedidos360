package com.pedidos360.orders.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Topologia de RabbitMQ del microservicio de ordenes (EP3).
 *
 * Todos los nombres (exchanges, colas, routing keys y colas de mensajes
 * perdidos) estan centralizados en application.yaml bajo el prefijo
 * "messaging". Este POJO solo los transporta: ninguna clase escribe nombres
 * de infraestructura en codigo.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    /** Exchanges con nombre y tipo. */
    private Exchanges exchanges = new Exchanges();

    /** Tipo de cola usado al declarar las colas (quorum = replicada en el cluster). */
    private String queueType = "quorum";

    /** Colas que declara este microservicio, indexadas por nombre logico. */
    private Map<String, QueueDefinition> queues = new LinkedHashMap<>();

    /** Routing keys de los eventos que produce este microservicio. */
    private RoutingKeys routingKeys = new RoutingKeys();

    /** Monitor de las colas de mensajes no entregados. */
    private DlqMonitor dlqMonitor = new DlqMonitor();

    @Getter
    @Setter
    public static class DlqMonitor {
        /**
         * true: el monitor registra en logs los mensajes de la DLQ y los libera.
         * false: los mensajes quedan retenidos en la DLQ para inspeccion manual
         * (util en la demo: permite ver el mensaje en la consola de RabbitMQ).
         */
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Exchanges {
        /** Exchange directo (una clave de routing por destino). */
        private String direct;
        /** Exchange topic (enrutado por patron de claves). */
        private String topic;
        /** Dead letter exchange: destino de los mensajes no procesables. */
        private String deadLetter;
    }

    @Getter
    @Setter
    public static class QueueDefinition {
        /** Nombre fisico de la cola en RabbitMQ. */
        private String name;
        /** Tipo de exchange al que se enlaza: "direct" o "topic". */
        private String exchange;
        /** Clave de routing usada en el binding. */
        private String routingKey;
        /** Cola de mensajes no entregados (DLQ) asociada. */
        private String deadLetterQueue;
    }

    @Getter
    @Setter
    public static class RoutingKeys {
        private String orderCreated;
        private String orderStatusChanged;
    }
}
