package com.pedidos360.users.messaging;

import com.pedidos360.users.messaging.event.OrderCreatedEvent;
import com.pedidos360.users.service.AuditService;
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
 * Consumidor de la cola "auditoria.pedidos" (quorum, enlazada al exchange
 * topic pedidos.topic con la clave pedido.creado).
 *
 * ACK/NACK manual con reintentos y paso a DLQ.
 */
@Component
@RequiredArgsConstructor
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final MessageRetrySupport retrySupport;

    @RabbitListener(queues = "${messaging.queues.audit.name}", ackMode = "MANUAL")
    public void onOrderCreated(Message message, Channel channel) throws IOException {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), OrderCreatedEvent.class);
        } catch (Exception ex) {
            log.error("Payload invalido en la cola de auditoria: {}",
                    new String(message.getBody(), StandardCharsets.UTF_8), ex);
            retrySupport.deadLetter(channel, message, "payload invalido: " + ex.getMessage());
            return;
        }

        try {
            auditService.register(event);
            retrySupport.ack(channel, message);
        } catch (Exception ex) {
            int retries = retrySupport.retryCount(message);
            if (retries < MessageRetrySupport.MAX_RETRIES) {
                log.warn("Falla al registrar la auditoria del pedido {} (reintento {}/{}): {}",
                        event.orderId(), retries + 1, MessageRetrySupport.MAX_RETRIES, ex.getMessage());
                retrySupport.republish(message, event, retries + 1);
                retrySupport.ack(channel, message);
            } else {
                retrySupport.deadLetter(channel, message, "reintentos agotados: " + ex.getMessage());
            }
        }
    }
}
