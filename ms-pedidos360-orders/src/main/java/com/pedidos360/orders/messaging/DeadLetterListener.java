package com.pedidos360.orders.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Monitor de la cola de mensajes no entregados (DLQ) del microservicio.
 *
 * Registra en logs cada mensaje rechazado junto con los headers x-death que
 * agrega RabbitMQ (exchange y cola de origen, motivo del rechazo, reintentos).
 *
 * Usa ackMode AUTO de forma explicita: el contenedor de escucha del servicio
 * trabaja en modo MANUAL para los consumidores de negocio (ACK/NACK
 * controlados), y este monitor no debe heredar ese modo porque dejaria los
 * mensajes de la DLQ en estado "unacked" de forma indefinida.
 */
@Component
@RequiredArgsConstructor
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    @RabbitListener(queues = "${messaging.queues.tickets.deadLetterQueue}", ackMode = "AUTO",
            autoStartup = "${messaging.dlq-monitor.enabled:true}")
    public void onDeadLetter(Message message) {
        log.error("[DLQ tickets.pedidos] Mensaje no entregado -> headers={} body={}",
                message.getMessageProperties().getHeaders(),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
