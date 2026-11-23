package com.pedidos360.messagingadmin.dto;

/**
 * Contratos del servicio admin de mensajeria. Son identicos a los que ya
 * consume el frontend (FRONTEND/src/api/messaging.ts) para Rabbit y los nuevos
 * de Kafka (EP5).
 */
public final class MessagingDtos {

    private MessagingDtos() {
    }

    public record QueueInfo(
            String name,
            String type,
            boolean durable,
            long messages,
            long messagesReady,
            long messagesUnacknowledged,
            long consumers,
            String state) {
    }

    public record ExchangeInfo(String name, String type, boolean durable, boolean internal) {
    }

    public record BindingInfo(String source, String destination, String destinationType, String routingKey) {
    }

    public record NodeInfo(String name, String type, boolean running) {
    }

    public record NewQueue(
            String name,
            boolean durable,
            boolean quorum,
            String deadLetterExchange,
            String deadLetterRoutingKey) {
    }

    public record NewExchange(String name, String type, boolean durable) {
    }

    public record NewBinding(String exchange, String queue, String routingKey) {
    }

    public record KafkaTopicInfo(String name, int partitions, int replicationFactor) {
    }

    public record KafkaGroupInfo(
            String groupId,
            String state,
            int members,
            long lag,
            java.util.List<String> topics) {
    }

    public record NewKafkaTopic(String name, int partitions, int replicationFactor) {
    }
}
