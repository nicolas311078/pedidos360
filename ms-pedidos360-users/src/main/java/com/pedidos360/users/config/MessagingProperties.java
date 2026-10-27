package com.pedidos360.users.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Topologia de RabbitMQ del microservicio de usuarios (EP3).
 *
 * Los nombres estan centralizados en application.yaml (prefijo "messaging").
 * Cada microservicio declara solo las colas que consume.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private Exchanges exchanges = new Exchanges();

    private String queueType = "quorum";

    private Map<String, QueueDefinition> queues = new LinkedHashMap<>();

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
        private String direct;
        private String topic;
        private String deadLetter;
    }

    @Getter
    @Setter
    public static class QueueDefinition {
        private String name;
        /** Tipo de exchange al que se enlaza: "direct" o "topic". */
        private String exchange;
        private String routingKey;
        private String deadLetterQueue;
    }
}
