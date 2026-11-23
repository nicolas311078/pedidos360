# EP5 — Kafka en Pedidos360 (entrega)

Kafka **se agrega**, no reemplaza a RabbitMQ. RabbitMQ sigue siendo el canal transaccional de notificaciones/auditoría (`pedido.creado`), y Kafka se usa como canal de analítica/auditoría de eventos con retención y replay.

---

## 1. Arquitectura

```
                         AFTER_COMMIT
 OrderService ──► OrderEventBridge ──┬──► OrderEventPublisher ──► RabbitMQ
                                     │      (pedidos.direct / pedidos.topic)
                                     └──► KafkaEventPublisher ──► Kafka
                                            pedidos.creados.v1
                                            pedidos.estados.v1

 Kafka ──► ms-pedidos360-users ──► OrderAnalyticsService ──► audit_entries (idempotente por event_id)
 Rabbit ─► ms-pedidos360-users ──► NotificationService / AuditService (sin cambios)
```

* `OrderEventBridge` publica en ambos brokers con `try/catch` independientes: **si Kafka falla, Rabbit igual entrega el evento** y la orden no se ve afectada.
* Publicación solo en `TransactionPhase.AFTER_COMMIT`: si la transacción se revierte, no sale ningún mensaje.
* La clave del registro de Kafka es `orderId` → todos los eventos de un pedido caen en la misma partición y conservan su orden.

---

## 2. Infraestructura (`docker-compose.yml`)

| Servicio | Imagen | Función | URL |
|---|---|---|---|
| `zookeeper-1/2/3` | `confluentinc/cp-zookeeper:7.6.1` | Quórum Zookeeper | internas :2181 |
| `kafka-1/2/3` | `confluentinc/cp-kafka:7.6.1` | 3 brokers | `localhost:9092/9093/9094`, internos `:29092` |
| `kafka-ui` | `provectuslabs/kafka-ui:v0.7.2` | Consola web | http://localhost:8085 |
| `ms-pedidos360-messaging-admin` | build local | Admin REST de Rabbit + Kafka | http://localhost:8084 |

Parámetros relevantes: `KAFKA_DEFAULT_REPLICATION_FACTOR=3`, `KAFKA_NUM_PARTITIONS=3`, `min.insync.replicas=2`, `auto.create.topics.enable=true`, retención 168 h.

Tópicos creados:

| Tópico | Particiones | Réplicas | Productor | Consumidor |
|---|---:|---:|---|---|
| `pedidos.creados.v1` | 3 | 3 | orders | `pedidos-created-group` (users) |
| `pedidos.estados.v1` | 3 | 3 | orders | `pedidos-status-group` (users) |

---

## 3. Contrato de eventos

`x-event-type` y `x-event-id` viajan como headers del record; el body es el **mismo DTO** que se publica por RabbitMQ.

`pedidos.creados.v1`:

```json
{
  "eventId": "27710a10-dc0c-4d2a-9c1c-b8fb92eb44d3",
  "eventType": "pedido.creado",
  "orderId": 58,
  "userEmail": "cliente@tudominio.cl",
  "total": "2000.00",
  "itemCount": 1,
  "occurredAt": "2026-11-23T08:16:40.4225803"
}
```

`pedidos.estados.v1`:

```json
{
  "eventId": "32cda4a6-c58f-4f2e-b544-e436526890e4",
  "eventType": "pedido.estado.actualizado",
  "orderId": 58,
  "userEmail": "cliente@tudominio.cl",
  "previousStatus": "CREADO",
  "status": "ACEPTADO",
  "total": "2000.00",
  "occurredAt": "2026-11-23T08:17:10.1200000"
}
```

---

## 4. Código

| Archivo | Responsabilidad |
|---|---|
| `ms-pedidos360-orders/.../messaging/KafkaEventPublisher.java` | Serializa con `tools.jackson.databind.ObjectMapper` y envía con `KafkaTemplate<String,String>` |
| `ms-pedidos360-orders/.../messaging/OrderEventBridge.java` | Puente dual Rabbit + Kafka tras el commit |
| `ms-pedidos360-orders/.../config/KafkaTopicsProperties.java` | Nombres de tópicos desde configuración |
| `ms-pedidos360-users/.../messaging/KafkaOrderCreatedConsumer.java` | Consume `pedidos.creados.v1`, ACK manual, NACK con reintento |
| `ms-pedidos360-users/.../messaging/KafkaOrderStatusConsumer.java` | Consume `pedidos.estados.v1` |
| `ms-pedidos360-users/.../service/OrderAnalyticsService.java` | Idempotente: `findFirstByEventId` antes de grabar |
| `ms-pedidos360-users/.../repository/AuditEntryRepository.java` | `findFirstByEventId` |

**Decisión técnica:** se usa `StringSerializer`/`StringDeserializer` + `ObjectMapper` de Jackson 3 (`tools.jackson`), no `JsonSerializer`, para no mezclar Jackson 2 y 3 en el classpath de Spring Boot 4.1. El starter correcto es `org.springframework.boot:spring-boot-starter-kafka`. En users, `spring.kafka.listener.ack-mode: manual_immediate` permite `ack.acknowledge()` y `ack.nack(Duration)`.

**Ojo con el YAML:** hay dos bloques distintos. `spring.kafka` (indentado, config del cliente) y `kafka:` raíz (columna 0, nombres de tópicos/grupos propios). No son lo mismo.

Variables de entorno: `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CREATED_TOPIC`, `KAFKA_STATUS_TOPIC`, `KAFKA_PARTITIONS`, `KAFKA_REPLICATION_FACTOR`, `KAFKA_RETENTION_MS`, `KAFKA_CLEANUP_POLICY`, `KAFKA_CREATED_GROUP`, `KAFKA_STATUS_GROUP`, `KAFKA_ADMIN_URL`.

---

## 5. Servicio admin y seguridad

`ms-pedidos360-messaging-admin` (:8084) expone:

| Ruta | Descripción |
|---|---|
| `GET /api/kafka/topics` · `POST` · `DELETE /{name}` | Administración de tópicos (AdminClient) |
| `GET /api/kafka/consumer-groups` | Estado, miembros y lag calculado con `listOffsets(latest)` |
| `GET /api/kafka/brokers` | Nodos del cluster |
| `GET/POST/DELETE /api/messaging/*` | Consola Rabbit existente (validación `400`, recursos protegidos `409`) |

El BFF (`ProxyForwardFilter`) enruta `/api/kafka/**` y `/api/messaging/**` al servicio admin y exige rol `ADMIN`:

| Caso | Resultado |
|---|---|
| Sin token | `401` |
| Token `CLIENTE` | `403` |
| Token `ADMIN` | `200` |

El frontend `/admin/mensajeria` tiene dos pestañas: **RabbitMQ** y **Kafka** (tópicos, brokers, consumer groups y creación de tópicos).

---

## 6. Verificación paso a paso

```bash
# 1) Infraestructura
docker compose up -d
docker compose ps                       # 16 contenedores

# 2) Estado de Kafka
curl http://localhost:8084/api/kafka/topics
curl http://localhost:8084/api/kafka/consumer-groups
curl http://localhost:8084/api/kafka/brokers
# Consola: http://localhost:8085

# 3) Token ADMIN local (sin OAuth)
node aws/genjwt.js "<JWT_SECRET del .env>" ADMIN admin@tudominio.cl
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/kafka/topics

# 4) Probar seguridad
# sin token -> 401, CLIENTE -> 403, ADMIN -> 200

# 5) Evento real: crear una orden desde http://localhost:4200 (o POST /api/orders)
curl -H "Authorization: Bearer <token>" \
     -H "Content-Type: application/json" \
     -d '{"items":[{"productId":11,"quantity":1}]}' \
     http://localhost:8080/api/orders

# 6) Confirmar consumo y idempotencia
docker exec pedidos360-db psql -U <DB_USER> -d users_db \
  -c "SELECT event_type, event_id, order_id FROM audit_entries WHERE order_id = <id>;"
curl http://localhost:8084/api/kafka/consumer-groups   # lag = 0
```

---

## 7. Demo EP6 (guion de 5 minutos)

1. Mostrar `docker compose ps` con los 3 brokers, Zookeeper y Kafka UI.
2. Abrir Kafka UI → tópicos `pedidos.creados.v1` / `pedidos.estados.v1`, 3 particiones y 3 réplicas.
3. Mostrar `/admin/mensajeria`, pestaña Kafka: brokers, consumer groups con lag 0.
4. Crear una orden desde el frontend como CLIENTE.
5. Volver a Kafka UI y mostrar el mensaje nuevo con su `x-event-id`.
6. Mostrar en `audit_entries` el registro con ese `event_id` (analítica) **y** el registro del consumidor Rabbit (auditoría) → convivencia de ambos brokers.
7. Cambiar el estado de la orden como OPERADOR y mostrar el evento en `pedidos.estados.v1`.
8. (Opcional) parar un broker con `docker compose stop kafka-3` y demostrar que el productor sigue publicando con `min.insync.replicas=2`; luego `docker compose start kafka-3`.

---

## 8. Troubleshooting

| Síntoma | Causa / solución |
|---|---|
| `spring-kafka` no compila | Boot 4.1 usa `spring-boot-starter-kafka` |
| `ack.nack(Duration)` no existe | En users falta `ack-mode: manual_immediate` |
| Jackson 2/3 conflict | Usar `tools.jackson.databind.ObjectMapper` y String serializer |
| `localhost:8084` no responde desde el BFF | Dentro de Compose la URL es `http://ms-pedidos360-messaging-admin:8084` |
| Imagen de Kafka UI incorrecta | Usar `provectuslabs/kafka-ui:v0.7.2` |
| Consumer group sin topics en lag | Es normal si aún no hay offsets *committed*; el lag aparece tras el primer evento |
