package com.pedidos360.orders.controller;

import com.pedidos360.orders.dto.CreateOrderRequest;
import com.pedidos360.orders.dto.StatusUpdateRequest;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> create(@RequestHeader("X-User-Email") String userEmail,
                                        @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(201).body(orderService.create(userEmail, request));
    }

    @GetMapping
    public List<Order> findByUser(@RequestHeader("X-User-Email") String userEmail) {
        return orderService.findByUserEmail(userEmail);
    }

    @GetMapping("/all")
    public List<Order> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public Order findById(@RequestHeader("X-User-Email") String userEmail, @PathVariable Long id) {
        return orderService.findByIdAndUser(id, userEmail);
    }

    @PutMapping("/{id}/status")
    public Order updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return orderService.updateStatus(id, request.status());
    }
}