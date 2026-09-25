package com.pedidos360.orders.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotNull List<ItemLine> items
) {
    public record ItemLine(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}