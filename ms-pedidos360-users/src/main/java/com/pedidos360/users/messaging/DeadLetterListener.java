package com.pedidos360.users.messaging;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Monitor de las DLQ del microservicio: registra en logs cada mensaje que no se
 * pudo entregar, incluyendo los headers x-death que agrega RabbitMQ.
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

    @RabbitListener(queues = "${messaging.queues.notifications.deadLetterQueue}", ackMode = "AUTO",
            autoStartup = "${messaging.dlq-monitor.enabled:true}")
    public void onNotificationDeadLetter(Message message) {
        log.error("[DLQ notificaciones.pedidos] Mensaje no entregado -> headers={} body={}",
                message.getMessageProperties().getHeaders(),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }

    @RabbitListener(queues = "${messaging.queues.audit.deadLetterQueue}", ackMode = "AUTO",
            autoStartup = "${messaging.dlq-monitor.enabled:true}")
    public void onAuditDeadLetter(Message message) {
        log.error("[DLQ auditoria.pedidos] Mensaje no entregado -> headers={} body={}",
                message.getMessageProperties().getHeaders(),
                new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
