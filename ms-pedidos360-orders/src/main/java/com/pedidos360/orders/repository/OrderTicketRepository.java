package com.pedidos360.orders.repository;

import com.pedidos360.orders.model.OrderTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderTicketRepository extends JpaRepository<OrderTicket, Long> {

    List<OrderTicket> findByOrderIdOrderByIdAsc(Long orderId);
}
