package com.pedidos360.users.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "kafka.load-test", name = "enabled", havingValue = "true")
public class SlowConsumer {

    private static final Logger log = LoggerFactory.getLogger(SlowConsumer.class);
    private final long delayMs;
    private final AtomicInteger processed = new AtomicInteger();

    public SlowConsumer(@Value("${kafka.load-test.delay-ms:10}") long delayMs) {
        this.delayMs = Math.max(0, delayMs);
    }

    @KafkaListener(
            topics = "${kafka.load-test.topic}",
            groupId = "${kafka.load-test.group-id}",
            concurrency = "${kafka.load-test.concurrency:1}")
    public void onMessage(String payload, Acknowledgment acknowledgment) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consumidor lento interrumpido", ex);
        }

        acknowledgment.acknowledge();
        int total = processed.incrementAndGet();
        if (total % 100 == 0) {
            log.info("Prueba Kafka: {} mensajes lentos procesados", total);
        }
    }
}
