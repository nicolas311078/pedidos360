package com.pedidos360.users.service;

import com.pedidos360.users.messaging.event.OrderCreatedEvent;
import com.pedidos360.users.messaging.event.OrderStatusChangedEvent;
import com.pedidos360.users.model.AuditEntry;
import com.pedidos360.users.repository.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Logica de negocio de los consumidores de Kafka (EP5).
 *
 * Guarda la misma traza que el consumidor de RabbitMQ pero marcando el origen
 * "Kafka", para que en la demo se distingan ambos flujos en la tabla de
 * auditoria. Es idempotente: si un evento se reentrega (Kafka garantiza
 * at-least-once, no exactly-once) no se duplica el registro.
 */
@Service
@RequiredArgsConstructor
public class OrderAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(OrderAnalyticsService.class);

    private final AuditEntryRepository auditEntryRepository;

    public void recordCreated(OrderCreatedEvent event) {
        String detail = "Kafka | Pedido creado por $" + event.total()
                + " (" + event.itemCount() + " producto/s)";
        saveIfNew(event.eventId(), event.eventType(), event.orderId(), event.userEmail(), detail);
    }

    public void recordStatusChanged(OrderStatusChangedEvent event) {
        String detail = "Kafka | Pedido " + event.orderId() + ": "
                + event.previousStatus() + " -> " + event.newStatus() + " ($" + event.total() + ")";
        saveIfNew(event.eventId(), event.eventType(), event.orderId(), event.userEmail(), detail);
    }

    private void saveIfNew(String eventId, String eventType, Long orderId, String userEmail, String detail) {
        if (eventId != null && auditEntryRepository.findFirstByEventId(eventId).isPresent()) {
            log.info("Evento {} ya procesado por Kafka (idempotente), se omite", eventId);
            return;
        }

        AuditEntry entry = AuditEntry.builder()
                .eventType(eventType)
                .eventId(eventId)
                .orderId(orderId)
                .userEmail(userEmail)
                .detail(detail)
                .createdAt(LocalDateTime.now())
                .build();

        auditEntryRepository.save(entry);
        log.info("Analitica Kafka registrada: evento {} del pedido {}", eventType, orderId);
    }
}
