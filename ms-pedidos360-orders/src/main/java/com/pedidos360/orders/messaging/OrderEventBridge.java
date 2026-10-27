package com.pedidos360.orders.messaging;

import com.pedidos360.orders.event.OrderCreatedDomainEvent;
import com.pedidos360.orders.event.OrderStatusChangedDomainEvent;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.OrderStatus;
import com.pedidos360.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Puente entre los eventos de dominio y RabbitMQ.
 *
 * Los eventos se publican SOLO despues del commit de la transaccion
 * (TransactionPhase.AFTER_COMMIT): si la transaccion falla, no sale ningun
 * mensaje a la cola. Esto evita el clasico problema de "mensaje publicado
 * para una operacion que despues se revierte".
 */
@Component
@RequiredArgsConstructor
public class OrderEventBridge {

    private static final Logger log = LoggerFactory.getLogger(OrderEventBridge.class);

    private final OrderEventPublisher publisher;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedDomainEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(
                publisher::publishOrderCreated,
                () -> log.warn("No se encontro el pedido {} para publicar el evento de creacion", event.orderId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedDomainEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(
                order -> publisher.publishOrderStatusChanged(order, parseStatus(event.previousStatus())),
                () -> log.warn("No se encontro el pedido {} para publicar el evento de estado", event.orderId()));
    }

    private OrderStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
