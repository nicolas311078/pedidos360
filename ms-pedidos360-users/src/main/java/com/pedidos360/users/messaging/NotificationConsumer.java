package com.pedidos360.users.messaging;

import com.pedidos360.users.messaging.event.OrderCreatedEvent;
import com.pedidos360.users.service.NotificationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Consumidor de la cola "notificaciones.pedidos" (quorum, enlazada al
 * exchange directo pedidos.direct con la clave pedido.creado).
 *
 * ACK/NACK manual: ACK si la notificacion se registro, reintento con
 * republica ante fallas transitorias y NACK sin requeue (mensaje a DLQ) si el
 * payload es invalido o se agotaron los reintentos.
 */
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final MessageRetrySupport retrySupport;

    @RabbitListener(queues = "${messaging.queues.notifications.name}", ackMode = "MANUAL")
    public void onOrderCreated(Message message, Channel channel) throws IOException {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), OrderCreatedEvent.class);
        } catch (Exception ex) {
            log.error("Payload invalido en la cola de notificaciones: {}",
                    new String(message.getBody(), StandardCharsets.UTF_8), ex);
            retrySupport.deadLetter(channel, message, "payload invalido: " + ex.getMessage());
            return;
        }

        try {
            notificationService.notifyOrderCreated(event);
            retrySupport.ack(channel, message);
        } catch (Exception ex) {
            int retries = retrySupport.retryCount(message);
            if (retries < MessageRetrySupport.MAX_RETRIES) {
                log.warn("Falla al notificar el pedido {} (reintento {}/{}): {}",
                        event.orderId(), retries + 1, MessageRetrySupport.MAX_RETRIES, ex.getMessage());
                retrySupport.republish(message, event, retries + 1);
                retrySupport.ack(channel, message);
            } else {
                retrySupport.deadLetter(channel, message, "reintentos agotados: " + ex.getMessage());
            }
        }
    }
}
