package com.pedidos360.users.messaging;

import com.pedidos360.users.messaging.event.OrderCreatedEvent;
import com.pedidos360.users.service.OrderAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Consumidor Kafka del evento "pedido.creado" (EP5).
 *
 * El consumer group se lee de application.yaml (kafka.created-group), igual
 * que el topico (kafka.topics.created). El ACK es manual: se confirma solo
 * cuando la analitica se guardo. Si falla de forma transitoria se hace nack con
 * reintento; si el payload es invalido se descarta para no bloquear la particion.
 *
 * Este consumidor convive con NotificationConsumer (RabbitMQ): son flujos
 * distintos y no se pisan.
 */
@Component
@RequiredArgsConstructor
public class KafkaOrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderCreatedConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderAnalyticsService analyticsService;

    @KafkaListener(topics = "${kafka.topics.created}", groupId = "${kafka.created-group}")
    public void onMessage(String payload, Acknowledgment ack) {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(payload.getBytes(StandardCharsets.UTF_8), OrderCreatedEvent.class);
        } catch (Exception ex) {
            log.error("Payload invalido en el topico de pedidos creados: {}", payload, ex);
            ack.acknowledge();
            return;
        }

        try {
            analyticsService.recordCreated(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Fallo al procesar el evento Kafka {} del pedido {}; se reintentara",
                    event.eventId(), event.orderId(), ex);
            ack.nack(Duration.ofSeconds(5));
        }
    }
}
