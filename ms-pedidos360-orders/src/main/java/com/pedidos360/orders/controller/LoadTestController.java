package com.pedidos360.orders.controller;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/kafka/load-test")
@ConditionalOnProperty(prefix = "kafka.load-test", name = "enabled", havingValue = "true")
public class LoadTestController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int maxMessages;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LoadTestController(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${kafka.load-test.topic}") String topic,
            @Value("${kafka.load-test.max-messages:2000}") int maxMessages) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.maxMessages = maxMessages;
    }

    @PostMapping
    public LoadTestResult publish(@RequestParam(defaultValue = "2000") int messages) {
        if (messages < 1 || messages > maxMessages) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "messages debe estar entre 1 y " + maxMessages);
        }
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya hay una prueba Kafka en curso");
        }

        String batchId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        try {
            CompletableFuture<?>[] sends = new CompletableFuture<?>[messages];
            for (int sequence = 1; sequence <= messages; sequence++) {
                String payload = "{\"batchId\":\"" + batchId
                        + "\",\"sequence\":" + sequence
                        + ",\"generatedAt\":\"" + Instant.now() + "\"}";
                sends[sequence - 1] = kafkaTemplate.send(
                        new ProducerRecord<>(topic, Integer.toString(sequence), payload));
            }
            CompletableFuture.allOf(sends).join();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            return new LoadTestResult(batchId, topic, messages, elapsedMs);
        } catch (CompletionException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Kafka no pudo confirmar todos los mensajes de la prueba",
                    ex.getCause());
        } finally {
            running.set(false);
        }
    }

    public record LoadTestResult(String batchId, String topic, int publishedMessages, long elapsedMs) {
    }
}
