# Pedidos360: comandos locales de PowerShell

Ejecuta estos comandos desde PowerShell. La ruta base del proyecto es:

```powershell
cd C:\Users\nicol\Desktop\project-cloud-native-ev1-dev\project-cloud-native-ev1-dev
```

## 1. Iniciar y detener el proyecto

Construir e iniciar todos los servicios:

```powershell
docker compose up -d --build
```

Ver el estado de todos los contenedores:

```powershell
docker compose ps
```

Ver logs recientes de todos los servicios:

```powershell
docker compose logs --tail 100
```

Detener los servicios sin borrar datos:

```powershell
docker compose down
```

URLs locales:

| Servicio | URL |
| --- | --- |
| Aplicación | http://localhost:4200 |
| BFF | http://localhost:8080 |
| RabbitMQ Management, nodo 1 | http://localhost:15672 |
| RabbitMQ Management, nodo 2 | http://localhost:15673 |

Las credenciales de RabbitMQ son las configuradas en `.env`. En esta instalación: `pedidos360` / `pedidos360`.

## 2. Comprobar RabbitMQ y el cluster

Comprobar que ambos nodos responden:

```powershell
docker exec rabbitmq-node1 rabbitmq-diagnostics -q ping
docker exec rabbitmq-node2 rabbitmq-diagnostics -q ping
```

Ver el estado del cluster:

```powershell
docker exec rabbitmq-node1 rabbitmqctl cluster_status
```

El resultado debe incluir `rabbit@rabbitmq-node1` y `rabbit@rabbitmq-node2`.

Ver las colas, consumers y mensajes pendientes:

```powershell
docker exec rabbitmq-node1 rabbitmqctl list_queues -p / name type consumers messages_ready messages_unacknowledged
```

Ver los consumers conectados:

```powershell
docker exec rabbitmq-node1 rabbitmqctl list_consumers -p /
```

Las colas de la aplicación son:

- `notificaciones.pedidos`: recibe `pedido.creado`.
- `auditoria.pedidos`: recibe `pedido.creado`.
- `tickets.pedidos`: recibe `pedido.estado.actualizado`.

Ver los logs de los servicios consumidores:

```powershell
docker compose logs --since 5m ms-pedidos360-users ms-pedidos360-orders
```

## 3. Crear un pedido de prueba

Este comando crea un pedido directo al microservicio de órdenes y guarda su identificador en `$OID`. El producto `9` debe existir y tener stock de al menos 2 unidades.

```powershell
$body = '{"items":[{"productId":9,"quantity":2}]}'

$resp = Invoke-RestMethod `
  -Uri "http://localhost:8082/api/orders" `
  -Method POST `
  -Headers @{ "X-User-Email" = "ni.maureira@duocuc.cl" } `
  -ContentType "application/json" `
  -Body $body

$resp | ConvertTo-Json -Depth 5
$OID = $resp.id
"Pedido creado: #$OID"
```

Crear diez pedidos y conservarlos en `$orders`:

```powershell
$orders = @()

for ($i = 1; $i -le 10; $i++) {
  $order = Invoke-RestMethod `
    -Uri "http://localhost:8082/api/orders" `
    -Method POST `
    -Headers @{ "X-User-Email" = "ni.maureira@duocuc.cl" } `
    -ContentType "application/json" `
    -Body '{"items":[{"productId":9,"quantity":1}]}'

  $orders += $order
}

$orders | Select-Object id, userEmail, status, total, createdAt
```

Cada pedido creado publica el evento `pedido.creado` en RabbitMQ.

## 4. Cambiar estados y probar el consumer de tickets

Los estados válidos son: `CREADO`, `ACEPTADO`, `EN_PREPARACION`, `DESPACHADO`, `ENTREGADO` y `CANCELADO`.

Cambiar el pedido guardado en `$OID` a aceptado:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8082/api/orders/$OID/status" `
  -Method PUT `
  -ContentType "application/json" `
  -Body '{"status":"ACEPTADO"}' |
  ConvertTo-Json -Depth 5
```

Cambiarlo a preparación:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8082/api/orders/$OID/status" `
  -Method PUT `
  -ContentType "application/json" `
  -Body '{"status":"EN_PREPARACION"}' |
  ConvertTo-Json -Depth 5
```

Cambiar los diez pedidos creados a aceptados:

```powershell
foreach ($order in $orders) {
  Invoke-RestMethod `
    -Uri "http://localhost:8082/api/orders/$($order.id)/status" `
    -Method PUT `
    -ContentType "application/json" `
    -Body '{"status":"ACEPTADO"}' | Out-Null
}
```

Cambiar de estado publica `pedido.estado.actualizado` y el consumer de `tickets.pedidos` genera un comprobante. Compruébalo en los logs:

```powershell
docker compose logs --since 5m ms-pedidos360-orders |
  Select-String -Pattern "Ticket generado|pedido.estado.actualizado|DLQ"
```

## 5. Consultar pedidos

Consultar el pedido guardado en `$OID`:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8082/api/orders/$OID" `
  -Headers @{ "X-User-Email" = "ni.maureira@duocuc.cl" } |
  ConvertTo-Json -Depth 5
```

Listar todos los pedidos y ver el JSON completo:

```powershell
$allOrders = Invoke-RestMethod -Uri "http://localhost:8082/api/orders/all"
$allOrders | ConvertTo-Json -Depth 8
```

Listar solo los campos principales:

```powershell
$allOrders | Select-Object id, userEmail, status, total, createdAt
```

Listar los pedidos del correo de prueba:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8082/api/orders" `
  -Headers @{ "X-User-Email" = "ni.maureira@duocuc.cl" } |
  Select-Object id, userEmail, status, total, createdAt
```

## 6. Publicar un mensaje de RabbitMQ y dejarlo en una cola de inspección

Configura las credenciales para la API de administración:

```powershell
$rmqUser = "pedidos360"
$rmqPassword = "pedidos360"
$basic = [Convert]::ToBase64String(
  [Text.Encoding]::ASCII.GetBytes("$rmqUser`:$rmqPassword")
)
$rmqHeaders = @{ Authorization = "Basic $basic" }

$queue = "demo.inspeccion"
$queueUrl = [Uri]::EscapeDataString($queue)
```

Crear la cola de inspección:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:15672/api/queues/%2F/$queueUrl" `
  -Method Put `
  -Headers $rmqHeaders `
  -ContentType "application/json" `
  -Body '{"durable":true,"auto_delete":false,"arguments":{}}'
```

Enlazarla al exchange real `pedidos.direct`:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:15672/api/bindings/%2F/e/pedidos.direct/q/$queueUrl" `
  -Method Post `
  -Headers $rmqHeaders `
  -ContentType "application/json" `
  -Body '{"routing_key":"demo.inspeccion","arguments":{}}'
```

Publicar un mensaje manual:

```powershell
$publishBody = @{
  properties = @{ content_type = "application/json" }
  routing_key = "demo.inspeccion"
  payload = '{"origen":"PowerShell","mensaje":"publicado a mano"}'
  payload_encoding = "string"
} | ConvertTo-Json -Depth 5 -Compress

Invoke-RestMethod `
  -Uri "http://localhost:15672/api/exchanges/%2F/pedidos.direct/publish" `
  -Method Post `
  -Headers $rmqHeaders `
  -ContentType "application/json" `
  -Body $publishBody
```

La respuesta debe contener `routed: true`. Confirmar que el mensaje quedó pendiente en la cola:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:15672/api/queues/%2F/$queueUrl" `
  -Headers $rmqHeaders |
  Select-Object name, messages, messages_ready, consumers
```

También puedes abrir `demo.inspeccion` desde http://localhost:15672, en **Queues and Streams**.

## 7. Probar la DLQ de tickets

Publica un JSON inválido en el exchange y routing key reales de cambios de estado. El consumer de tickets lo rechazará y RabbitMQ lo dirigirá a `tickets.pedidos.dlq`.

```powershell
$badPayload = @{
  properties = @{ content_type = "application/json" }
  routing_key = "pedido.estado.actualizado"
  payload = "{"
  payload_encoding = "string"
} | ConvertTo-Json -Depth 5 -Compress

Invoke-RestMethod `
  -Uri "http://localhost:15672/api/exchanges/%2F/pedidos.topic/publish" `
  -Method Post `
  -Headers $rmqHeaders `
  -ContentType "application/json" `
  -Body $badPayload
```

Revisar las colas y los logs posteriores:

```powershell
docker exec rabbitmq-node1 rabbitmqctl list_queues -p / name consumers messages_ready messages_unacknowledged

docker compose logs --since 2m ms-pedidos360-orders |
  Select-String -Pattern "DLQ|Payload invalido|rechazado"
```

Con `MESSAGING_DLQ_MONITOR_ENABLED=true`, el listener de DLQ registra el rechazo y confirma el mensaje; por eso puede no permanecer pendiente en la DLQ.
