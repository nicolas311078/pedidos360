package com.pedidos360.users.repository;

import com.pedidos360.users.model.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findTop20ByOrderByIdDesc();

    /**
     * Usado por el consumidor de Kafka para ser idempotente: si el mismo
     * eventId llega dos veces (Kafka es at-least-once), no se guarda otra vez.
     */
    Optional<AuditEntry> findFirstByEventId(String eventId);
}
