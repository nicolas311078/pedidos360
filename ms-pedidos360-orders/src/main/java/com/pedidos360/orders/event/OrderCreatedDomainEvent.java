package com.pedidos360.orders.event;

/**
 * Evento de dominio interno (no viaja por RabbitMQ) que indica que se creo un
 * pedido. El puente OrderEventBridge lo publica a RabbitMQ despues del commit
 * de la transaccion.
 */
public record OrderCreatedDomainEvent(Long orderId) {
}
