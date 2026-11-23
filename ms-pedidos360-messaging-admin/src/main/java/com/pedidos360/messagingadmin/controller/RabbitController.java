package com.pedidos360.messagingadmin.controller;

import com.pedidos360.messagingadmin.dto.MessagingDtos.BindingInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.ExchangeInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewBinding;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewExchange;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewQueue;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NodeInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.QueueInfo;
import com.pedidos360.messagingadmin.service.RabbitManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Expone el estado de RabbitMQ al BFF (ruta /api/messaging/**). */
@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
public class RabbitController {

    private final RabbitManagementService service;

    @GetMapping("/queues")
    public List<QueueInfo> queues() {
        return service.queues();
    }

    @GetMapping("/exchanges")
    public List<ExchangeInfo> exchanges() {
        return service.exchanges();
    }

    @GetMapping("/bindings")
    public List<BindingInfo> bindings() {
        return service.bindings();
    }

    @GetMapping("/nodes")
    public List<NodeInfo> nodes() {
        return service.nodes();
    }

    @PostMapping("/queues")
    public void createQueue(@RequestBody NewQueue request) {
        service.createQueue(request);
    }

    @DeleteMapping("/queues/{name}")
    public void deleteQueue(@PathVariable String name) {
        service.deleteQueue(name);
    }

    @PostMapping("/exchanges")
    public void createExchange(@RequestBody NewExchange request) {
        service.createExchange(request);
    }

    @DeleteMapping("/exchanges/{name}")
    public void deleteExchange(@PathVariable String name) {
        service.deleteExchange(name);
    }

    @PostMapping("/bindings")
    public void createBinding(@RequestBody NewBinding request) {
        service.createBinding(request);
    }

    @DeleteMapping("/bindings")
    public void deleteBinding(
            @RequestParam String exchange,
            @RequestParam String queue,
            @RequestParam String routingKey) {
        service.deleteBinding(exchange, queue, routingKey);
    }
}
