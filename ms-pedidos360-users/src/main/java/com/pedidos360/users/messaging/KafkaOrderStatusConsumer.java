package com.pedidos360.users.messaging;

import com.pedidos360.users.messaging.event.OrderStatusChangedEvent;
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
 * Consumidor Kafka del evento "pedido.estado.actualizado" (EP5).
 *
 * Usa un consumer group distinto del de pedidos creados (kafka.status-group)
 * para que ambos flujos avancen con offsets independientes.
 */
@Component
@RequiredArgsConstructor
public class KafkaOrderStatusConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderStatusConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderAnalyticsService analyticsService;

    @KafkaListener(topics = "${kafka.topics.status}", groupId = "${kafka.status-group}")
    public void onMessage(String payload, Acknowledgment ack) {
        OrderStatusChangedEvent event;
        try {
            event = objectMapper.readValue(payload.getBytes(StandardCharsets.UTF_8), OrderStatusChangedEvent.class);
        } catch (Exception ex) {
            log.error("Payload invalido en el topico de estados: {}", payload, ex);
            ack.acknowledge();
            return;
        }

        try {
            analyticsService.recordStatusChanged(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Fallo al procesar el evento Kafka {} del pedido {}; se reintentara",
                    event.eventId(), event.orderId(), ex);
            ack.nack(Duration.ofSeconds(5));
        }
    }
}
