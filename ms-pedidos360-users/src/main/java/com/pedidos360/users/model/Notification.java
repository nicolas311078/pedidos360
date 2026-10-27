package com.pedidos360.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notificacion enviada al cliente por el consumidor asincrono
 * "notificaciones.pedidos".
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private String channel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
