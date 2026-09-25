# AWS API Gateway — Pedidos360 (Evidencia EP1)

API HTTP de AWS que expone el backend como **único punto público de entrada**,
con **JWT Authorizer**, **CORS** y **login OAuth2 público** (Microsoft Entra ID),
según la guía EP1.

```
Navegador
   │
   ├── Web app: http://pedidos360-alb-*.us-east-1.elb.amazonaws.com/   (SPA nginx :4200)
   │      │  /oauth2/**  ·  /login/**  ·  /api/**  → ALB (reglas de path) → BFF
   │      ▼
   │   ALB (http://pedidos360-alb-*.us-east-1.elb.amazonaws.com)
   │      ├── default            → pedidos360-frontend-tg (nginx :4200, SPA)
   │      └── /api/* /oauth2/* /login/* → pedidos360-bff-tg (BFF :8080)
   │
   └── API + login público (guía): https://<api-id>.execute-api.us-east-1.amazonaws.com/prod
          │  $default        → JwtAuthorizerFunction (valida JWT) → ALB → BFF
          │  /oauth2/{proxy+} → OAuthProxyFunction (NONE) → ALB → BFF   [login público]
          │  /login/{proxy+}  → OAuthProxyFunction (NONE) → ALB → BFF   [callback OAuth]
          │  /api/auth/{proxy+}→ OAuthProxyFunction (NONE) → ALB → BFF  [login/registro local]
          │  OPTIONS /{proxy+} → 200 + CORS
          ▼
      ALB (el gateway reenvía a la misma URL del balanceador)
          ▼
      EC2 (Docker: frontend-SPA nginx :4200 · bff :8080 ← users :8083, catalog :8081, orders :8082, db)
```

> **Frontend en la web:** la SPA compilada se sirve con **nginx** en la EC2
> (puerto 4200) detrás del ALB. Las reglas de path del listener enrutan
> `/api/*`, `/oauth2/*` y `/login/*` al BFF, y el resto (la SPA, `/`,
> `/assets/*`, rutas del router como `/auth/callback`) al frontend. Así la
> **misma app corre en localhost (`docker compose up`, `:4200`)** y en la web
> por el ALB, y el login OAuth sigue pasando por el **gateway HTTPS** (Microsoft
> y Google exigen HTTPS; el ALB es HTTP).

## Arquitectura del template (`api-gateway.yaml`)

| Recurso | Función |
|---|---|
| `HttpApi` (HTTP API v2) | API pública con CORS habilitado y stage `prod` |
| `ProxyRoute` (`$default`) | Enruta cualquier `{method} {path}` al ALB (BFF), con authorizer |
| `JwtAuthorizerFunction` (Lambda) | Valida firma HS256, vigencia, issuer y estructura del JWT |
| `Authorizer` | Authorizer tipo REQUEST asociado a `$default` |
| `OAuthProxyFunction` (Lambda) | Proxy **sin authorizer** para las rutas públicas OAuth (`/oauth2/`, `/login/`, `/api/auth/`): reenvía al ALB preservando método, path (sin el prefijo de stage), query, headers y cookies, incluyendo el `Set-Cookie` de sesión y el `Location` de redirección de Microsoft/Google |
| `OAuthRoutes` | `ANY /oauth2/{proxy+}`, `ANY /login/{proxy+}`, `ANY /api/auth/{proxy+}` con `AuthorizationType: NONE` |
| `CorsRoute` | `OPTIONS /{proxy+}` → 200 con cabeceras CORS |
| `Stage prod` | Auto-deploy |

> **Por qué un proxy Lambda para OAuth:** HTTP API **no invoca el authorizer
> cuando falta el header `Authorization`** (responde `401` directo), y las rutas
> con `HTTP_PROXY` + `{proxy+}` reenvían el path como `/`. El flujo OAuth
> arranca **sin token** (navegador → `GET /oauth2/authorization/{azure|google}`
> → 302 al IdP), así que esas rutas se publican con `AuthorizationType: NONE` hacia
> una Lambda que reenvía al BFF con el path correcto (quitando el stage `/prod`
> que HTTP API deja en `event.rawPath`).

El `Authorization: Bearer <token>` se reenvía intacto al BFF, que **vuelve a validar**
issuer, audience, firma y vigencia (segunda validación exigida por la guía).

## Flujo de login OAuth2 público

El servicio users registra **Microsoft Entra ID (`azure`)** y, si está configurado,
**Google (`google`)** como clientes OAuth2 (misma `JWT_SECRET`, mismo destino
`${SPA_BASE_URL}/auth/callback`). Google no trae claims de rol: sus usuarios
ingresan siempre como **CLIENTE**, mientras que Microsoft mapea el claim
`roles` con `AZURE_ROLE_*_ID`.

1. SPA (local `:4200` o web en el ALB) → navega a
   `GET https://<api-id>.execute-api.us-east-1.amazonaws.com/prod/oauth2/authorization/{azure|google}`
   → `OAuthProxyFunction` (NONE) → ALB → BFF `/oauth2/authorization/{azure|google}`.
2. BFF/users responden `302` al IdP (`login.microsoftonline.com` o
   `accounts.google.com`) con
   `redirect_uri=https://<api-id>.execute-api.us-east-1.amazonaws.com/prod/login/oauth2/code/{azure|google}`
   (definido como `AZURE_/GOOGLE_REDIRECT_URI` en la EC2) y `Set-Cookie: JSESSIONID`.
3. El IdP autentica al usuario y redirige al navegador al **mismo gateway**
   (`/prod/login/oauth2/code/{azure|google}?code=...&state=...`) → `OAuthProxyFunction` (NONE) → ALB → BFF → users.
4. users intercambia el `code` con el IdP, crea/actualiza el usuario con su rol,
   emite el **JWT** y redirige a `${SPA_BASE_URL}/auth/callback?token=<jwt>`.
   - Web (EC2): `SPA_BASE_URL=http://pedidos360-alb-*.us-east-1.elb.amazonaws.com`
     → el ALB entrega la SPA (`/auth/callback` cae en la regla default → frontend).
   - Local: `SPA_BASE_URL=http://localhost:4200` (docker-compose).
   La SPA guarda el token en `localStorage` y las llamadas `/api/**` llevan
   `Authorization: Bearer <jwt>` al ALB (o al gateway).

> **Requisito en el portal de Entra ID:** registrar la Redirect URI pública
> `https://<api-id>.execute-api.us-east-1.amazonaws.com/prod/login/oauth2/code/azure`
> **además** de la local `http://localhost:8080/login/oauth2/code/azure` para
> que Microsoft acepte el callback del login por el gateway.

> **Requisito en Google Cloud Console (OAuth consent screen + OAuth client ID
> tipo "Web application"):** registrar las Redirect URIs
> `https://<api-id>.execute-api.us-east-1.amazonaws.com/prod/login/oauth2/code/google`
> (web) y `http://localhost:8080/login/oauth2/code/google` (local). Los envs
> `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` van en la EC2 (o `.env` local);
> si quedan vacíos, el registro de Google se omite y solo queda Microsoft.

## Prerequisitos

1. Cuenta AWS con CLI configurado: `aws configure`.
2. Backend corriendo en EC2 (ver `userdata-backend.sh`) y el ALB apuntando al BFF (`:8080`).
3. La `JWT_SECRET` **debe ser la misma** en `ms-pedidos360-users`, `ms-pedidos360-bff`
   y el template (parámetro `JwtSecret`).

## Despliegue

```bash
aws cloudformation deploy \
  --template-file api-gateway.yaml \
  --stack-name pedidos360-api \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
      JwtSecret="<misma JWT_SECRET del .env>" \
      BffUrl="http://pedidos360-alb-571995876.us-east-1.elb.amazonaws.com" \
      AllowedOrigin="http://localhost:4200"
```

## Usar en el frontend

La URL de salida (`Outputs.ApiUrl`) reemplaza el BFF en el frontend:

```bash
# SPA (local)
VITE_BFF_URL=<ApiUrl> npm run dev

# SPA (docker): cambiar en docker-compose.yml la variable VITE_BFF_URL
```

### Login OAuth por el gateway desde la SPA

El login **debe arrancar en la URL absoluta del BFF público**, nunca por la ruta
relativa del proxy de Vite: así la cookie de sesión `JSESSIONID` queda en el
**mismo dominio** que el `redirect_uri` del callback (si arranca relativo, la
cookie queda en `localhost:4200` y el callback del gateway no la recibe →
`authorization_request_not_found`).

Con el flujo apuntado al gateway, **ambas** variables del frontend deben usar la
URL pública (docker-compose.yml):

```yaml
frontend:
  environment:
    # proxy Vite para /api y /oauth2 → gateway (flujo AWS, authorizer incluido)
    VITE_BFF_URL: https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod
    # login OAuth con URL absoluta (mismo dominio que el callback)
    VITE_PUBLIC_BFF_URL: https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod
```

Para **volver al flujo local** (sin gateway) basta con:

```yaml
    VITE_BFF_URL: http://ms-pedidos360-bff:8080
    VITE_PUBLIC_BFF_URL: http://localhost:8080
```

> Al reconstruir el frontend (`docker compose build frontend && docker compose up -d frontend`),
> Vite regenera los hashes de los paquetes: si el navegador abre la pestaña vieja
> con caché, pide módulos que ya no existen → **pantalla blanca**. Hacer recarga
> forzada (`Ctrl+Shift+R`) o borrar la caché del sitio.

### Frontend en la web (SPA + nginx + ALB)

La SPA **también se despliega en la EC2** detrás del ALB, servida por `nginx`
(`FRONTEND/Dockerfile.prod`: build de Vite + nginx). Así la app corre en
`http://pedidos360-alb-*.us-east-1.elb.amazonaws.com` igual que en localhost.

**Pasos de despliegue** (ya aplicados):

```bash
# 1. Imagen de producción y push a ECR
docker build -f FRONTEND/Dockerfile.prod \
  --build-arg VITE_PUBLIC_BFF_URL=https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod \
  -t pedidos360-frontend:prod FRONTEND
docker tag pedidos360-frontend:prod $REGISTRY/pedidos360-frontend:prod
docker push $REGISTRY/pedidos360-frontend:prod

# 2. EC2: correr el contenedor nginx (puerto 4200 -> 80)
docker run -d --name pedidos360-frontend --restart unless-stopped -p 4200:80 $REGISTRY/pedidos360-frontend:prod

# 3. Target group del frontend (health check en "/")
aws elbv2 create-target-group --name pedidos360-frontend-tg --protocol HTTP --port 4200 \
  --vpc-id <vpc> --health-check-path "/" --matcher HttpCode=200
aws elbv2 register-targets --target-group-arn arn:...:targetgroup/pedidos360-frontend-tg/<id> \
  --targets Id=<instance-id>,Port=4200

# 4. Reglas del listener (orden: paths del BFF primero)
aws elbv2 create-rule --listener-arn arn:...:listener/app/pedidos360-alb/<id>/<listener> --priority 10 \
  --conditions "Field=path-pattern,Values={/api/*,/oauth2/*,/login/*}" \
  --actions "Type=forward,TargetGroupArn=arn:...:targetgroup/pedidos360-bff-tg/<id>"
aws elbv2 modify-listener --listener-arn arn:...:listener/app/pedidos360-alb/<id>/<listener> \
  --default-actions "Type=forward,TargetGroupArn=arn:...:targetgroup/pedidos360-frontend-tg/<id>"
```

Después del login OAuth, users redirige a `SPA_BASE_URL`; en la EC2 ya apunta al
ALB (`http://pedidos360-alb-*.us-east-1.elb.amazonaws.com`), así el token llega a
la **web**. El security group de la instancia debe permitir el puerto `4200`
desde el security group del ALB.

`LoginPage.tsx` navega a `${VITE_PUBLIC_BFF_URL}/oauth2/authorization/azure`
(`window.location.href`). Después del login, users redirige a
`${SPA_BASE_URL}/auth/callback?token=<jwt>`: SPA local `http://localhost:4200` o,
en la EC2, `http://pedidos360-alb-*.us-east-1.elb.amazonaws.com` (web).

> Si al abrir la SPA sigue entrando directo con la sesión guardada, el
> `StartGate` de `App.tsx` ahora muestra el login ("Ya tenés una sesión
> iniciada" → Continuar / Cambiar de cuenta) en vez de entrar al catálogo. Con
> "Cambiar de cuenta" `logout()` borra el token y se puede iniciar con otra.
>
> `SecurityConfig` de users inyecta `prompt=select_account` en el authorize, así
> **Microsoft siempre muestra el selector de cuentas** y no reutiliza la cuenta
> anterior sin preguntar. Para que aplique en el **gateway**, publicar la imagen
> actualizada del users en ECR y recrear el contenedor en la EC2:
>
> ```bash
> docker build -t $ACCOUNT.dkr.ecr.$REGION.amazonaws.com/pedidos360-users:latest ./ms-pedidos360-users
> aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $REGISTRY
> docker push $REGISTRY/pedidos360-users:latest   # luego en la EC2: docker pull + docker rm/run
> ```
>
> ### Fix de 401 intermitente "Token inválido o expirado" (clock skew)
>
> **Síntoma:** el JWT válido daba `200` directo a la IP de la EC2 pero `401`
> intermitente por el gateway (`{"error":"Token inválido o expirado"}`). El log
> del BFF lo confirmaba:
>
> ```text
> WARN JwtService: JWT inválido: JwtException - Token emitido en el futuro
> ```
>
> **Causa:** `JwtService` del BFF validaba `iat`/`exp` contra su reloj sin
> tolerancia. El PC del desarrollador va unos segundos adelante del reloj de la
> EC2, así que un JWT recién emitido (`iat = ahora local`) queda "en el futuro"
> visto desde la EC2 durante unos segundos. La cadena API Gateway → Lambda →
> ALB añade latencia y hace el rechazo intermitente (la llamada directa a veces
> cae fuera de la ventana y valida).
>
> **Fix:** ventana de skew configurable en el BFF (default 30s):
> `jwt.clock-skew-seconds: ${JWT_CLOCK_SKEW_SECONDS:30}` en `application.yaml` y
> en `JwtService.validate` se compara contra `now ± skew`. En la EC2 se setea
> `JWT_CLOCK_SKEW_SECONDS=60` (ver `userdata-backend.sh`). No se toca el
> authorizer del gateway (los casos expirado → 403 siguen intactos).
>
> ```bash
> # publicar el BFF con el fix y recrear en la EC2
> docker compose build ms-pedidos360-bff
> docker tag project-cloud-native-ev1-dev-ms-pedidos360-bff:latest $REGISTRY/pedidos360-bff:latest
> docker push $REGISTRY/pedidos360-bff:latest
> # en la EC2: docker pull + docker rm -f ms-pedidos360-bff + docker run (con JWT_CLOCK_SKEW_SECONDS=60)
> ```
>
> ### Catálogo en la EC2
>
> `userdata-backend.sh` ahora incluye el **seed del catálogo** (5 categorías + 18
> productos, misma data que local), así que una EC2 nueva queda con productos. Si
> solo hay que resembrar una EC2 existente:
>
> ```bash
> docker exec pedidos360-db psql -U postgres -d catalog_db -c "SELECT id,name FROM categories;"
> # insertar con el mismo SQL del userdata (INSERT ... ON CONFLICT DO NOTHING)
> ```

### Round-trip de sesión OAuth (verificación con curl)

```bash
curl -c cookies.txt -i "$BASE/oauth2/authorization/azure"          # guarda JSESSIONID
curl -b cookies.txt -i "$BASE/login/oauth2/code/azure?code=xxx&state=yyy"
```

- Sin cookie (o dominios distintos) → `authorization_request_not_found`.
- Con la cookie + state mismatched → `invalid_request` (la sesión se encontró).
- Con la cookie + code falso → `invalid_grant`/`AADSTS9002313` (la sesión OK,
  falla solo el intercambio del code) — el flujo completo ya funciona.

## Cómo probar la API con curl

URL pública (stage `prod`):

```bash
BASE=https://821dbjhp16.execute-api.us-east-1.amazonaws.com/prod
```

### 1. OPTIONS preflight → `200` + CORS

```bash
curl -i -X OPTIONS "$BASE/api/users/profile" \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: GET"
```

### 2. Sin token → `401`

```bash
curl -i "$BASE/api/users/profile"
```

### 3. Firma corrupta → `403`

```bash
curl -i "$BASE/api/users/profile" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.basura"
```

### 4. Token válido → `200`

Generá un JWT HS256 con la misma `JWT_SECRET`, `sub` = un usuario existente en la
DB de users y claims `iss=pedidos360-users`, `aud=pedidos360-bff`, `role`:

```bash
TOKEN=$(node genjwt.js "$JWT_SECRET" "CLIENTE" "cliente@NicolazAura.onmicrosoft.com")
curl -i "$BASE/api/users/profile" -H "Authorization: Bearer $TOKEN"
```

> `genjwt.js` (generador de ejemplo): payload
> `{ sub, role, iss:"pedidos360-users", aud:"pedidos360-bff", iat, exp }` firmado
> con HMAC-SHA256 a partir de la `JWT_SECRET`.

### 5. Inicio de sesión OAuth (Microsoft) → `302` a `login.microsoftonline.com`

```bash
curl -i "$BASE/oauth2/authorization/azure"
```

- `Location` apunta a `login.microsoftonline.com` con `redirect_uri` del propio gateway.
- `Set-Cookie: JSESSIONID` se reenvía tal cual (sesión OAuth del BFF).

### 5b. Inicio de sesión OAuth (Google) → `302` a `accounts.google.com`

```bash
curl -i "$BASE/oauth2/authorization/google"
```

- Requiere `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` configurados (en la EC2 o el
  `.env` local) y la redirect URI del gateway registrada en Google Cloud Console.
- `Location` apunta a `accounts.google.com/o/oauth2/v2/auth` con `client_id`, la
  misma redirect URI del gateway y `prompt=select_account`.

### 6. Callback OAuth sin `code`/`state` válidos → `401` controlado de Spring

```bash
curl -i "$BASE/login/oauth2/code/azure?code=xxx&state=yyy"
```

Confirma que la ruta pública llega al BFF/users con el path sin stage (un `404`
con `path="/prod/..."` indicaría que el proxy no está normalizando el stage).

## Nota de seguridad

El secreto del authorizer se inyecta como parámetro del stack. En producción,
guárdalo en **SSM Parameter Store** o **Secrets Manager** y referéncialo con
`!! ` en `Lambda::Function.Environment` (usar `AWS::SSM::Parameter::Value`).