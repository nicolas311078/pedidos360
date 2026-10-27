package com.pedidos360.orders.model;

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
 * Comprobante (ticket) generado de forma asincrona a partir del evento
 * "pedido.estado.actualizado".
 */
@Entity
@Table(name = "order_tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_number", nullable = false, unique = true)
    private String ticketNumber;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "order_status", nullable = false)
    private String orderStatus;

    @Column(nullable = false)
    private String total;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
