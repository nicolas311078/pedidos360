package com.pedidos360.orders.service;

import com.pedidos360.orders.messaging.event.OrderStatusChangedEvent;
import com.pedidos360.orders.model.OrderTicket;
import com.pedidos360.orders.repository.OrderTicketRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logica de negocio del consumidor de la cola de tickets: genera el
 * comprobante del pedido a partir del evento de cambio de estado.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderTicketRepository orderTicketRepository;

    public OrderTicket generateTicket(OrderStatusChangedEvent event) {
        String ticketNumber = "TCK-" + event.orderId() + "-" + event.newStatus() + "-"
                + LocalDateTime.now().format(FORMATTER);

        OrderTicket ticket = OrderTicket.builder()
                .ticketNumber(ticketNumber)
                .orderId(event.orderId())
                .userEmail(event.userEmail())
                .orderStatus(event.newStatus())
                .total(event.total() != null ? event.total() : "0.00")
                .generatedAt(LocalDateTime.now())
                .build();

        OrderTicket saved = orderTicketRepository.save(ticket);
        log.info("Ticket generado para el pedido {} (estado {}): ticket {}",
                event.orderId(), event.newStatus(), saved.getTicketNumber());
        return saved;
    }
}
