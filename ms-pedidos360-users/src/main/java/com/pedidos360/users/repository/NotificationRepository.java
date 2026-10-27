package com.pedidos360.users.repository;

import com.pedidos360.users.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserEmailOrderByIdDesc(String userEmail);
}
