package com.pedidos360.orders.service;

import java.math.BigDecimal;

public record ProductSnapshot(
        Long id,
        String name,
        BigDecimal price,
        Integer stock
) {
}