package com.pedidos360.orders.event;

/**
 * Evento de dominio interno (no viaja por RabbitMQ) que indica que cambio el
 * estado de un pedido.
 */
public record OrderStatusChangedDomainEvent(Long orderId, String previousStatus) {
}
