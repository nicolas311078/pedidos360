package com.pedidos360.orders.messaging;

import com.pedidos360.orders.config.KafkaTopicsProperties;
import com.pedidos360.orders.messaging.event.OrderCreatedEvent;
import com.pedidos360.orders.messaging.event.OrderStatusChangedEvent;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Publicador de Kafka del microservicio de ordenes (EP5).
 *
 * Publica EXACTAMENTE los mismos DTO que ya se envian por RabbitMQ: el contrato
 * de negocio no cambia, solo cambia el transporte. Se usa StringSerializer en
 * application.yaml (no JsonSerializer) para que el JSON lo arme el ObjectMapper
 * del proyecto (tools.jackson) y no aparezca un segundo Jackson en el classpath.
 *
 * La clave del mensaje es el orderId: asi todos los eventos de un mismo pedido
 * caen en la misma particion y se conserva el orden por pedido.
 */
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicsProperties properties;

    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                OrderCreatedEvent.TYPE,
                order.getId(),
                order.getUserEmail(),
                order.getTotal().toPlainString(),
                order.getItems().size(),
                LocalDateTime.now().toString());

        send(properties.getTopics().getCreated(), String.valueOf(order.getId()), event);
    }

    public void publishOrderStatusChanged(Order order, OrderStatus previousStatus) {
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                UUID.randomUUID().toString(),
                OrderStatusChangedEvent.TYPE,
                order.getId(),
                order.getUserEmail(),
                previousStatus != null ? previousStatus.name() : null,
                order.getStatus().name(),
                order.getTotal().toPlainString(),
                LocalDateTime.now().toString());

        send(properties.getTopics().getStatus(), String.valueOf(order.getId()), event);
    }

    private void send(String topic, String key, Object event) {
        String payload = objectMapper.writeValueAsString(event);

        List<Header> headers = List.of(
                new RecordHeader("x-event-type", eventType(event).getBytes(StandardCharsets.UTF_8)),
                new RecordHeader("x-event-id", eventId(event).getBytes(StandardCharsets.UTF_8)));

        kafkaTemplate.send(new ProducerRecord<>(topic, null, key, payload, headers));

        log.info("Evento publicado -> topico '{}' key '{}' body={}", topic, key, payload);
    }

    private String eventType(Object event) {
        if (event instanceof OrderCreatedEvent e) {
            return e.eventType();
        }
        if (event instanceof OrderStatusChangedEvent e) {
            return e.eventType();
        }
        return "desconocido";
    }

    private String eventId(Object event) {
        if (event instanceof OrderCreatedEvent e) {
            return e.eventId();
        }
        if (event instanceof OrderStatusChangedEvent e) {
            return e.eventId();
        }
        return "";
    }
}
