# Pedidos360 - Arquitectura de Microservicios

Sistema de gestión de pedidos con arquitectura de microservicios. La autenticación y autorización centralizada en un BFF que valida el JWT emitido por el login de Microsoft Entra ID (OAuth2) y enruta las peticiones a los microservicios internos.

```
Microsoft Entra ID (Usuarios + Roles)
   │  OAuth2 (Authorization Code + PKCE)
   ▼
 [ SPA React: FRONTEND ]  ← aplicación de usuario (puerto 4200)
   │  login local JWT + botón "Ingresar con Microsoft"
   ▼
 [ BFF: ms-pedidos360-bff ]  ← exposición de la API (puerto 8080)
   │  Valida JWT + autorización (ADMIN/USER) + CORS
   ├──► ms-pedidos360-users    (usuarios, auth OAuth2, registro/login JWT) — puerto 8083
   ├──► ms-pedidos360-catalog  (categorías y productos)                    — puerto 8081
   └──► ms-pedidos360-orders   (órdenes, reglas de negocio, stock)         — puerto 8082
               │
               └──► ms-pedidos360-catalog (valida precio y descuenta stock)
```

## Componentes

| Componente             | Puerto | Base de datos | Función |
|------------------------|--------|---------------|---------|
| **FRONTEND** (React + Vite) | 4200 | - | SPA: catálogo, carrito, órdenes y panel admin |
| **ms-pedidos360-bff**  | 8080 (público) | - | Validación de JWT, roles, CORS y ruteo hacia los servicios internos |
| **ms-pedidos360-users**    | 8083  | `users_db`    | Usuarios, login OAuth2 con Microsoft Entra ID y emisión de JWT |
| **ms-pedidos360-catalog**  | 8081  | `catalog_db`  | CRUD de categorías y productos, control de stock |
| **ms-pedidos360-orders**   | 8082  | `orders_db`   | Órdenes, estados y descuento de stock contra el catálogo |

**Tecnologías:** Java 21 · Spring Boot · PostgreSQL · JWT · React + Vite · Maven · Docker / Docker Compose

---

## Ejecución con Docker (recomendada)

Levanta PostgreSQL + los 5 servicios (SPA + 4 microservicios) en un solo comando:

```bash
# 1) Copiar el archivo de variables y completar los valores:
cp .env.example .env

# 2) Levantar todo:
docker-compose up --build
```

Una vez arriba:

* Aplicación web: `http://localhost:4200`
* BFF (punto de entrada único de la API): `http://localhost:8080`
* Login con Microsoft (OAuth2): `http://localhost:8080/oauth2/authorization/azure`
* Catálogo (vía BFF): `http://localhost:8080/api/catalog/products`
* Los puertos 8081, 8082 y 8083 se exponen únicamente para depuración; la aplicación debe consumir siempre el 8080.

> Las variables requeridas en `.env`: `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`, `JWT_SECRET`. `SPA_BASE_URL` define hacia dónde redirige el backend tras el login OAuth2 (por defecto `http://localhost:4200`).

### Parar todo
```bash
docker-compose down
```

> Si actualizas desde una versión anterior, elimina el volumen de la base para que se creen las nuevas bases `catalog_db` y `orders_db`: `docker-compose down -v`

---

## Ejecución local (sin Docker)

Cada microservicio necesita PostgreSQL con su base de datos (`users_db`, `catalog_db`, `orders_db`). Requiere un archivo `.env` en cada carpeta usando su `.env-example`:

```bash
cd ms-pedidos360-users && ./mvnw spring-boot:run      # puerto 8083
cd ms-pedidos360-catalog && ./mvnw spring-boot:run    # puerto 8081
cd ms-pedidos360-orders && ./mvnw spring-boot:run     # puerto 8082
cd ms-pedidos360-bff && ./mvnw spring-boot:run        # puerto 8080
cd FRONTEND && npm install && npm run dev             # puerto 4200
```

> En modo local, definí `SPA_BASE_URL=http://localhost:4200` en `ms-pedidos360-users` y `VITE_BFF_URL=http://localhost:8080` en la SPA (el proxy de Vite ya deriva `/api` y `/oauth2` al BFF por defecto).

---

## Frontend (React + Vite)

SPA en `FRONTEND/` que consume la API **solo a través del BFF** (puerto 8080). Usa un proxy de Vite en desarrollo para evitar CORS y guarda el JWT en `localStorage`.

**Pantallas:**

| Ruta | Acceso | Descripción |
|------|--------|-------------|
| `/login` | público | Login/registro local + botón "Ingresar con Microsoft" |
| `/auth/callback` | público | Recibe el `token` que el backend adjunta tras el login OAuth2 |
| `/` | autenticado | Catálogo con filtro por categoría y alta al carrito |
| `/carrito` | autenticado | Cantidades, total y creación de orden |
| `/mis-pedidos` | autenticado | Órdenes del usuario con sus items |
| `/admin/productos` | ADMIN | CRUD de productos y categorías |
| `/admin/ordenes` | ADMIN | Cambio de estado de cualquier orden |
| `/admin/usuarios` | ADMIN | Listado de usuarios |

**Flujo OAuth2:** el botón de Microsoft lleva a `/oauth2/authorization/azure` (proxy → BFF). Microsoft redirige a `AZURE_REDIRECT_URI`, y `ms-pedidos360-users` completa el login y redirige a `${SPA_BASE_URL}/auth/callback?token=...`, donde la SPA guarda el token y navega al catálogo.

---

## Reglas de autorización (BFF)

* **Público (sin JWT):** `/oauth2/**`, `/login/oauth2/**`, `/api/auth/**` (login/registro).
* **Cualquier usuario autenticado:** lectura del catálogo, sus órdenes, su perfil.
* **Solo ADMIN:** escritura en catálogo (`POST/PUT/PATCH/DELETE /api/catalog/**`), cambio de estado de órdenes (`PUT /api/orders/{id}/status`) y listado de usuarios (`GET /api/users`).

Los microservicios internos confían en la red interna; el BFF agrega el encabezado `X-User-Email` para las órdenes.

---

**Variables de Entorno**

```env
JWT_SECRET=<tu_secret_HS256_de_64_chars_identica_en_users_bff_y_template_aws>
JWT_EXPIRATION=86400000
DB_USER=postgres
DB_PASSWORD=postgres
AZURE_CLIENT_ID=
AZURE_CLIENT_SECRET=
AZURE_TENANT_ID=
AZURE_REDIRECT_URI=http://localhost:8080/login/oauth2/code/azure
```

> _En funcionamiento: **Login con Microsoft Entra ID (OAuth2 + PKCE)**, **emisión/validación de JWT con roles**, **arquitectura BFF + 3 microservicios con bases de datos independientes**._