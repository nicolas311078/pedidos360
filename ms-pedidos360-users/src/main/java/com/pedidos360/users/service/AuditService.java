package com.pedidos360.users.service;

import com.pedidos360.users.messaging.event.OrderCreatedEvent;
import com.pedidos360.users.model.AuditEntry;
import com.pedidos360.users.repository.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Logica de negocio del consumidor "auditoria.pedidos": deja traza del evento
 * recibido por el exchange topic.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEntryRepository auditEntryRepository;

    public AuditEntry register(OrderCreatedEvent event) {
        AuditEntry entry = AuditEntry.builder()
                .eventType(event.eventType())
                .eventId(event.eventId())
                .orderId(event.orderId())
                .userEmail(event.userEmail())
                .detail("Pedido creado por $" + event.total() + " (" + event.itemCount() + " producto/s)")
                .createdAt(LocalDateTime.now())
                .build();

        AuditEntry saved = auditEntryRepository.save(entry);
        log.info("Auditoria registrada: evento {} del pedido {}", event.eventType(), event.orderId());
        return saved;
    }
}
