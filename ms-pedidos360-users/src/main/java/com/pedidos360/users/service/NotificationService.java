package com.pedidos360.users.service;

import com.pedidos360.users.messaging.event.OrderCreatedEvent;
import com.pedidos360.users.model.Notification;
import com.pedidos360.users.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Logica de negocio del consumidor "notificaciones.pedidos": genera la
 * notificacion del pedido para el cliente.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public Notification notifyOrderCreated(OrderCreatedEvent event) {
        Notification notification = Notification.builder()
                .userEmail(event.userEmail())
                .orderId(event.orderId())
                .subject("Pedido " + event.orderId() + " recibido")
                .body("Tu pedido por $" + event.total() + " (" + event.itemCount()
                        + " producto/s) fue registrado. Te avisaremos cuando cambie su estado.")
                .channel("email")
                .createdAt(LocalDateTime.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notificacion enviada a {} por el pedido {}", event.userEmail(), event.orderId());
        return saved;
    }
}
