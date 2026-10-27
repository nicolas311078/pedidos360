# EP3 — Mensajería asíncrona con RabbitMQ

Diseño e implementación de la parte asíncrona de Pedidos360: **RabbitMQ 3.13 en un cluster de 2 nodos**, tres colas quorum con su DLQ, dos tipos de exchange (direct y topic), consumidores con ACK/NACK manual y un microservicio administrador con API REST.

---

## 1. Por qué mensajería asíncrona

El flujo síncrono del backend es "petición → respuesta". Cuando se crea un pedido hay tres trabajos que **no son parte de la respuesta** del cliente:

| Trabajo | Por qué asíncrono |
|---|---|
| Notificar al cliente | Es una notificación, no un dato que el cliente espere |
| Generar el comprobante (ticket) | Es un documento; si falla, el pedido ya está creado y no debe revertirse |
| Registrar la auditoría | Traza; no debe bloquear la creación del pedido |

Con RabbitMQ el pedido se guarda y la respuesta vuelve al instante; el trabajo pesado se procesa después, en segundo plano, con reintentos y con un plan B (la DLQ) cuando el mensaje no se puede procesar.

---

## 2. Topología

```
                    ┌──────────────────────┐
  ms-pedidos360-    │   exchange direct    │   routing key exacta
  orders            │   pedidos.direct     │
  (productor)  ───► │                      │ ──► notificaciones.pedidos ──► ms-pedidos360-users
                    └──────────────────────┘           │  (DLQ: notificaciones.pedidos.dlq)
                    ┌──────────────────────┐
                    │   exchange topic     │   routing key por patrón
                    │   pedidos.topic      │
  ms-pedidos360-    │                      │ ──► auditoria.pedidos ──────► ms-pedidos360-users
  orders            │                      │           │  (DLQ: auditoria.pedidos.dlq)
  (productor)  ───► │                      │ ──► tickets.pedidos ────────► ms-pedidos360-orders
                    └──────────────────────┘           │  (DLQ: tickets.pedidos.dlq)
                    ┌──────────────────────┐
                    │ dead letter exchange │   pedidos.dlx
                    └──────────────────────┘ ──► *.dlq
```

| Exchange | Tipo | Routing key | Cola | Consumidor | DLQ |
|---|---|---|---|---|---|
| `pedidos.direct` | direct | `pedido.creado` | `notificaciones.pedidos` | ms-pedidos360-users | `notificaciones.pedidos.dlq` |
| `pedidos.topic` | topic | `pedido.creado` | `auditoria.pedidos` | ms-pedidos360-users | `auditoria.pedidos.dlq` |
| `pedidos.topic` | topic | `pedido.estado.actualizado` | `tickets.pedidos` | ms-pedidos360-orders | `tickets.pedidos.dlq` |
| `pedidos.dlx` | direct | nombre de la DLQ | `*.dlq` | log del servicio | — |

* Las colas se declaran como **quorum** (`x-queue-type=quorum`): los mensajes se replican en los dos nodos del cluster y sobreviven a la caída de un nodo.
* El evento `pedido.creado` se publica en **los dos exchanges**: el direct lo enruta a notificaciones y el topic a auditoría. El mismo evento, dos consumidores distintos (fan-out).

### Nombres centralizados

Todos los nombres viven en `application.yaml` de cada servicio (prefijo `messaging`), nunca en código:

```yaml
messaging:
  exchanges:
    direct: pedidos.direct
    topic: pedidos.topic
    deadLetter: pedidos.dlx
  queueType: quorum
  queues:
    notifications:
      name: notificaciones.pedidos
      exchange: direct
      routingKey: pedido.creado
      deadLetterQueue: notificaciones.pedidos.dlq
```

El POJO `MessagingProperties` + `MessagingTopologyConfig` los transforman en beans `Queue`, `Exchange`, `Binding` y `Declarables`. Al abrir la conexión, `RabbitAdmin` los declara en el broker: la topología es **declarativa e idempotente**.

---

## 3. Cluster de 2 nodos

`docker-compose.yml` levanta `rabbitmq-node1` y `rabbitmq-node2` (imagen `rabbitmq:3.13-management-alpine`):

* Ambos comparten la **cookie de Erlang** (`RABBITMQ_ERLANG_COOKIE`): es lo que permite que se reconozcan.
* `rabbitmq-node2` espera a que `node1` esté sano y se une con `rabbitmqctl join_cluster rabbit@rabbitmq-node1`.
* El plugin **management** está activo: consola web en `http://localhost:15672` (node1) y `http://localhost:15673` (node2).

Verificación del cluster:

```bash
docker exec rabbitmq-node1 rabbitmqctl cluster_status
# o desde la consola web: la pestaña "Nodes" muestra los 2 nodos
```

---

## 4. Productores y consumidores

### Publicación (ms-pedidos360-orders)

`OrderService` publica un **evento de dominio** dentro de la transacción; `OrderEventBridge` lo traduce a RabbitMQ **después del commit** (`@TransactionalEventListener(phase = AFTER_COMMIT)`). Si la transacción falla, no sale ningún mensaje.

`OrderEventPublisher` arma el JSON a mano (sin `MessageConverter`) para que el payload no dependa del classpath del consumidor: cada servicio declara su propia versión del evento.

### Consumo (ACK/NACK manual)

Todos los consumidores usan `ackMode = "MANUAL"` y `MessageRetrySupport`:

| Situación | Acción |
|---|---|
| Procesado correctamente | `basicAck` |
| Falla transitoria | Se republica el mensaje con `x-retry-count + 1` y se hace `basicAck` del original (hasta 3 reintentos) |
| Payload inválido | `basicNack(requeue=false)` → el broker lo mueve a la **DLQ** |
| Reintentos agotados | `basicNack(requeue=false)` → **DLQ** |

Cada DLQ tiene su propio listener (`DeadLetterListener`), que **registra en logs** el mensaje rechazado junto con los headers `x-death` que agrega RabbitMQ (exchange y cola de origen, motivo del rechazo) y después lo libera con `ackMode = AUTO`.

Ese monitor se puede pausar con `MESSAGING_DLQ_MONITOR_ENABLED=false`: los mensajes quedan retenidos en la DLQ (sin consumidores) y se pueden inspeccionar en la consola de RabbitMQ. Es el modo que se usa en la demo para mostrar el mensaje fallido dentro de la DLQ.

| Consumidor | Lógica de negocio | Efecto observable |
|---|---|---|
| `NotificationConsumer` (users) | Genera la notificación del pedido | Fila en `notifications` + log |
| `AuditConsumer` (users) | Registra la traza del evento | Fila en `audit_entries` + log |
| `TicketConsumer` (orders) | Genera el comprobante del pedido | Fila en `order_tickets` + log |

---

## 5. Microservicio administrador de RabbitMQ

`ms-pedidos360-rabbit-admin` (puerto 8084) expone la API de administración:

* **Mutaciones** (crear/eliminar) por la API declarativa de AMQP (`AmqpAdmin`): `queue.declare`, `exchange.declare`, `queue.bind`, y sus operaciones de borrado.
* **Lectura** por la API de administración HTTP de RabbitMQ: contadores de mensajes, consumidores, bindings y **nodos del cluster**.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/messaging/queues` | Colas con mensajes pendientes, sin ACK y consumidores |
| POST | `/api/messaging/queues` | Crea cola (`name`, `durable`, `quorum`, DLQ opcional) |
| DELETE | `/api/messaging/queues/{name}` | Elimina cola |
| GET | `/api/messaging/exchanges` | Exchanges |
| POST | `/api/messaging/exchanges` | Crea exchange (`name`, `type`) |
| DELETE | `/api/messaging/exchanges/{name}` | Elimina exchange |
| GET | `/api/messaging/bindings` | Bindings exchange → cola |
| POST | `/api/messaging/bindings` | Crea binding (`exchange`, `queue`, `routingKey`) |
| DELETE | `/api/messaging/bindings` | Elimina binding |
| GET | `/api/messaging/nodes` | Nodos del cluster |

Reglas de negocio del servicio:

* **Validación de entrada**: el nombre debe cumplir `^[a-zA-Z0-9._-]{1,128}$`; el tipo de exchange solo puede ser `direct`, `topic`, `fanout` u `headers`. Si no, responde `400` con el detalle.
* **Colas y exchanges protegidos**: los de la propia aplicación (`notificaciones.pedidos`, `pedidos.topic`, etc.) se pueden listar pero no eliminar → `409`.

El BFF expone esta API en `/api/messaging/**` **solo para el rol ADMIN**, y el frontend la consume en `/admin/mensajeria`.

---

## 6. Guion de demostración

1. `docker compose up -d` → mostrar `docker ps` con los 2 nodos de RabbitMQ y los servicios.
2. Consola de RabbitMQ → pestaña **Nodes**: 2 nodos en el mismo cluster. Pestaña **Queues**: las 3 colas quorum + sus 3 DLQ.
3. Crear un pedido en la app (como CLIENTE) → en la consola se ven los mensajes en `notificaciones.pedidos` y `auditoria.pedidos`, y en los logs del servicio de usuarios las notificaciones y auditorías generadas.
4. Cambiar el estado del pedido (como OPERADOR) → el mensaje `pedido.estado.actualizado` va a `tickets.pedidos` y se genera el comprobante (`order_tickets`).
5. **DLQ**: publicar un mensaje con payload inválido en `pedidos.topic` con routing key `pedido.estado.actualizado` → el consumidor lo rechaza con NACK y el mensaje queda en `tickets.pedidos.dlq`. Con el monitor activo se ve el log `[DLQ ...]`; con `MESSAGING_DLQ_MONITOR_ENABLED=false` el mensaje queda retenido y se puede abrir desde la consola.
6. `/admin/mensajeria` (como ADMIN): ver colas, exchanges, bindings y los nodos del cluster; crear una cola y un binding, y eliminarlos.

---

## 7. Posible equivalente en GCP

La parte asíncrona se puede portar a **Google Cloud Pub/Sub** sin tocar la lógica de negocio, porque los nombres, la topología y los consumidores ya están detrás de los beans de Spring AMQP:

| RabbitMQ | Pub/Sub |
|---|---|
| Exchange | Topic |
| Queue | Subscription |
| Routing key | `attributes.routingKey` / filtro de suscripción |
| DLQ (`x-dead-letter-exchange`) | Dead-letter topic + subscription de error |
| `basicAck` | `ack()` |
| `basicNack(requeue=false)` | `nack()` |
| Cluster de 2 nodos | Servicio gestionado (sin cluster propio que administrar) |

Cambiar de broker es, en la práctica, cambiar la dependencia (`spring-boot-starter-amqp` → `spring-cloud-gcp-starter-pubsub`), la configuración de los beans de topología y el `MessageConverter`. El resto del código (productores, consumidores, lógica de negocio, DLQ, logs) se mantiene.

---

## 8. Verificación realizada

Todas las comprobaciones se ejecutaron sobre el stack local (`docker compose up -d`, 9 contenedores) con la topología declarada por los propios servicios.

| Comprobación | Resultado |
|---|---|
| Cluster RabbitMQ | `rabbitmqctl cluster_status` muestra `rabbit@rabbitmq-node1` y `rabbit@rabbitmq-node2` en ejecución (RabbitMQ 3.13.7, Erlang 26.2.5.16) |
| Topología | 6 colas `quorum` (3 de negocio + 3 DLQ) y exchanges `pedidos.direct`, `pedidos.topic`, `pedidos.dlx` con sus bindings |
| Pedido creado | Se publica `pedido.creado` en `pedidos.direct` y `pedidos.topic`; el servicio de usuarios registra la notificación y la auditoría (filas en `notifications` y `audit_entries`) |
| Cambio de estado | Se publica `pedido.estado.actualizado`; el consumidor de tickets genera el comprobante (fila en `order_tickets`) |
| Reintentos | Una falla de base de datos se reintenta 3 veces (`x-retry-count` 1..3) y luego pasa a la DLQ |
| DLQ | Un payload inválido produce NACK y el mensaje llega a `tickets.pedidos.dlq`; el monitor lo registra en logs con los headers `x-death` |
| Inspección de DLQ | Con `MESSAGING_DLQ_MONITOR_ENABLED=false` la cola queda con `messages=1` y `consumers=0` |
| API del administrador | `201` al crear cola/exchange/binding, `204` al eliminarlos, `409` en recursos protegidos, `400` en nombres inválidos |
| Seguridad de la ruta | `/api/messaging/**` por el BFF: `200` con ADMIN, `403` con CLIENTE, `401` sin token |
| Frontend | `npm run build` (TypeScript + Vite) sin errores y ruta `/admin/mensajeria` servida por el contenedor |
