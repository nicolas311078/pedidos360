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
 * Puente entre los eventos de dominio y los dos brokers: RabbitMQ (EP3) y
 * Kafka (EP5). RabbitMQ sigue siendo el canal principal; Kafka se agrega como
 * canal de analitica/auditoria sin reemplazarlo.
 *
 * Los eventos se publican SOLO despues del commit de la transaccion
 * (TransactionPhase.AFTER_COMMIT): si la transaccion falla, no sale ningun
 * mensaje a la cola ni al topico. Esto evita el clasico problema de "mensaje
 * publicado para una operacion que despues se revierte".
 *
 * Cada broker se publica de forma independiente: si Kafka no responde, Rabbit
 * igual entrega el evento y el pedido no se ve afectado.
 */
@Component
@RequiredArgsConstructor
public class OrderEventBridge {

    private static final Logger log = LoggerFactory.getLogger(OrderEventBridge.class);

    private final OrderEventPublisher publisher;
    private final KafkaEventPublisher kafkaPublisher;
    private final OrderRepository orderRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedDomainEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(
                order -> {
                    publishSafely(() -> publisher.publishOrderCreated(order), "RabbitMQ", event.orderId());
                    publishSafely(() -> kafkaPublisher.publishOrderCreated(order), "Kafka", event.orderId());
                },
                () -> log.warn("No se encontro el pedido {} para publicar el evento de creacion", event.orderId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedDomainEvent event) {
        orderRepository.findById(event.orderId()).ifPresentOrElse(
                order -> {
                    OrderStatus previousStatus = parseStatus(event.previousStatus());
                    publishSafely(() -> publisher.publishOrderStatusChanged(order, previousStatus),
                            "RabbitMQ", event.orderId());
                    publishSafely(() -> kafkaPublisher.publishOrderStatusChanged(order, previousStatus),
                            "Kafka", event.orderId());
                },
                () -> log.warn("No se encontro el pedido {} para publicar el evento de estado", event.orderId()));
    }

    private void publishSafely(Runnable publication, String broker, Long orderId) {
        try {
            publication.run();
        } catch (RuntimeException ex) {
            log.error("Fallo al publicar el pedido {} en {} (RabbitMQ/Kafka funcionan de forma independiente)",
                    orderId, broker, ex);
        }
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
