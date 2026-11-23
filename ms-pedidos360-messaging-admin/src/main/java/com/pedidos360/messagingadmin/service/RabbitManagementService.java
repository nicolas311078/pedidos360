package com.pedidos360.messagingadmin.service;

import com.pedidos360.messagingadmin.dto.MessagingDtos.BindingInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.ExchangeInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewBinding;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewExchange;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NewQueue;
import com.pedidos360.messagingadmin.dto.MessagingDtos.NodeInfo;
import com.pedidos360.messagingadmin.dto.MessagingDtos.QueueInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Servicio que traduce la Management API de RabbitMQ a los DTOs que consume
 * el frontend. No reemplaza a RabbitAdmin: solo expone el estado del cluster.
 */
@Service
@RequiredArgsConstructor
public class RabbitManagementService {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9._-]{1,128}$");
    private static final Set<String> VALID_EXCHANGE_TYPES =
            Set.of("direct", "topic", "fanout", "headers");
    private static final Set<String> PROTECTED_QUEUES = Set.of(
            "notificaciones.pedidos", "notificaciones.pedidos.dlq",
            "auditoria.pedidos", "auditoria.pedidos.dlq",
            "tickets.pedidos", "tickets.pedidos.dlq");
    private static final Set<String> PROTECTED_EXCHANGES =
            Set.of("pedidos.direct", "pedidos.topic", "pedidos.dlx");

    private final RestClient rabbitManagementClient;
    private final ObjectMapper objectMapper;

    @Value("${rabbit.management.vhost}")
    private String vhost;

    public List<QueueInfo> queues() {
        List<QueueInfo> result = new ArrayList<>();
        for (Map<String, Object> row : list("/api/queues")) {
            result.add(new QueueInfo(
                    str(row, "name"),
                    str(row, "type"),
                    bool(row, "durable"),
                    num(row, "messages"),
                    num(row, "messages_ready"),
                    num(row, "messages_unacknowledged"),
                    num(row, "consumers"),
                    str(row, "state")));
        }
        return result;
    }

    public List<ExchangeInfo> exchanges() {
        List<ExchangeInfo> result = new ArrayList<>();
        for (Map<String, Object> row : list("/api/exchanges")) {
            result.add(new ExchangeInfo(
                    str(row, "name"), str(row, "type"), bool(row, "durable"), bool(row, "internal")));
        }
        return result;
    }

    public List<BindingInfo> bindings() {
        List<BindingInfo> result = new ArrayList<>();
        for (Map<String, Object> row : list("/api/bindings")) {
            result.add(new BindingInfo(
                    str(row, "source"), str(row, "destination"),
                    str(row, "destination_type"), str(row, "routing_key")));
        }
        return result;
    }

    public List<NodeInfo> nodes() {
        List<NodeInfo> result = new ArrayList<>();
        for (Map<String, Object> row : list("/api/nodes")) {
            result.add(new NodeInfo(str(row, "name"), str(row, "type"), bool(row, "running")));
        }
        return result;
    }

    public void createQueue(NewQueue request) {
        validateName(request.name());
        Map<String, Object> body = new HashMap<>();
        body.put("durable", request.durable());
        body.put("auto_delete", false);
        body.put("arguments", queueArguments(request));

        rabbitManagementClient.post()
                .uri("/api/queues/{vhost}/{name}", encodedVhost(), encode(request.name()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteQueue(String name) {
        validateName(name);
        if (PROTECTED_QUEUES.contains(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cola protegida: " + name);
        }
        rabbitManagementClient.delete()
                .uri("/api/queues/{vhost}/{name}", encodedVhost(), encode(name))
                .retrieve()
                .toBodilessEntity();
    }

    public void createExchange(NewExchange request) {
        validateName(request.name());
        if (request.type() == null || !VALID_EXCHANGE_TYPES.contains(request.type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de exchange inválido");
        }
        Map<String, Object> body = new HashMap<>();
        body.put("type", request.type());
        body.put("durable", request.durable());
        body.put("auto_delete", false);

        rabbitManagementClient.post()
                .uri("/api/exchanges/{vhost}/{name}", encodedVhost(), encode(request.name()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteExchange(String name) {
        validateName(name);
        if (PROTECTED_EXCHANGES.contains(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exchange protegido: " + name);
        }
        rabbitManagementClient.delete()
                .uri("/api/exchanges/{vhost}/{name}", encodedVhost(), encode(name))
                .retrieve()
                .toBodilessEntity();
    }

    public void createBinding(NewBinding request) {
        validateName(request.exchange());
        validateName(request.queue());
        Map<String, Object> body = new HashMap<>();
        body.put("routing_key", request.routingKey());
        body.put("arguments", Map.of());

        rabbitManagementClient.post()
                .uri("/api/bindings/{vhost}/e/{exchange}/q/{queue}",
                        encodedVhost(), encode(request.exchange()), encode(request.queue()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteBinding(String exchange, String queue, String routingKey) {
        rabbitManagementClient.delete()
                .uri("/api/bindings/{vhost}/e/{exchange}/q/{queue}/{routingKey}",
                        encodedVhost(), encode(exchange), encode(queue), encode(routingKey))
                .retrieve()
                .toBodilessEntity();
    }

    private static void validateName(String name) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre inválido: " + name);
        }
    }

    private Map<String, Object> queueArguments(NewQueue request) {
        Map<String, Object> args = new HashMap<>();
        if (request.quorum()) {
            args.put("x-queue-type", "quorum");
        }
        if (request.deadLetterExchange() != null && !request.deadLetterExchange().isBlank()) {
            args.put("x-dead-letter-exchange", request.deadLetterExchange());
            args.put("x-dead-letter-routing-key",
                    request.deadLetterRoutingKey() == null ? "" : request.deadLetterRoutingKey());
        }
        return args;
    }

    private List<Map<String, Object>> list(String path) {
        String json = rabbitManagementClient.get()
                .uri(path)
                .retrieve()
                .body(String.class);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    private String encodedVhost() {
        return encode(vhost);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static long num(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static boolean bool(Map<String, Object> row, String key) {
        return Boolean.TRUE.equals(row.get(key));
    }
}
