# Despliegue en AWS - Pedidos360 con RabbitMQ Cluster

## Instancia EC2

- **AMI**: Amazon Linux 2023 (con Docker)
- **Instance ID**: `i-035eb3939d3abbf02`
- **IP pública**: `13.220.149.198`
- **Región**: `us-east-1`
- **Security Group**: `sg-03f1b9c95f43373ff` (`pedidos360-app-sg`)

### Puertos abiertos en Security Group

| Puerto | Uso |
|---|---|
| 22 | SSH |
| 4200 | Frontend (Angular/nginx) |
| 8080 | BFF |
| 8081 | Catalog |
| 8082 | Orders |
| 8083 | Users |
| 5672 | AMQP (RabbitMQ node1) |
| 5673 | AMQP (RabbitMQ node2) |
| 15672 | RabbitMQ Management UI (node1) |
| 15673 | RabbitMQ Management UI (node2) |

## RabbitMQ Cluster (2 nodos)

Se desplegaron dos contenedores RabbitMQ en la misma red Docker `pedidos360`:

### Node 1
```bash
docker run -d --name rabbitmq-node1 \
  --network pedidos360 --network-alias rabbitmq-node1 --hostname rabbitmq-node1 \
  -e RABBITMQ_NODENAME=rabbit@rabbitmq-node1 \
  -e RABBITMQ_ERLANG_COOKIE=pedidos360-cluster-cookie \
  -e RABBITMQ_DEFAULT_USER=pedidos360 \
  -e RABBITMQ_DEFAULT_PASS=pedidos360 \
  -p 5672:5672 -p 15672:15672 \
  -v rabbitmq-node1-data:/var/lib/rabbitmq \
  -v /opt/pedidos360/rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro \
  rabbitmq:3.13-management-alpine
```

### Node 2 (se une al cluster automáticamente)
```bash
docker run -d --name rabbitmq-node2 \
  --network pedidos360 --network-alias rabbitmq-node2 --hostname rabbitmq-node2 \
  -e RABBITMQ_NODENAME=rabbit@rabbitmq-node2 \
  -e RABBITMQ_ERLANG_COOKIE=pedidos360-cluster-cookie \
  -e RABBITMQ_DEFAULT_USER=pedidos360 \
  -e RABBITMQ_DEFAULT_PASS=pedidos360 \
  -p 5673:5672 -p 15673:15672 \
  -v rabbitmq-node2-data:/var/lib/rabbitmq \
  -v /opt/pedidos360/rabbitmq/rabbitmq.conf:/etc/rabbitmq/rabbitmq.conf:ro \
  rabbitmq:3.13-management-alpine
```

### Configuración de cluster (`/opt/pedidos360/rabbitmq/rabbitmq.conf`)
```
cluster_formation.peer_discovery_backend = classic_config
cluster_formation.classic_config.nodes.1 = rabbit@rabbitmq-node1
cluster_formation.node_cleanup.interval = 10
cluster_partition_handling = autoheal
```

### Credenciales
- **Usuario**: `pedidos360`
- **Contraseña**: `pedidos360`

## Topología RabbitMQ (6 colas + 3 exchanges)

### Exchanges
| Exchange | Tipo | Durable |
|---|---|---|
| `pedidos.direct` | direct | sí |
| `pedidos.topic` | topic | sí |
| `pedidos.dlx` | direct (dead letter) | sí |

### Colas (3 principales + 3 DLQ)
| Cola | Exchange | Routing Key | DLQ |
|---|---|---|---|
| `notificaciones.pedidos` | pedidos.direct | `pedido.creado` | `notificaciones.pedidos.dlq` |
| `auditoria.pedidos` | pedidos.topic | `pedido.creado` | `auditoria.pedidos.dlq` |
| `tickets.pedidos` | pedidos.topic | `pedido.estado.actualizado` | `tickets.pedidos.dlq` |
| `notificaciones.pedidos.dlq` | pedidos.dlx | `notificaciones.pedidos.dlq` | - |
| `auditoria.pedidos.dlq` | pedidos.dlx | `auditoria.pedidos.dlq` | - |
| `tickets.pedidos.dlq` | pedidos.dlx | `tickets.pedidos.dlq` | - |

## Microservicios conectados

Variables de entorno en `ms-pedidos360-orders` y `ms-pedidos360-users`:
```
SPRING_RABBITMQ_HOST=rabbitmq-node1
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=pedidos360
SPRING_RABBITMQ_PASSWORD=pedidos360
RABBITMQ_HOST=rabbitmq-node1
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=pedidos360
RABBITMQ_PASSWORD=pedidos360
MESSAGING_DLQ_MONITOR_ENABLED=true
```

## URLs de acceso

| Servicio | URL |
|---|---|
| Frontend | http://13.220.149.198:4200 |
| BFF | http://13.220.149.198:8080 |
| Catalog | http://13.220.149.198:8081 |
| Orders | http://13.220.149.198:8082 |
| Users | http://13.220.149.198:8083 |
| RabbitMQ Management (node1) | http://13.220.149.198:15672 |
| RabbitMQ Management (node2) | http://13.220.149.198:15673 |
| ALB | http://pedidos360-alb-571995876.us-east-1.elb.amazonaws.com |

## Notas

- El microservicio `ms-pedidos360-users` requiere credenciales reales de Azure para OAuth2. En AWS se configuró con valores dummy para que arranque y declare la topología de RabbitMQ.
- Las colas se crearon manualmente con `rabbitmqadmin` porque los microservicios no pudieron declararlas automáticamente (el bean `MessagingTopologyConfig` no se ejecutó por el fallo de OAuth2 en `users`).
- El `rabbitmq.conf` en la instancia se generó manualmente con `echo` porque SSM no aceptaba heredocs con comillas simples.