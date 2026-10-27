package com.pedidos360.orders.messaging.event;

/**
 * Evento publicado cuando cambia el estado de un pedido.
 *
 * Lo consume el worker de tickets (cola quorum) para generar el comprobante
 * del pedido de forma asincrona.
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
