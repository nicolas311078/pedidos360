package com.pedidos360.users.messaging.event;

/**
 * Copia del evento "pedido.creado" que produce ms-pedidos360-orders.
 *
 * Cada microservicio declara su propia version del contrato (payload en JSON
 * simple), de modo que los servicios no comparten clases ni dependencias.
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
