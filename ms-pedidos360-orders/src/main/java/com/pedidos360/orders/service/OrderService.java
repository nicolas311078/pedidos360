package com.pedidos360.orders.service;

import com.pedidos360.orders.dto.CreateOrderRequest;
import com.pedidos360.orders.exception.ResourceNotFoundException;
import com.pedidos360.orders.model.Order;
import com.pedidos360.orders.model.OrderItem;
import com.pedidos360.orders.model.OrderStatus;
import com.pedidos360.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestClient restClient;

    @Value("${catalog.url}")
    private String catalogUrl;

    @Transactional
    public Order create(String userEmail, CreateOrderRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalStateException("La orden debe tener al menos un producto");
        }

        Order order = Order.builder()
                .userEmail(userEmail)
                .status(OrderStatus.CREADO)
                .createdAt(LocalDateTime.now())
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.ItemLine line : request.items()) {
            ProductSnapshot product = fetchProduct(line.productId());

            if (product.stock() < line.quantity()) {
                throw new IllegalStateException("Stock insuficiente para el producto: " + product.name());
            }

            BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(line.quantity()));
            total = total.add(subtotal);

            OrderItem item = OrderItem.builder()
                    .productId(product.id())
                    .productName(product.name())
                    .price(product.price())
                    .quantity(line.quantity())
                    .subtotal(subtotal)
                    .build();
            order.addItem(item);
        }

        order.setTotal(total);
        return orderRepository.save(order);
    }

    public List<Order> findByUserEmail(String userEmail) {
        return orderRepository.findByUserEmail(userEmail);
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public Order findByIdAndUser(Long id, String userEmail) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
        if (!order.getUserEmail().equals(userEmail)) {
            throw new ResourceNotFoundException("Orden no encontrada: " + id);
        }
        return order;
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden no encontrada: " + id));
    }

    @Transactional
    public Order updateStatus(Long id, OrderStatus newStatus) {
        Order order = findById(id);
        if (order.getStatus() == OrderStatus.CANCELADO || order.getStatus() == OrderStatus.ENTREGADO) {
            throw new IllegalStateException("No se puede cambiar el estado desde " + order.getStatus());
        }
        if (!isAllowedTransition(order.getStatus(), newStatus)) {
            throw new IllegalStateException(
                    "No se puede pasar de " + order.getStatus() + " a " + newStatus);
        }
        order.setStatus(newStatus);

        // Regla de negocio de la guía: el stock disminuye al ACEPTAR el pedido.
        if (newStatus == OrderStatus.ACEPTADO) {
            for (OrderItem item : order.getItems()) {
                decrementStock(item.getProductId(), item.getQuantity());
            }
        }
        return orderRepository.save(order);
    }

    /**
     * Transiciones válidas: CREADO -> ACEPTADO (o CANCELADO),
     * ACEPTADO -> EN_PREPARACION, EN_PREPARACION -> DESPACHADO,
     * DESPACHADO -> ENTREGADO. CANCELADO se permite en cualquier estado activo.
     */
    private boolean isAllowedTransition(OrderStatus current, OrderStatus next) {
        if (next == OrderStatus.CANCELADO || next == current) {
            return true;
        }
        return switch (current) {
            case CREADO -> next == OrderStatus.ACEPTADO;
            case ACEPTADO -> next == OrderStatus.EN_PREPARACION;
            case EN_PREPARACION -> next == OrderStatus.DESPACHADO;
            case DESPACHADO -> next == OrderStatus.ENTREGADO;
            default -> false;
        };
    }

    private ProductSnapshot fetchProduct(Long productId) {
        try {
            return restClient.get()
                    .uri(catalogUrl + "/api/catalog/products/{id}", productId)
                    .retrieve()
                    .body(ProductSnapshot.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Producto no disponible: " + productId);
            }
            throw new IllegalStateException("Error consultando el producto " + productId);
        }
    }

    private void decrementStock(Long productId, int quantity) {
        restClient.patch()
                .uri(catalogUrl + "/api/catalog/products/{id}/stock", productId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("delta", -quantity))
                .retrieve()
                .toBodilessEntity();
    }
}