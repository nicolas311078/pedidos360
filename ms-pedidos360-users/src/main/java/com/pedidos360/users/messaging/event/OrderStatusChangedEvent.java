package com.pedidos360.users.messaging.event;

/**
 * Copia del evento "pedido.estado.actualizado" que produce
 * ms-pedidos360-orders. Se deserializa del tópico Kafka
 * "pedidos.estados.v1" con el mismo payload simple que viaja por RabbitMQ.
 */
public record OrderStatusChangedEvent(
        String eventId,
        String eventType,
        Long orderId,
        String userEmail,
        String previousStatus,
        String newStatus,
        String total,
        String occurredAt) {

    public static final String TYPE = "pedido.estado.actualizado";
}
