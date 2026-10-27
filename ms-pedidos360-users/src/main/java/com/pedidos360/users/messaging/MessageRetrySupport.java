package com.pedidos360.users.messaging;

import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

/**
 * Soporte comun de los consumidores del microservicio: reintentos
 * controlados y paso a DLQ con ACK/NACK manual.
 */
@Component
@RequiredArgsConstructor
public class MessageRetrySupport {

    private static final Logger log = LoggerFactory.getLogger(MessageRetrySupport.class);

    public static final String RETRY_HEADER = "x-retry-count";
    public static final int MAX_RETRIES = 3;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public int retryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RETRY_HEADER);
        return value instanceof Number number ? number.intValue() : 0;
    }

    public void republish(Message original, Object payload, int retryCount) {
        byte[] body = objectMapper.writeValueAsBytes(payload);
        var builder = MessageBuilder.withBody(body)
                .setContentType("application/json")
                .setContentEncoding("UTF-8")
                .setMessageId(UUID.randomUUID().toString())
                .setHeader(RETRY_HEADER, retryCount);

        Object eventType = original.getMessageProperties().getHeaders().get("x-event-type");
        if (eventType != null) {
            builder.setHeader("x-event-type", eventType);
        }
        Object eventId = original.getMessageProperties().getHeaders().get("x-event-id");
        if (eventId != null) {
            builder.setHeader("x-event-id", eventId);
        }

        rabbitTemplate.send(
                original.getMessageProperties().getReceivedExchange(),
                original.getMessageProperties().getReceivedRoutingKey(),
                builder.build());
    }

    public void ack(Channel channel, Message message) throws IOException {
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    public void deadLetter(Channel channel, Message message, String reason) throws IOException {
        log.error("Mensaje enviado a DLQ (cola '{}'): {}", message.getMessageProperties().getConsumerQueue(), reason);
        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
    }
}
