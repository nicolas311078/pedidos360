package com.pedidos360.orders.messaging;

import com.pedidos360.orders.messaging.event.OrderStatusChangedEvent;
import com.pedidos360.orders.service.TicketService;
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
 * Consumidor de la cola "tickets.pedidos" (quorum, enlazada al exchange topic
 * pedidos.topic con la clave pedido.estado.actualizado).
 *
 * ACK/NACK manual:
 * - ACK cuando el comprobante se genero correctamente.
 * - Reintento con republica del mensaje (hasta MAX_RETRIES) ante fallas
 *   transitorias.
 * - NACK sin requeue cuando el payload es invalido o se agotaron los
 *   reintentos: el broker mueve el mensaje a la DLQ "tickets.pedidos.dlq".
 */
@Component
@RequiredArgsConstructor
public class TicketConsumer {

    private static final Logger log = LoggerFactory.getLogger(TicketConsumer.class);

    private final ObjectMapper objectMapper;
    private final TicketService ticketService;
    private final MessageRetrySupport retrySupport;

    @RabbitListener(queues = "${messaging.queues.tickets.name}", ackMode = "MANUAL")
    public void onOrderStatusChanged(Message message, Channel channel) throws IOException {
        OrderStatusChangedEvent event;
        try {
            event = objectMapper.readValue(message.getBody(), OrderStatusChangedEvent.class);
        } catch (Exception ex) {
            log.error("Payload invalido en la cola de tickets: {}",
                    new String(message.getBody(), StandardCharsets.UTF_8), ex);
            retrySupport.deadLetter(channel, message, "payload invalido: " + ex.getMessage());
            return;
        }

        try {
            ticketService.generateTicket(event);
            retrySupport.ack(channel, message);
        } catch (Exception ex) {
            int retries = retrySupport.retryCount(message);
            if (retries < MessageRetrySupport.MAX_RETRIES) {
                log.warn("Falla al generar el ticket del pedido {} (reintento {}/{}): {}",
                        event.orderId(), retries + 1, MessageRetrySupport.MAX_RETRIES, ex.getMessage());
                retrySupport.republish(message, event, retries + 1);
                retrySupport.ack(channel, message);
            } else {
                retrySupport.deadLetter(channel, message, "reintentos agotados: " + ex.getMessage());
            }
        }
    }
}
