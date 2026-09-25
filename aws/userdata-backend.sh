#!/bin/bash
# user-data Pedidos360 EP1 - EC2 (plan B: API Gateway -> ALB -> EC2)
# FIXME: en produccion, obtener JWT_SECRET y AZURE_CLIENT_SECRET de Secrets Manager/SSM,
# no versionarlos. Para el lab se dejan inline para reproducibilidad.
exec > /var/log/user-data.log 2>&1
set -e

# ---------- Docker ----------
dnf install -y -q docker
systemctl enable --now docker
usermod -aG docker ec2-user

ACCOUNT=324523428924
REGION=us-east-1
REGISTRY=$ACCOUNT.dkr.ecr.$REGION.amazonaws.com

# ---------- Login ECR (via IMDS instance profile LabRole) ----------
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REGISTRY

# ---------- Red interna ----------
docker network create pedidos360 || true

# ---------- PostgreSQL 16 con init.sql ----------
mkdir -p /opt/pedidos360
cat > /opt/pedidos360/init.sql <<'SQL'
CREATE DATABASE catalog_db;
CREATE DATABASE orders_db;
SQL

docker run -d --name pedidos360-db \
  --network pedidos360 --network-alias db \
  -e POSTGRES_DB=users_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -v /opt/pedidos360/init.sql:/docker-entrypoint-initdb.d/init.sql:ro \
  -v pgdata:/var/lib/postgresql/data \
  postgres:16-alpine

# esperar DB lista
for i in $(seq 1 60); do
  if docker exec pedidos360-db pg_isready -U postgres -d users_db >/dev/null 2>&1; then
    echo "DB lista tras $i intentos"
    break
  fi
  sleep 2
done

# ---------- ms-pedidos360-users ----------
docker run -d --name ms-pedidos360-users \
  --network pedidos360 --network-alias ms-pedidos360-users \
  -e DB_HOST=db -e DB_PORT=5432 -e DB_NAME=users_db \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e JWT_SECRET="CHANGE_ME_secret_compartida_HS256_64_chars" \
  -e JWT_EXPIRATION=86400000 \
  -e AZURE_CLIENT_ID="755beba9-5846-4299-96ba-e01735ee7742" \
  -e AZURE_CLIENT_SECRET="CHANGE_ME_client_secret_de_entra_id" \
  -e AZURE_TENANT_ID="61171dfb-185b-4480-9577-15603444f1ed" \
  -e AZURE_REDIRECT_URI="https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod/login/oauth2/code/azure" \
  -e GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-}" \
  -e GOOGLE_CLIENT_SECRET="${GOOGLE_CLIENT_SECRET:-}" \
  -e GOOGLE_REDIRECT_URI="https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod/login/oauth2/code/google" \
  -e SPA_BASE_URL="http://pedidos360-alb-571995876.us-east-1.elb.amazonaws.com" \
  -p 8083:8083 \
  $REGISTRY/pedidos360-users:latest

# ---------- ms-pedidos360-catalog ----------
docker run -d --name ms-pedidos360-catalog \
  --network pedidos360 --network-alias ms-pedidos360-catalog \
  -e DB_HOST=db -e DB_PORT=5432 -e DB_NAME=catalog_db \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -p 8081:8081 \
  $REGISTRY/pedidos360-catalog:latest

# ---------- ms-pedidos360-orders ----------
docker run -d --name ms-pedidos360-orders \
  --network pedidos360 --network-alias ms-pedidos360-orders \
  -e DB_HOST=db -e DB_PORT=5432 -e DB_NAME=orders_db \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e CATALOG_URL="http://ms-pedidos360-catalog:8081" \
  -p 8082:8082 \
  $REGISTRY/pedidos360-orders:latest

# ---------- ms-pedidos360-bff (proxy publico, target del ALB) ----------
docker run -d --name ms-pedidos360-bff \
  --network pedidos360 --network-alias ms-pedidos360-bff \
  -e USERS_URL="http://ms-pedidos360-users:8083" \
  -e CATALOG_URL="http://ms-pedidos360-catalog:8081" \
  -e ORDERS_URL="http://ms-pedidos360-orders:8082" \
  -e BFF_PUBLIC_BASE="https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod" \
  -e JWT_SECRET="CHANGE_ME_secret_compartida_HS256_64_chars" \
  -e JWT_CLOCK_SKEW_SECONDS=60 \
  -p 8080:8080 \
  $REGISTRY/pedidos360-bff:latest

# Esperar a que el BFF levante antes de sembrar el catálogo
for i in $(seq 1 60); do
  if curl -sf http://localhost:8080/api/catalog/products >/dev/null 2>&1; then
    echo "BFF listo tras $i intentos"
    break
  fi
  sleep 2
done

# ---------- Seed del catálogo (reproducible: misma data que local) ----------
docker exec pedidos360-db psql -U postgres -d catalog_db -v ON_ERROR_STOP=1 -c "
INSERT INTO categories (name) VALUES ('Bebidas'),('Abarrotes'),('Snacks'),('Limpieza'),('Tecnología') ON CONFLICT (name) DO NOTHING;
WITH cat AS (SELECT id, name FROM categories)
INSERT INTO products (name, description, price, stock, category_id)
SELECT v.name, v.description, v.price, v.stock, c.id
FROM (VALUES
  ('Coca-Cola 1.5L', 'Bebida gaseosa sabor cola, botella retornable 1.5 litros', 2500, 48),
  ('Agua mineral 600ml', 'Agua purificada sin gas, botella 600 ml', 1000, 100),
  ('Jugo natural 1L', 'Jugo de naranja 100% natural, envase 1 litro', 1800, 35),
  ('Arroz grado 1 1kg', 'Arroz de grano largo, bolsa 1 kg', 1200, 80),
  ('Fideos tallarines 500g', 'Pasta de sémola de trigo, paquete 500 g', 900, 90),
  ('Aceite vegetal 1L', 'Aceite mezcla para cocina, botella 1 litro', 3000, 60),
  ('Harina sin polvos 1kg', 'Harina de trigo para repostería, bolsa 1 kg', 1100, 45),
  ('Azúcar blanca 1kg', 'Azúcar refinada, bolsa 1 kg', 1300, 70),
  ('Papas fritas 160g', 'Snack de papas fritas sabor original, bolsa 160 g', 1500, 55),
  ('Galletas de chocolate 200g', 'Galletas rellenas sabor chocolate, paquete 200 g', 900, 65),
  ('Chocolate barra 100g', 'Chocolate con leche, barra 100 g', 2000, 40),
  ('Maní salado 150g', 'Maní tostado con sal, bolsa 150 g', 1200, 50),
  ('Detergente líquido 3L', 'Detergente para ropa concentrado, botella 3 litros', 3500, 25),
  ('Cloro gel 1L', 'Lavanda/perfumado, botella 1 litro', 1800, 30),
  ('Jabón líquido manos 250ml', 'Jabón antibacterial para manos, dispensador 250 ml', 2200, 38),
  ('Audífonos Bluetooth', 'Audífonos inalámbricos con estuche de carga', 15000, 15),
  ('Mouse inalámbrico', 'Mouse óptico 2.4GHz, compatible USB', 12000, 20),
  ('Pendrive USB 64GB', 'Memoria USB 3.0, 64 GB', 8000, 28)
) AS v(name, description, price, stock)
JOIN cat c ON c.name = CASE
  WHEN v.name IN ('Coca-Cola 1.5L','Agua mineral 600ml','Jugo natural 1L') THEN 'Bebidas'
  WHEN v.name IN ('Arroz grado 1 1kg','Fideos tallarines 500g','Aceite vegetal 1L','Harina sin polvos 1kg','Azúcar blanca 1kg') THEN 'Abarrotes'
  WHEN v.name IN ('Papas fritas 160g','Galletas de chocolate 200g','Chocolate barra 100g','Maní salado 150g') THEN 'Snacks'
  WHEN v.name IN ('Detergente líquido 3L','Cloro gel 1L','Jabón líquido manos 250ml') THEN 'Limpieza'
  ELSE 'Tecnología'
END;" || echo "seed catálogo: ya aplicado o DB aun no lista"

# ---------- Frontend (SPA + nginx, detras del ALB, puerto 4200) ----------
# Imagen de produccion: FRONTEND/Dockerfile.prod (vite build + nginx).
# El ALB enruta / (SPA) y /api/*, /oauth2/*, /login/* (BFF) por reglas de path.
docker run -d --name pedidos360-frontend \
  --restart unless-stopped \
  -p 4200:80 \
  $REGISTRY/pedidos360-frontend:latest

echo "=== user-data completo ==="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"