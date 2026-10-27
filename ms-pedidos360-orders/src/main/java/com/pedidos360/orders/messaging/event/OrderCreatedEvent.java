package com.pedidos360.orders.messaging.event;

/**
 * Evento publicado cuando se crea un pedido.
 *
 * Viaja como JSON por RabbitMQ. Los campos son tipos simples (String, long,
 * int) para que el consumidor de otro microservicio lo deserialice sin
 * depender de clases compartidas.
 */
public record OrderCreatedEvent(
        String eventId,
        String eventType,
        Long orderId,
        String userEmail,
        String total,
        int itemCount,
        String occurredAt) {

    public static final String TYPE = "pedido.creado";
}
