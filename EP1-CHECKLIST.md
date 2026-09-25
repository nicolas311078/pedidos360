# Checklist EP1 — Caso 0 Pedidos360 (Microsoft Entra ID)

Estado por ítem de la guía, después de los cambios aplicados.

## Roles y autorización (cambios aplicados)

| Ítem | Estado | Detalle |
|---|---|---|
| Roles ADMIN / OPERADOR / CLIENTE | ✅ Aplicado | `Role` enum en `ms-pedidos360-users`; mapeo desde App Roles de Entra ID |
| App Roles en Entra ID | ⚠️ Falta en Azure | Crear app roles + asignar usuarios (pasos más abajo) |
| Login Microsoft (sin registro) | ✅ Aplicado | `LoginPage` solo tiene "Ingresar con Microsoft"; se eliminó el acceso al registro local |
| Rutas protegidas por rol | ✅ Aplicado | `ProtectedRoute`, `OperationRoute` (Admin/Operador), `AdminRoute` (solo Admin) |
| Diferenciación de pantallas por actor | ✅ Aplicado | Cliente: solo sus pedidos; Operador/Admin: "Mis pedidos" muestra todos + "Operación · Órdenes"; Admin: además usuarios y catálogo |
| Cambio de estado limitado | ✅ Aplicado | BFF solo permite `PUT /api/orders/{id}/status` a OPERADOR/ADMIN; catálogo (escritura) y usuarios solo ADMIN |

## Reglas de negocio (guía Caso 0)

| Regla | Estado |
|---|---|
| Flujo de estados CREADO → ACEPTADO → EN_PREPARACIÓN → DESPACHADO → ENTREGADO / CANCELADO | ✅ Aplicado (enum + transiciones validadas) |
| No se puede DESPACHAR sin ACEPTAR | ✅ Aplicado (`isAllowedTransition`) |
| El stock disminuye al ACEPTAR el pedido | ✅ Aplicado (se movió el `decrementStock` a la transición a ACEPTADO) |

## Validación JWT

| Ítem | Estado | Detalle |
|---|---|---|
| Firma + vigencia | ✅ Ya existía | jjwt en BFF |
| Issuer + audience | ✅ Aplicado | `JwtService` del BFF valida `expected-issuer` / `expected-audience`; `users` firma con `iss=pedidos360-users`, `aud=pedidos360-bff` |

## AWS API Gateway

| Ítem | Estado | Detalle |
|---|---|---|
| Template CloudFormation | ✅ Creado | `aws/api-gateway.yaml` |
| JWT Authorizer (Lambda) | ✅ Creado | Valida HS256 + expiración, reenvía el token al BFF |
| CORS + OPTIONS | ✅ Creado | `CorsConfiguration` en la API |
| Despliegue real en AWS | ⚠️ Pendiente | Requiere cuenta AWS + BFF accesible públicamente (`aws/README.md`) |

## Pasos que tenés que hacer vos (Azure)

### 1. Crear los App Roles en la App Registration

La app en uso es la registration **`755beba9-5846-4299-96ba-e01735ee7742`** (tenant
`61171dfb-185b-4480-9577-15603444f1ed` / `NicolazAura.onmicrosoft.com`; en el portal
aparece con su display name, ej. `prueba1` o `Pedidos360`).

En `portal.azure.com` → **Microsoft Entra ID** → **App registrations** → la app →
**App roles** → **Create app role** (3 roles).

> ⚠️ Lo que importa es el **Value**: Azure lo incluye en el claim `roles` del token
> y es lo que el backend compara (por defecto). Los "Id" (GUID) los genera Azure
> automáticamente y **no** necesitas copiarlos.

| Display name | Value (obligatorio) | Allowed member types | Description |
|---|---|---|---|
| Cliente | `CLIENTE` | Users/Groups | Cliente que crea y sigue sus pedidos |
| Operador | `OPERADOR` | Users/Groups | Gestiona pedidos y estados |
| Administrador | `ADMIN` | Users/Groups | Administra productos, stock, usuarios y operación |

> Si quieres mapear por GUID en vez de Value, puedes sobreescribir
> `AZURE_ROLE_ADMIN_ID` (etc.) en el `.env` / EC2 con el GUID que Azure genere.

### 2. Crear usuarios de prueba y asignar los roles

En **Microsoft Entra ID** → **Users** → crear al menos 3 usuarios (ej. `cliente@NicolazAura.onmicrosoft.com`,
`operador@NicolazAura.onmicrosoft.com`, `admin@NicolazAura.onmicrosoft.com`).

Luego, en **Enterprise applications** → buscar la app → **Users and groups** →
**Add user/group** → elegir el usuario y el **rol** (Cliente/Operador/Administrador) → **Assign**. Repetir para cada usuario.

> Después de asignar roles, el usuario debe **cerrar y volver a iniciar sesión**:
> el claim `roles` solo aparece en tokens nuevos. Si el claim no aparece, otorgar
> **admin consent** en Enterprise applications → la app → *Permissions* → *Grant admin consent*.

### 3. Verificar las redirect URIs

En la app → **Authentication** → Redirect URIs deben estar:
- `http://localhost:8080/login/oauth2/code/azure` (local)
- `https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod/login/oauth2/code/azure` (web/gateway)

## Cómo levantar y probar

```bash
cd ...\project-cloud-native-ev1-dev\project-cloud-native-ev1-dev
docker-compose up --build
```

1. Abrir `http://localhost:4200`.
2. Login → Microsoft → iniciar con cada usuario de prueba.
3. Verificar que la navbar y las rutas cambian según el rol.
4. Pruebas de autorización: sin token (403/401), token inválido, token válido sin permiso (ej. Cliente llamando `PUT /api/orders/1/status` → 403).

## Brechas restantes vs. guía (para decidir después)

- Oracle vs PostgreSQL (la guía menciona Oracle; verificar con el docente si la persistencia Postgres es aceptable).
- Angular + MSAL (la guía lo pide; su grupo eligió React — confirmar aceptación del docente).
- Despliegue real del API Gateway en AWS (template listo).
- Microservicio `orders` con regla "no despachar sin aceptar" demostrada en la demo.