package com.pedidos360.orders.messaging;

import com.pedidos360.orders.config.MessagingProperties;
import com.pedidos360.orders.messaging.event.OrderCreatedEvent;
import com.pedidos360.orders.messaging.event.OrderStatusChangedEvent;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publica los eventos de dominio del microservicio de ordenes.
 *
 * El JSON se arma aqui de forma explicita (sin MessageConverter) para que el
 * payload no dependa del classpath del consumidor: cada servicio declara su
 * propia version del evento. Los headers x-event-type / x-event-id permiten
 * trazar un mensaje en la consola de RabbitMQ.
 */
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MessagingProperties properties;

    /**
     * "pedido.creado" se publica en los dos exchanges:
     * - direct (pedidos.direct) con la clave exacta "pedido.creado" -> notificaciones
     * - topic (pedidos.topic) con la clave "pedido.creado" -> auditoria
     * Asi el mismo evento llega a dos consumidores distintos (fan-out).
     */
    public void publishOrderCreated(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                OrderCreatedEvent.TYPE,
                order.getId(),
                order.getUserEmail(),
                order.getTotal().toPlainString(),
                order.getItems().size(),
                LocalDateTime.now().toString());

        send(properties.getExchanges().getDirect(), properties.getRoutingKeys().getOrderCreated(), event);
        send(properties.getExchanges().getTopic(), properties.getRoutingKeys().getOrderCreated(), event);
    }

    /** "pedido.estado.actualizado" viaja por el exchange topic (patron pedido.estado.*). */
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

        send(properties.getExchanges().getTopic(), properties.getRoutingKeys().getOrderStatusChanged(), event);
    }

    private void send(String exchange, String routingKey, Object event) {
        byte[] body = objectMapper.writeValueAsBytes(event);
        Message message = MessageBuilder.withBody(body)
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setMessageId(UUID.randomUUID().toString())
                .setHeader("x-event-type", eventType(event))
                .setHeader("x-event-id", eventId(event))
                .setHeader("x-retry-count", 0)
                .build();

        rabbitTemplate.send(exchange, routingKey, message);
        log.info("Evento publicado -> exchange '{}' routingKey '{}' body={}",
                exchange, routingKey, new String(body, java.nio.charset.StandardCharsets.UTF_8));
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
