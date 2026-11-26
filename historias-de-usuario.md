# ShopFlow — Historias de usuario

Backlog para el redo de ShopFlow, armado a partir de la auditoría de código (los números `#n` remiten a los hallazgos de esa auditoría). El objetivo es una tienda lista para producción para un comercio chico que, además, sirva de portafolio: **Postgres** es la fuente de verdad, **Redis** maneja datos efímeros y rápidos (carritos, rate limiting, tokens, colas, caché) y **Neo4j** resuelve las recomendaciones.

## Convenciones

- **Formato:** `Como <rol> quiero <acción> para <beneficio>`.
- **Roles:** Invitado (sin cuenta), Cliente (logueado), Admin, Desarrollador.
- **Prioridad:** `Must` bloquea la salida a producción · `Should` debe estar en la primera versión pública · `Could` suma valor, puede esperar.
- **Escenarios:** en Gherkin (Dado / Cuando / Entonces). Cada historia tiene un camino feliz y 1 o 2 caminos tristes según su tamaño.
- **Tareas:** `BE` backend · `FE` frontend · `DB` base de datos · `OPS` infraestructura · `QA` tests.

## Definition of Done

Una historia está terminada cuando:

1. Todos sus escenarios pasan y los críticos tienen test automatizado.
2. La CI está en verde (build + tests de back y front).
3. Los errores devuelven el formato único de la HU-03.
4. No hay secretos ni URLs de entorno en el código.
5. Si toca UI, tiene estados de carga, vacío y error.

## Roadmap sugerido

| Sprint | Foco | Historias |
|---|---|---|
| 1 | Base para producción | HU-01, HU-02, HU-03, HU-04, HU-27 |
| 2 | Cuenta y catálogo | HU-06, HU-07, HU-08, HU-31, HU-32, HU-33, HU-10, HU-11 |
| 3 | Carrito y checkout | HU-12, HU-13, HU-16, HU-17 |
| 4 | Pagos y pedidos | HU-18, HU-19, HU-20, HU-30 |
| 5 | Panel de admin | HU-21, HU-22, HU-23, HU-24 |
| 6 | Neo4j, legales y extras | HU-14, HU-15, HU-25, HU-26, HU-05, HU-09, HU-28, HU-29 |

---

## Épica 0 — Base para producción

### HU-01 · Configuración por entorno y secretos fuera del código
**Prioridad:** Must · **Auditoría:** #4, #5 · **Estado:** Hecha

> Como **desarrollador** quiero que los secretos y las URLs se configuren por variables de entorno para poder desplegar la tienda sin exponer credenciales.

**Tareas**
- [x] BE: reemplazar los valores de `application.properties` por `${JWT_SECRET}`, `${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`, `${NEO4J_URI}`, `${NEO4J_USER}`, `${NEO4J_PASSWORD}`, `${REDIS_HOST}`, `${REDIS_PORT}`.
- [x] BE: crear los perfiles `dev` y `prod`; `show-sql` solo en `dev`.
- [x] BE: leer los orígenes de CORS desde `app.cors.allowed-origins` (lista separada por comas).
- [x] BE: al arrancar, verificar que `JWT_SECRET` exista y tenga al menos 32 bytes; si no, fallar con un mensaje claro.
- [x] FE: crear `environment.prod.ts` con `apiUrl: '/api'` y configurar `fileReplacements` en `angular.json`.
- [x] OPS: agregar `.env.example`, sumar `.env` al `.gitignore` y **rotar** el secreto JWT y las contraseñas que hoy están en el historial de git.

**Escenarios**
```gherkin
Escenario: La app arranca con la configuración del entorno
  Dado que las variables de entorno de base de datos, Redis, Neo4j y JWT están definidas
  Cuando inicio el backend con el perfil "prod"
  Entonces se conecta a las tres bases de datos
  Y los tokens se firman con el secreto del entorno

Escenario: Falta el secreto JWT
  Dado que la variable JWT_SECRET no está definida
  Cuando inicio el backend
  Entonces la aplicación no arranca
  Y el log indica "Falta la variable JWT_SECRET"

Escenario: Un origen no autorizado llama a la API
  Dado que app.cors.allowed-origins es "https://mitienda.com"
  Cuando una página en "https://otro-sitio.com" llama a GET /api/products
  Entonces la respuesta no incluye Access-Control-Allow-Origin
  Y el navegador bloquea la respuesta
```

---

### HU-02 · Migraciones versionadas y datos iniciales
**Prioridad:** Must · **Auditoría:** #14, #6 · **Estado:** Hecha

> Como **desarrollador** quiero que el esquema de la base se cree con migraciones versionadas para poder cambiarlo en producción sin perder datos.

**Tareas**
- [x] BE: agregar Flyway y pasar `ddl-auto` a `validate`.
- [x] DB: `V1__esquema_inicial.sql` con las tablas actuales más `orders.status` y `orders.expires_at`.
- [x] DB: `V2__provincias_y_ciudades.sql` con el seed de provincias y ciudades de Argentina.
- [x] DB: `V3__admin_inicial.sql`, o un comando que cree el primer admin desde variables de entorno. *(Resuelto con `AdminBootstrap` y `ADMIN_EMAIL`/`ADMIN_PASSWORD`, para no dejar el hash en SQL.)*
- [x] DB: índices en `products(active, category_id)`, `orders(user_id, created_at)` y `orders(status, expires_at)`.

**Escenarios**
```gherkin
Escenario: Base vacía
  Dado una base de datos Postgres vacía
  Cuando inicio el backend
  Entonces Flyway aplica las migraciones V1 a Vn en orden
  Y la tabla de ciudades tiene las ciudades cargadas

Escenario: El código no coincide con el esquema
  Dado que agregué un campo a una entidad sin escribir la migración
  Cuando inicio el backend
  Entonces el arranque falla en la validación de Hibernate
  Y el mensaje indica la tabla y la columna faltante
```

---

### HU-03 · Formato de error único y mensajes reales
**Prioridad:** Must · **Auditoría:** #7 · **Estado:** Hecha

> Como **cliente** quiero ver mensajes de error que expliquen qué pasó para saber cómo seguir.

**Tareas**
- [x] BE: `GlobalExceptionHandler` que devuelva `ProblemDetail` (RFC 9457) con `title`, `detail`, `status` y `errors` por campo.
- [x] BE: mapear `AuthenticationException` → 401, `AccessDeniedException` → 403, `NotFoundException` → 404, `DataIntegrityViolationException`/`ConflictException` → 409, validación → 400.
- [x] BE: para el resto, 500 con un `errorId` (UUID); loguear el stacktrace con ese id y no enviárselo al cliente. Quitar `printStackTrace`.
- [x] BE: reemplazar los `RuntimeException` de `AddressService` por excepciones de dominio.
- [x] FE: helper `errorMessage(err)` usado por el toaster: status 0 → "No pudimos conectar con el servidor", 4xx → el `detail` del backend, 500 → "Algo falló de nuestro lado (código X)".

**Escenarios**
```gherkin
Escenario: Error de validación con detalle por campo
  Dado que soy admin
  Cuando creo un producto con precio -5
  Entonces recibo 400
  Y el cuerpo incluye errors.price = "El precio debe ser mayor a 0"

Escenario: Error inesperado
  Dado que ocurre una excepción no controlada en el servidor
  Cuando el cliente hace la request
  Entonces recibe 500 con un errorId
  Y la respuesta no contiene el stacktrace
  Y el log del servidor tiene el stacktrace con el mismo errorId

Escenario: El servidor no responde
  Dado que el backend está caído
  Cuando el cliente intenta iniciar sesión
  Entonces ve "No pudimos conectar con el servidor"
  Y no ve "Email o contraseña incorrectos"
```

---

### HU-04 · Despliegue con Docker y CI
**Prioridad:** Must · **Estado:** En curso

> Como **desarrollador** quiero levantar todo el sistema con un comando y que cada cambio se valide solo para desplegar con confianza.

**Tareas**
- [x] OPS: `Dockerfile` multi-stage para el backend (Maven → JRE 21 slim).
- [x] OPS: `Dockerfile` para el frontend (build de Angular → Nginx, con proxy de `/api` al backend).
- [x] OPS: `docker-compose.yml` con Postgres, Redis, Neo4j, backend y frontend, con healthchecks y `depends_on: condition: service_healthy`.
- [x] OPS: GitHub Actions que corra build y tests de back y front en cada PR. *(Además levanta el stack completo con `--wait`. Falta activar la protección de `main` en GitHub.)*
- [ ] OPS: backup diario de Postgres (`pg_dump`) con retención de 7 días. *(Escrito en `docker-compose.prod.yml` + `ops/backup.sh`; falta probarlo en el servidor.)*
- [ ] OPS: HTTPS (Caddy o Nginx + Let's Encrypt) y dominio propio. *(Caddy configurado en `docker-compose.prod.yml`; falta el dominio y el servidor.)*

**Escenarios**
```gherkin
Escenario: Levantar todo el sistema
  Dado que tengo Docker instalado y un archivo .env completo
  Cuando ejecuto "docker compose up -d"
  Entonces los cinco servicios quedan "healthy"
  Y la tienda responde en http://localhost

Escenario: Un PR rompe un test
  Dado un pull request que hace fallar un test
  Cuando corre la CI
  Entonces el pipeline queda en rojo
  Y GitHub bloquea el merge
```

---

### HU-05 · Observabilidad y métricas técnicas
**Prioridad:** Should

> Como **desarrollador** quiero ver la salud del sistema y los errores en producción para detectar problemas antes que los clientes.

**Tareas**
- [ ] BE: Spring Boot Actuator con `/actuator/health` (Postgres, Redis, Neo4j) y `/actuator/prometheus`.
- [ ] BE: grupo de readiness que incluya Postgres y Redis pero **no** Neo4j, porque las recomendaciones no son críticas.
- [ ] BE: filtro que agregue un `requestId` al MDC y al header `X-Request-Id`; logs en JSON en `prod`.
- [ ] BE + FE: integrar Sentry con el entorno y el `requestId`.
- [ ] OPS: proteger `/actuator/**` (solo red interna o rol ADMIN).

**Escenarios**
```gherkin
Escenario: Sistema saludable
  Dado que Postgres, Redis y Neo4j están arriba
  Cuando consulto GET /actuator/health
  Entonces el estado es "UP" y lista los tres componentes

Escenario: Neo4j caído
  Dado que Neo4j no responde
  Cuando consulto GET /actuator/health/readiness
  Entonces el estado sigue siendo "UP"
  Y GET /actuator/health muestra el componente neo4j en "DOWN"

Escenario: Error en producción
  Dado que una request termina en 500
  Cuando reviso Sentry
  Entonces veo el evento con el mismo requestId que está en los logs
```

---

## Épica 1 — Cuenta y sesión

### HU-06 · Registro de cliente
**Prioridad:** Must · **Estado:** Hecha

> Como **invitado** quiero crear una cuenta para poder comprar y guardar mi historial.

**Tareas**
- [x] BE: normalizar el email (trim + minúsculas) antes de validar y guardar.
- [x] BE: si se viola la restricción única de email (dos registros a la vez), devolver 409 y no 500.
- [x] BE: eliminar `POST /api/users`, que duplica el registro sin validar (auditoría #19).
- [x] FE: mostrar los errores por campo que devuelve el backend y redirigir al `returnUrl` si existe.

**Escenarios**
```gherkin
Escenario: Registro exitoso
  Dado que no existe una cuenta con "ana@mail.com"
  Cuando me registro con nombre "Ana", email " Ana@Mail.com " y una contraseña de 10 caracteres
  Entonces la cuenta se crea con el email "ana@mail.com"
  Y quedo con la sesión iniciada

Escenario: Email ya registrado
  Dado que existe una cuenta con "ana@mail.com"
  Cuando intento registrarme con "ana@mail.com"
  Entonces recibo 409
  Y veo "Ya existe una cuenta con ese email"

Escenario: Contraseña corta
  Cuando intento registrarme con una contraseña de 5 caracteres
  Entonces recibo 400 con el error en el campo "password"
  Y la cuenta no se crea
```

---

### HU-07 · Inicio de sesión con límite de intentos
**Prioridad:** Must · **Auditoría:** #7, #13 · **Estado:** Hecha

> Como **cliente** quiero iniciar sesión de forma segura para que nadie pueda adivinar mi contraseña.

**Tareas**
- [x] BE: credenciales inválidas → 401 "Email o contraseña incorrectos", sin revelar si el email existe.
- [x] BE (Redis): contador `login:fail:{ip}:{email}` con `INCR` + `EXPIRE 900`; a partir de 5 fallos, responder 429 con el header `Retry-After`. *(La IP real detrás del proxy sale de la `RemoteIpValve` de Tomcat, que no se puede falsificar con `X-Forwarded-For`.)*
- [x] BE: borrar el contador cuando el login es exitoso.
- [x] FE: mostrar el tiempo de espera cuando llega un 429.

**Escenarios**
```gherkin
Escenario: Login correcto
  Dado que existe la cuenta "ana@mail.com" con contraseña "secreta123"
  Cuando inicio sesión con esos datos
  Entonces recibo un token
  Y el contador de intentos fallidos se borra

Escenario: Contraseña incorrecta
  Cuando inicio sesión con "ana@mail.com" y una contraseña equivocada
  Entonces recibo 401 con "Email o contraseña incorrectos"
  Y el contador de intentos fallidos sube a 1

Escenario: Demasiados intentos
  Dado que fallé 5 veces en los últimos 15 minutos
  Cuando intento iniciar sesión otra vez, aunque sea con la contraseña correcta
  Entonces recibo 429 con un Retry-After
  Y veo "Demasiados intentos. Probá de nuevo en 15 minutos"
```

---

### HU-08 · Manejo de sesión vencida y permisos
**Prioridad:** Must · **Auditoría:** #11 · **Estado:** Hecha

> Como **cliente** quiero que la app me avise cuando mi sesión venció para no quedar en un estado roto.

**Tareas**
- [x] FE: ante un 401, el interceptor llama a `authStore.logout()` (no solo borra el storage) y redirige a `/login?returnUrl=<ruta actual>`.
- [x] FE: ante un 403, mostrar "No tenés permiso" **sin** cerrar la sesión. *(El interceptor ya no cierra sesión; el mensaje lo pone el toaster con el `detail` del backend.)*
- [x] FE: `authGuard` revisa `isTokenExpired` además de que exista el token.
- [x] FE: después del login, volver al `returnUrl`.

**Escenarios**
```gherkin
Escenario: Navegación con sesión válida
  Dado que tengo un token vigente
  Cuando entro a /cart
  Entonces veo mi carrito

Escenario: El token vence con la pestaña abierta
  Dado que dejé la pestaña abierta y mi token venció
  Cuando agrego un producto al carrito
  Entonces la app me lleva a /login?returnUrl=/products/all
  Y veo "Tu sesión venció, iniciá sesión de nuevo"
  Y el header ya no muestra mi usuario

Escenario: Un cliente entra a una ruta de admin
  Dado que soy un cliente sin rol ADMIN
  Cuando entro a /admin/products
  Entonces vuelvo al inicio con el mensaje "No tenés permiso"
  Y sigo con la sesión iniciada
```

---

### HU-09 · Recuperar contraseña
**Prioridad:** Should · **Estado:** Hecha

> Como **cliente** quiero restablecer mi contraseña si la olvidé para no perder mi cuenta.

**Tareas**
- [x] BE (Redis): `POST /api/auth/forgot` genera un token aleatorio seguro y lo guarda como `pwreset:{hash}` → `userId` con TTL de 30 minutos.
- [x] BE: `POST /api/auth/reset` valida el token, cambia la contraseña y borra la clave (uso único).
- [x] BE: enviar el email con el link (depende de HU-20).
- [x] FE: pantallas "Olvidé mi contraseña" y "Nueva contraseña".

**Escenarios**
```gherkin
Escenario: Restablecer con éxito
  Dado que pedí recuperar la contraseña de "ana@mail.com"
  Cuando abro el link dentro de los 30 minutos y elijo una contraseña nueva
  Entonces puedo iniciar sesión con la contraseña nueva
  Y el link ya no sirve otra vez

Escenario: Link vencido o ya usado
  Dado un link de recuperación vencido
  Cuando intento usarlo
  Entonces veo "El enlace venció, pedí uno nuevo"

Escenario: Email que no existe
  Cuando pido recuperar la contraseña de "nadie@mail.com"
  Entonces veo el mismo mensaje que si existiera: "Si el email está registrado, te enviamos un enlace"
  Y no se envía ningún email
```

---

### HU-31 · Verificación del email
**Prioridad:** Must · **Estado:** Hecha

> Como **dueño del comercio** quiero que cada cuenta confirme su email para no aceptar pedidos de direcciones falsas o ajenas.

**Tareas**
- [x] DB (`V3`): `users.email_verified`; las cuentas que ya existían quedan verificadas.
- [x] BE (Redis): al registrarse, token aleatorio guardado como `verify:email:{hash}` → `userId` con TTL de 24 horas, de un solo uso (`GETDEL`).
- [x] BE: email con el enlace (Spring Mail; en desarrollo llega a Mailpit, `http://localhost:8025`). Si el SMTP está caído, el registro funciona igual.
- [x] BE: `POST /api/auth/verify-email` confirma, inicia sesión y recuerda el dispositivo; `POST /api/auth/verify-email/resend` (con sesión) manda otro, como mucho uno por minuto.
- [x] BE: `createOrder` responde 403 "Confirmá tu email para comprar" si la cuenta no está verificada (se mira la base, no el JWT).
- [x] FE: aviso bajo el header con "Reenviar email", página `/verify-email` y checkout bloqueado hasta confirmar.

**Escenarios**
```gherkin
Escenario: Confirmo con el enlace
  Dado que me registré con "ana@mail.com"
  Cuando abro el enlace del email dentro de las 24 horas
  Entonces veo "Tu email está confirmado"
  Y puedo comprar

Escenario: Todavía no confirmé mi email
  Dado que me registré y no abrí el enlace
  Cuando intento confirmar la compra
  Entonces veo "Confirmá tu email para comprar"
  Y mi carrito y el stock quedan como estaban

Escenario: Enlace usado o vencido
  Cuando abro un enlace que ya usé
  Entonces veo "El enlace venció o ya se usó"
```

---

### HU-32 · Ingreso con Google
**Prioridad:** Should · **Estado:** Hecha

> Como **cliente** quiero entrar con mi cuenta de Google para no crear ni recordar otra contraseña.

**Tareas**
- [x] DB (`V3`): `users.google_sub` (único) y `password` opcional, con un CHECK para que toda cuenta tenga alguna forma de entrar.
- [x] BE: `POST /api/auth/google` valida el ID token de Google: firma con sus claves públicas, emisor, `aud` = nuestro client id, vencimiento y `email_verified`.
- [x] BE: busca por `google_sub`; si no, une la cuenta con el mismo email (conserva contraseña, pedidos y nombre); si no, crea una cuenta verificada sin contraseña.
- [x] BE: `GET /api/auth/config` devuelve el client id; sin `GOOGLE_CLIENT_ID` el botón no aparece.
- [x] FE: botón oficial de Google (Google Identity Services, popup) en ingreso y registro; el script se carga solo si hace falta.

**Escenarios**
```gherkin
Escenario: Primera vez con Google
  Dado que no tengo cuenta en ShopFlow
  Cuando elijo "Continuar con Google"
  Entonces se crea mi cuenta con el email ya confirmado
  Y quedo con la sesión iniciada

Escenario: Ya tenía cuenta con contraseña
  Dado que tengo cuenta con "ana@gmail.com" y contraseña
  Cuando entro con Google con ese mismo email
  Entonces entro a la misma cuenta, con mis pedidos
  Y mi contraseña sigue funcionando

Escenario: Token emitido para otro sitio
  Cuando alguien envía un token de Google emitido para otra aplicación
  Entonces la API lo rechaza
```

---

### HU-33 · Verificación de dispositivo nuevo
**Prioridad:** Should · **Estado:** Hecha

> Como **cliente** quiero que me pidan un código por email cuando alguien entra con mi contraseña desde un dispositivo nuevo, para que no alcance con robarme la contraseña.

**Tareas**
- [x] BE (Redis): dispositivos de confianza `device:{hash}` → `userId`, 90 días que se renuevan con cada uso. El navegador guarda el token aunque cierre sesión.
- [x] BE: contraseña correcta desde un navegador desconocido → 202 con `challengeId`; código de 6 números por email, válido 10 minutos.
- [x] BE: 5 códigos equivocados anulan el intento; otro código como mucho una vez por minuto y 5 envíos cada 15 minutos por cuenta; si el SMTP está caído, 503 con un mensaje claro.
- [x] BE: registrarse, confirmar el email o entrar con Google ya prueban el email: ese navegador queda de confianza sin código.
- [x] FE: segundo paso del ingreso con el código (`autocomplete="one-time-code"`), "Mandame otro código" y "Usar otra cuenta".

**Escenarios**
```gherkin
Escenario: Entro desde un dispositivo nuevo
  Dado que tengo cuenta y nunca entré desde este navegador
  Cuando ingreso email y contraseña correctos
  Entonces me piden el código que llegó a "a***@mail.com"
  Y con el código correcto entro y el navegador queda recordado

Escenario: Alguien tiene mi contraseña pero no mi email
  Dado que alguien conoce mi contraseña
  Cuando prueba 5 códigos equivocados
  Entonces el intento se anula y tiene que empezar de nuevo
  Y el email del código ya me dijo que cambie la contraseña si no fui yo

Escenario: Navegador conocido
  Dado que ya confirmé un código en este navegador
  Cuando vuelvo a ingresar con mi contraseña
  Entonces entro sin código
```

---

## Épica 2 — Catálogo

### HU-10 · Listado de productos con filtros, búsqueda y paginación
**Prioridad:** Must · **Auditoría:** #8 · **Estado:** Hecha

> Como **invitado** quiero explorar solo los productos disponibles, filtrar y buscar, para encontrar rápido lo que busco.

**Tareas**
- [x] BE: `GET /api/products?page&size&category&q&sort` devuelve un `Page` solo con productos activos (`size` máximo 48).
- [x] BE: `GET /api/admin/products` con todos los productos, incluidos los inactivos (para la HU-21).
- [x] BE: búsqueda por nombre y descripción sin distinguir mayúsculas (`ILIKE`, o `pg_trgm` si crece).
- [x] BE: eliminar los endpoints sin uso `priceRange`, `timeRange` y `name` (auditoría #19).
- [x] FE: filtros sincronizados con la URL (`?q=&category=&page=`) para poder compartir el link. *(La categoría va en la ruta: `/products/Remeras?q=lino&sort=price_asc&page=2`. Incluye orden, paginación y búsqueda en el header.)*
- [x] BE: `GET /api/products/suggestions?q=` con los 5 productos activos más parecidos. Índice en memoria con un trie de las palabras de los nombres: sin acentos ni mayúsculas, y tolera 1 error en palabras de 4 a 7 letras y 2 desde 8. La búsqueda del catálogo también encuentra esos productos.
- [x] FE: sugerencias mientras escribís, desde la 3ª letra y 250 ms después de la última tecla, con foto chica, nombre y precio; se recorren con flechas, Enter y Escape.

**Escenarios**
```gherkin
Escenario: Sugerencias mientras escribo
  Dado que existe el producto "Sartén de hierro fundido 26 cm"
  Cuando escribo "sarteb" en el buscador
  Entonces debajo del buscador veo "Sartén de hierro fundido 26 cm" con su foto y su precio
  Y veo como mucho 5 sugerencias

Escenario: Explorar por categoría
  Dado que hay 30 productos activos en "Remeras"
  Cuando entro a la categoría "Remeras"
  Entonces veo los primeros 12 productos
  Y puedo pasar a la página 2

Escenario: Búsqueda sin resultados
  Cuando busco "zzzz"
  Entonces recibo una lista vacía con status 200
  Y veo "No encontramos productos para 'zzzz'" con un botón para limpiar la búsqueda

Escenario: Producto desactivado
  Dado que el admin desactivó el producto "Gorra roja"
  Cuando entro al catálogo
  Entonces "Gorra roja" no aparece en el listado
```

---

### HU-11 · Detalle de producto
**Prioridad:** Must · **Estado:** Hecha

> Como **invitado** quiero ver el detalle de un producto con todas sus fotos para decidir si lo compro.

**Tareas**
- [x] BE: `GET /api/products/{id}` devuelve 404 si el producto está inactivo (salvo para admin).
- [x] DB (`V4`): tabla `product_images(product_id, url, position)`; la de `position = 0` es la portada que usa la card del listado.
- [x] BE: el detalle devuelve `images[]` ordenadas por `position`, y el promedio y la cantidad de reseñas (HU-30).
- [x] FE: ruta `/product/:id` con galería, descripción, precio con IVA incluido, stock, selector de cantidad, agregar al carrito y wishlist.
- [x] FE: galería con foto principal, flechas, miniaturas y swipe en mobile. Sin autoplay (norma del kit y WCAG 2.2.2).
- [x] FE: zoom a pantalla completa con la foto en resolución original; se cierra con Esc o tocando afuera y devuelve el foco.
- [x] FE: bloque "Otros también compraron" (HU-15).

**Escenarios**
```gherkin
Escenario: Ver un producto
  Dado que el producto 15 está activo y tiene stock 3
  Cuando entro a /product/15
  Entonces veo su nombre, precio, descripción y "Quedan 3"

Escenario: Recorrer las fotos
  Dado que el producto 15 tiene 4 fotos
  Cuando toco la tercera miniatura
  Entonces la foto principal pasa a ser la tercera
  Y con las flechas del teclado puedo ir a la anterior y a la siguiente
  Y al tocar la foto principal se abre el zoom en resolución original

Escenario: Producto inexistente o inactivo
  Cuando entro a /product/9999
  Entonces veo la página "Este producto no existe o ya no está disponible"
  Y un link para volver al catálogo
```

---

### HU-30 · Reseñas de productos comprados
**Prioridad:** Should · **Estado:** Hecha

> Como **cliente** quiero calificar y comentar los productos que recibí, y leer las reseñas de otros, para comprar con más confianza.

**Tareas**
- [x] DB (`V5`): tabla `reviews(id, product_id, user_id, order_id, rating 1–5, comment, created_at)` con `UNIQUE(product_id, user_id)`, más `hidden` para la moderación.
- [x] BE: `POST /api/products/{id}/reviews` solo si el usuario tiene una orden `ENTREGADA` con ese producto (envío o retiro); si no, 403. Si ya lo reseñó, 409.
- [x] BE: `GET /api/products/{id}/reviews?rating&page` paginado, más un resumen con promedio, total y cantidad por estrella, y `canReview` para quien pregunta.
- [x] BE: el admin puede ocultar una reseña (moderación mínima); las ocultas no cuentan en el promedio. `PUT /api/admin/reviews/{id}/hide` y `/show`.
- [x] FE: en el detalle (HU-11), promedio con estrellas junto al precio y panel de reseñas con filtro por estrellas. Mientras no exista "Mis pedidos", el formulario "Calificar" está en el panel, para quien puede reseñar.
- [x] FE: en "Mis pedidos" (HU-19), botón "Calificar" en los productos de órdenes entregadas que todavía no reseñé.

**Escenarios**
```gherkin
Escenario: Dejar una reseña
  Dado que mi orden 10 con el producto 15 está ENTREGADA
  Cuando califico el producto 15 con 4 estrellas y el comentario "Muy buena tela"
  Entonces la reseña aparece en el detalle del producto
  Y el promedio del producto se actualiza

Escenario: Reseña sin haber recibido el producto
  Dado que no tengo ninguna orden ENTREGADA con el producto 15
  Cuando intento reseñarlo
  Entonces recibo 403 con "Solo podés reseñar productos que recibiste"

Escenario: Filtrar por estrellas
  Dado que el producto 15 tiene 3 reseñas de 5 estrellas y 2 de 1 estrella
  Cuando filtro por 1 estrella
  Entonces veo solo las 2 reseñas de 1 estrella
```

---

## Épica 3 — Carrito (Redis)

### HU-12 · Carrito de invitado y unión al iniciar sesión
**Prioridad:** Should · **Estado:** Hecha

> Como **invitado** quiero agregar productos al carrito sin registrarme, y conservarlos cuando inicio sesión, para no perder lo que elegí.

**Tareas**
- [x] BE (Redis): carrito de invitado en `cart:guest:{uuid}` (hash `productId → qty`, TTL de 3 días); el front manda el uuid en el header `X-Cart-Id`.
- [x] BE: permitir los endpoints de `/api/cart` sin autenticación cuando viene `X-Cart-Id`.
- [x] BE: `POST /api/cart/merge` al iniciar sesión: suma con `HINCRBY`, recorta al stock disponible y borra la clave del invitado.
- [x] FE: generar y guardar el `cartId` en `localStorage`, llamar al merge después del login y quitar el aviso "Iniciá sesión para agregar al carrito".

**Escenarios**
```gherkin
Escenario: El carrito del invitado pasa a la cuenta
  Dado que como invitado agregué 2 "Remera azul"
  Y en mi cuenta ya tenía 1 "Remera azul"
  Cuando inicio sesión
  Entonces mi carrito tiene 3 "Remera azul"
  Y el carrito de invitado se borra de Redis

Escenario: La unión supera el stock
  Dado que la "Remera azul" tiene stock 2
  Y como invitado tengo 2 y en mi cuenta tenía 1
  Cuando inicio sesión
  Entonces mi carrito queda con 2 "Remera azul"
  Y veo "Ajustamos la cantidad de Remera azul al stock disponible"
```

---

### HU-13 · Cantidades del carrito con una sola regla de stock
**Prioridad:** Must · **Auditoría:** #18 · **Estado:** Hecha

> Como **cliente** quiero cambiar cantidades y ver siempre la verdad sobre el stock para no llevarme sorpresas en el checkout.

**Tareas**
- [x] BE: `CartService` es el **único** que lee y escribe el carrito; el prefijo `cart:` vive en un solo lugar.
- [x] BE: si se pide más que el stock, responder 409 con "Solo quedan N". No recortar en silencio.
- [x] BE: `getCart` marca los ítems inactivos o sin stock como `available: false` en vez de borrarlos sin avisar.
- [x] FE: los ítems no disponibles se muestran atenuados con "No disponible" y el checkout se bloquea hasta quitarlos.
- [x] FE: el selector de cantidad deshabilita "+" al llegar al stock.

**Escenarios**
```gherkin
Escenario: Subir la cantidad dentro del stock
  Dado que tengo 1 "Taza" en el carrito y hay stock 5
  Cuando subo la cantidad a 3
  Entonces el carrito muestra 3 "Taza"
  Y el total se actualiza

Escenario: Pedir más que el stock
  Dado que hay stock 5 de "Taza"
  Cuando intento poner 6
  Entonces recibo 409 con "Solo quedan 5"
  Y la cantidad sigue en la anterior

Escenario: Un producto se desactiva mientras está en el carrito
  Dado que tengo "Gorra roja" en el carrito
  Y el admin la desactiva
  Cuando abro el carrito
  Entonces "Gorra roja" aparece como "No disponible"
  Y el botón de continuar al pago está deshabilitado hasta que la quite
```

---

## Épica 4 — Wishlist y recomendaciones (Neo4j)

### HU-14 · Wishlist que no depende de Neo4j
**Prioridad:** Should · **Auditoría:** #9, #17 · **Estado:** Hecha

> Como **cliente** quiero guardar productos en mi wishlist aunque falle el sistema de recomendaciones, para que una función secundaria no me rompa la principal.

**Tareas**
- [x] BE: publicar `WishlistChanged` y sincronizar Neo4j en un `@TransactionalEventListener(AFTER_COMMIT)` asíncrono.
- [x] BE (Redis): si Neo4j falla, encolar el evento en `neo4j:sync:pending` (lista) y que un job reintente cada minuto.
- [x] BE: `DELETE /api/wishlist` para vaciarla en una sola transacción.
- [x] FE: UI optimista (el corazón se marca enseguida), con rollback y mensaje si falla.

**Escenarios**
```gherkin
Escenario: Agregar a la wishlist
  Cuando agrego "Taza" a mi wishlist
  Entonces aparece en mi wishlist
  Y en Neo4j existe la relación (cliente)-[:WISHLISTED]->(Taza)

Escenario: Neo4j caído
  Dado que Neo4j no responde
  Cuando agrego "Taza" a mi wishlist
  Entonces recibo 200 y "Taza" aparece en mi wishlist
  Y el evento queda en la cola neo4j:sync:pending
  Y cuando Neo4j vuelve, el job crea la relación

Escenario: Falla al vaciar la wishlist
  Dado que tengo 4 productos en la wishlist
  Cuando la vacío y el servidor responde con error
  Entonces la wishlist sigue mostrando los 4 productos
  Y veo "No pudimos vaciar tu wishlist, probá de nuevo"
```

---

### HU-15 · Recomendaciones por compras y wishlist
**Prioridad:** Should · **Estado:** Hecha (falta que el webhook de la HU-18 llame a `markPaid` y que la edición de la HU-21 publique `ProductChanged`)

> Como **cliente** quiero ver productos relacionados con lo que compré y guardé para descubrir cosas que me pueden gustar.

**Tareas**
- [x] BE (Neo4j): al pasar una orden a PAGADA, crear `(:User)-[:PURCHASED {qty, at}]->(:Product)`.
- [x] BE (Neo4j): "Otros también compraron" sobre `PURCHASED`; "Por tu wishlist" sobre `WISHLISTED` + `BELONGS_TO`.
- [x] BE: excluir productos inactivos, sin stock o ya comprados.
- [x] BE (Redis): cachear el resultado en `recs:{userId}` con TTL de 10 minutos e invalidarlo al comprar o cambiar la wishlist.
- [x] BE: sincronizar los nodos de producto al editar nombre o precio (HU-21).
- [x] OPS: comando de backfill que cargue en Neo4j las órdenes y wishlists existentes.
- [x] BE: fallback "Más vendidos" con SQL si no hay historial o Neo4j falla.

**Escenarios**
```gherkin
Escenario: Recomendaciones por compras de otros
  Dado que compré "Taza"
  Y otros 3 clientes que compraron "Taza" también compraron "Plato"
  Cuando entro al detalle de "Taza"
  Entonces en "Otros también compraron" aparece "Plato"

Escenario: Cliente sin historial
  Dado que soy un cliente nuevo sin compras ni wishlist
  Cuando entro al catálogo
  Entonces veo "Más vendidos" en lugar de recomendaciones personalizadas

Escenario: Neo4j caído
  Dado que Neo4j no responde y no hay caché en Redis
  Cuando pido mis recomendaciones
  Entonces recibo 200 con "Más vendidos"
  Y el error queda registrado en los logs
```

---

## Épica 5 — Checkout, pagos y pedidos

### HU-16 · Direcciones con provincia, ciudad y CPA
**Prioridad:** Must · **Auditoría:** #6, #16 · **Estado:** Hecha

> Como **cliente** quiero elegir mi provincia y ciudad de una lista y reutilizar mis direcciones para completar el checkout rápido.

**Tareas**
- [x] BE: `GET /api/states` y `GET /api/cities?stateId=` (públicos y cacheables).
- [x] BE: aceptar el CPA (`^[A-Z]\d{4}[A-Z]{3}$`) o el código viejo de 4 dígitos; normalizar a mayúsculas sin espacios.
- [x] BE: borrar o consultar una dirección de otro usuario → 404 (no revelar que existe).
- [x] FE: selects dependientes provincia → ciudad, lista de direcciones guardadas para elegir y opción "Agregar nueva".
- [x] FE: `autocomplete` correcto en los campos (`given-name`, `family-name`, `street-address`, `postal-code`).

**Escenarios**
```gherkin
Escenario: Cargar y reutilizar una dirección
  Dado que estoy en el checkout
  Cuando elijo "Córdoba" y "Villa Carlos Paz", ingreso "Av. San Martín 123" y el CPA "x5152abc"
  Entonces la dirección se guarda con CPA "X5152ABC"
  Y en mi próxima compra aparece en "Mis direcciones"

Escenario: CPA inválido
  Cuando ingreso el código postal "ABC"
  Entonces veo "Código postal inválido" en el campo
  Y no puedo continuar

Escenario: Dirección de otro cliente
  Dado que la dirección 50 pertenece a otro cliente
  Cuando intento borrar la dirección 50
  Entonces recibo 404
```

---

### HU-17 · Crear la orden sin vender stock que no existe
**Prioridad:** Must · **Auditoría:** #1, #3, #10 · **Estado:** Hecha

> Como **cliente** quiero confirmar mi compra con un total claro y la seguridad de que el producto está reservado para mí.

**Tareas**
- [x] BE: descontar stock de forma atómica: `UPDATE products SET stock = stock - :q WHERE id = :id AND stock >= :q`; si afecta 0 filas → 409 "Sin stock de X". Todo en una transacción. *(Adelantado en la HU-27.)*
- [x] BE: la orden nace en estado `PENDIENTE_PAGO` con `expires_at = now + 30 min`.
- [x] BE: el total lo calcula **solo** el backend; los precios ya incluyen IVA.
- [x] BE: `OrderService` lee el carrito vía `CartService` y lo borra en `afterCommit`.
- [x] FE: quitar el cálculo del 21% del resumen y mostrar "IVA incluido".
- [x] BE (Redis): compra idempotente. `POST /api/orders` exige el header `Idempotency-Key`. `SET order:idem:{userId}:{key} NX EX 86400`: la primera vez crea la orden y guarda su id; si la clave ya existe, devuelve la misma orden (200) sin descontar stock de nuevo, o 409 "Tu compra se está procesando" si la primera todavía no terminó.
- [x] BE (Redis): un solo checkout a la vez por cliente: lock `order:lock:{userId}` (`SET NX EX 30`) mientras se crea la orden, para que dos pestañas no compren el mismo carrito.
- [x] FE: el checkout genera una `Idempotency-Key` (UUID) al entrar y la reusa en cada reintento de esa compra; el botón queda deshabilitado mientras espera. La dirección se guarda una sola vez aunque se reintente.
- [x] QA: test de concurrencia: 2 hilos compran la última unidad → 1 orden y 1 rechazo. *(Adelantado en la HU-27.)*
- [x] QA: la misma compra enviada 2 veces (seguidas y en paralelo) con la misma clave → 1 orden, el stock baja una sola vez y las dos respuestas traen la misma orden. *(En paralelo, la segunda puede recibir 409 "Tu compra se está procesando"; su reintento trae la misma orden.)*

**Escenarios**
```gherkin
Escenario: Orden creada
  Dado que tengo 2 "Taza" a $1.000 en el carrito y hay stock 5
  Cuando confirmo la compra con una dirección mía
  Entonces se crea una orden en PENDIENTE_PAGO con total $2.000
  Y el stock de "Taza" baja a 3
  Y mi carrito queda vacío

Escenario: Dos clientes compran la última unidad
  Dado que "Taza" tiene stock 1
  Y dos clientes la tienen en el carrito
  Cuando ambos confirman la compra al mismo tiempo
  Entonces solo uno obtiene la orden
  Y el otro recibe 409 "Sin stock de Taza"
  Y su carrito queda intacto
  Y el stock final es 0, nunca negativo

Escenario: Falla al guardar la orden
  Dado que el guardado de la orden falla en la base de datos
  Cuando confirmo la compra
  Entonces el stock no cambia
  Y mi carrito sigue intacto

Escenario: Confirmo dos veces la misma compra
  Dado que tengo 1 "Taza" en el carrito
  Cuando confirmo la compra con doble clic, o se corta la conexión y el navegador reintenta
  Entonces se crea una sola orden
  Y el stock de "Taza" baja una sola vez
  Y las dos respuestas me muestran la misma orden
```

---

### HU-18 · Pago con Mercado Pago y estados de la orden
**Prioridad:** Must · **Auditoría:** #2

> Como **cliente** quiero pagar con Mercado Pago y que mi pedido se confirme solo cuando el pago se aprueba.

**Tareas**
- [ ] BE: crear la preferencia de Checkout Pro con los ítems de la orden, `external_reference = orderId` y las `back_urls`.
- [ ] BE: `POST /api/payments/webhook`: validar la firma `x-signature`, consultar el pago en la API de MP y actualizar la orden.
- [ ] BE (Redis): idempotencia con `SET mp:event:{id} NX EX 86400` para no procesar dos veces el mismo aviso.
- [ ] BE: máquina de estados `PENDIENTE_PAGO → PAGADA → ENVIADA → ENTREGADA`, y `PENDIENTE_PAGO|PAGADA → CANCELADA`.
- [ ] BE: job programado cada minuto que cancela las órdenes `PENDIENTE_PAGO` vencidas y devuelve el stock.
- [ ] FE: botón "Pagar con Mercado Pago" (redirect) y páginas de resultado aprobado, pendiente y rechazado que consultan el estado real.
- [ ] OPS: credenciales de sandbox para dev y de producción por variables de entorno.

**Escenarios**
```gherkin
Escenario: Pago aprobado
  Dado que tengo una orden en PENDIENTE_PAGO
  Cuando pago y Mercado Pago envía el webhook con estado "approved"
  Entonces la orden pasa a PAGADA
  Y recibo el email de confirmación
  Y se crean las relaciones PURCHASED en Neo4j

Escenario: Pago rechazado y vencimiento
  Dado que mi pago fue rechazado
  Cuando vuelvo a la tienda
  Entonces veo "Tu pago fue rechazado" con la opción de reintentar
  Y si pasan 30 minutos sin un pago aprobado, la orden pasa a CANCELADA
  Y el stock reservado vuelve a estar disponible

Escenario: Webhook duplicado o falso
  Dado que ya procesé el evento de pago 123
  Cuando llega otra vez el evento 123, o llega uno con firma inválida
  Entonces la orden no cambia
  Y el duplicado responde 200, mientras que el de firma inválida responde 401
```

---

### HU-19 · Mis pedidos
**Prioridad:** Must · **Estado:** Hecha

> Como **cliente** quiero ver mis pedidos y su estado para saber cuándo me llega la compra.

**Tareas**
- [x] BE: `GET /api/orders` paginado y `GET /api/orders/{id}` (solo del dueño); fetch join de ítems para evitar N+1.
- [x] FE: ruta `/account/orders` con la lista (fecha, total, estado) y el detalle.
- [x] FE: la página de orden exitosa lee la orden por id en lugar de `history.state`, así sobrevive a un recargo.

**Escenarios**
```gherkin
Escenario: Ver mis pedidos
  Dado que hice 3 compras
  Cuando entro a "Mis pedidos"
  Entonces veo las 3 órdenes, de la más nueva a la más vieja, con su estado

Escenario: Pedido de otro cliente
  Cuando entro a /account/orders/77 y la orden 77 es de otro cliente
  Entonces veo "Pedido no encontrado"
  Y la API responde 404
```

---

### HU-20 · Emails transaccionales
**Prioridad:** Should · **Estado:** Hecha (el mail de orden enviada espera a que la HU-18 o la HU-23 pasen la orden a ENVIADA)

> Como **cliente** quiero recibir emails cuando compro o cambia el estado de mi pedido para estar al tanto sin entrar a la tienda.

**Tareas**
- [x] BE: Spring Mail + plantillas para bienvenida, orden pagada, orden enviada y recuperación de contraseña.
- [x] BE (Redis): cola `mail:queue` (lista o Stream); un worker envía con hasta 3 reintentos y backoff.
- [x] BE: el envío nunca bloquea ni revierte la operación de negocio.

**Escenarios**
```gherkin
Escenario: Confirmación de compra
  Dado que mi orden pasó a PAGADA
  Cuando el worker procesa la cola
  Entonces recibo un email con el número de orden, los ítems y el total

Escenario: El servidor de email está caído
  Dado que el SMTP no responde
  Cuando mi orden pasa a PAGADA
  Entonces la orden queda PAGADA igual
  Y el email queda en la cola y se reintenta hasta 3 veces
```

---

## Épica 6 — Panel de administración

### HU-21 · Gestión de productos
**Prioridad:** Must · **Auditoría:** #12

> Como **admin** quiero crear, editar, activar y desactivar productos con su imagen para mantener el catálogo al día sin tocar la base de datos.

**Tareas**
- [ ] BE: `@Valid` en todos los request bodies; `PUT /api/admin/products/{id}`; `PATCH .../active`.
- [ ] BE: arreglar `save`, que hoy ignora `imageUrl`.
- [ ] BE: `POST /api/admin/uploads` (solo jpg, png o webp, máximo 2 MB; guardar en un volumen o en un storage tipo S3/Cloudinary).
- [ ] BE: al editar nombre o precio, sincronizar el nodo en Neo4j (vía el evento de la HU-14).
- [ ] FE: rutas `/admin/products` (tabla con búsqueda y filtro activo/inactivo) y `/admin/products/:id` (formulario), con `adminGuard`.

**Escenarios**
```gherkin
Escenario: Crear un producto con imagen
  Dado que soy admin
  Cuando creo "Taza negra" con precio $1.500, stock 10, categoría "Cocina" y una imagen png
  Entonces el producto aparece en el catálogo con su imagen

Escenario: Datos inválidos
  Cuando intento guardar un producto con precio 0 o una imagen de 5 MB
  Entonces recibo 400 con el error en el campo correspondiente
  Y el producto no se guarda

Escenario: Un cliente intenta crear un producto
  Dado que soy un cliente sin rol ADMIN
  Cuando llamo a POST /api/admin/products
  Entonces recibo 403
```

---

### HU-22 · Gestión de categorías
**Prioridad:** Should

> Como **admin** quiero administrar categorías para organizar el catálogo.

**Tareas**
- [ ] BE: CRUD en `/api/admin/categories` con `@Valid`; nombre único sin distinguir mayúsculas.
- [ ] BE: borrar una categoría con productos → 409 con la cantidad de productos.
- [ ] FE: pantalla `/admin/categories` (tabla + modal).

**Escenarios**
```gherkin
Escenario: Crear una categoría
  Cuando creo la categoría "Deco"
  Entonces aparece en el menú de categorías de la tienda

Escenario: Borrar una categoría con productos
  Dado que "Cocina" tiene 12 productos
  Cuando intento borrarla
  Entonces recibo 409 con "La categoría tiene 12 productos; movelos o desactivalos primero"

Escenario: Nombre duplicado
  Dado que existe "Deco"
  Cuando creo "deco"
  Entonces recibo 409 con "Ya existe una categoría con ese nombre"
```

---

### HU-23 · Gestión de órdenes
**Prioridad:** Must

> Como **admin** quiero ver las órdenes y cambiar su estado para gestionar envíos y cancelaciones.

**Tareas**
- [ ] BE: `GET /api/admin/orders?status&from&to&page` y `PATCH /api/admin/orders/{id}/status`, validando la máquina de estados de la HU-18.
- [ ] BE: al cancelar una orden PAGADA, devolver el stock y registrar quién y por qué canceló (la devolución del dinero se hace en MP).
- [ ] BE: tabla `order_status_history` (estado, fecha, usuario).
- [ ] FE: `/admin/orders` con filtros por estado y fecha, detalle con cliente, dirección, ítems e historial.

**Escenarios**
```gherkin
Escenario: Marcar un pedido como enviado
  Dado que la orden 10 está PAGADA
  Cuando la marco como ENVIADA
  Entonces su estado es ENVIADA
  Y el cliente recibe el email "Tu pedido está en camino"
  Y el historial registra el cambio con mi usuario

Escenario: Transición inválida
  Dado que la orden 10 está ENTREGADA
  Cuando intento pasarla a PAGADA
  Entonces recibo 409 con "No se puede pasar de ENTREGADA a PAGADA"

Escenario: Cancelar una orden pagada
  Dado que la orden 11 está PAGADA con 2 "Taza"
  Cuando la cancelo con el motivo "Sin stock real"
  Entonces su estado es CANCELADA
  Y el stock de "Taza" sube en 2
```

---

### HU-24 · Dashboard de métricas de negocio
**Prioridad:** Should

> Como **admin** quiero ver ventas, productos destacados y carritos abandonados para tomar decisiones del negocio.

**Tareas**
- [ ] BE: `GET /api/admin/metrics?from&to` con ventas por día, cantidad de órdenes pagadas, ticket promedio, top 5 productos y productos con stock menor a 5 (SQL agregado sobre órdenes PAGADAS o posteriores).
- [ ] BE (Redis): sorted set `carts:activity` (`ZADD` con timestamp en cada cambio de carrito); un carrito está abandonado si tiene ítems y no se tocó en 24 h. Contarlos con `ZRANGEBYSCORE`, sin `KEYS`.
- [ ] BE (Neo4j): "productos más guardados en wishlist que no se venden" (para promociones).
- [ ] FE: `/admin` con tarjetas de KPI, gráfico de ventas por día y tablas, con selector de rango.

**Escenarios**
```gherkin
Escenario: Ver métricas del mes
  Dado que en noviembre hubo 20 órdenes pagadas por $200.000
  Cuando consulto las métricas del 1 al 30 de noviembre
  Entonces veo ventas $200.000, 20 órdenes y ticket promedio $10.000
  Y veo el top 5 de productos por unidades vendidas

Escenario: Rango sin ventas
  Cuando consulto un rango sin órdenes
  Entonces veo los indicadores en 0 y el mensaje "Sin ventas en este período"
  Y no recibo un error

Escenario: Rango inválido
  Cuando consulto con "desde" posterior a "hasta"
  Entonces recibo 400 con "La fecha de inicio debe ser anterior a la de fin"
```

---

## Épica 7 — Legales y confianza

### HU-25 · Botón de arrepentimiento
**Prioridad:** Must (obligatorio en Argentina, Res. 424/2020)

> Como **cliente** quiero arrepentirme de una compra dentro del plazo legal sin trámites complicados.

**Tareas**
- [ ] BE: `POST /api/orders/{id}/withdrawal` dentro de los 10 días corridos desde la entrega (o la compra, si aún no se entregó); genera un código de trámite y avisa al admin.
- [ ] BE: tabla `withdrawal_requests` (orden, fecha, código, estado).
- [ ] FE: link "Botón de arrepentimiento" visible en el footer y en el detalle del pedido; formulario simple que no exige iniciar sesión (email + número de orden).
- [ ] BE + FE: email al cliente con el código.

**Escenarios**
```gherkin
Escenario: Arrepentimiento dentro del plazo
  Dado que mi orden 20 se entregó hace 3 días
  Cuando envío la solicitud de arrepentimiento
  Entonces recibo un código de trámite en pantalla y por email
  Y el admin ve la solicitud en el panel

Escenario: Fuera de plazo
  Dado que mi orden se entregó hace 15 días
  Cuando envío la solicitud
  Entonces veo "El plazo de 10 días para arrepentirte venció" y los datos de contacto del comercio

Escenario: Orden que no coincide
  Cuando envío un número de orden que no corresponde a mi email
  Entonces veo "No encontramos una compra con esos datos"
```

---

### HU-26 · Información del comercio y páginas legales
**Prioridad:** Must

> Como **invitado** quiero ver quién vende, cómo contactarlo y sus políticas para confiar en la tienda.

**Tareas**
- [ ] BE: `GET /api/store-info` con razón social, CUIT, dirección, WhatsApp, horarios, URL de Data Fiscal y redes; editable desde el admin.
- [ ] FE: páginas `/legal/terminos`, `/legal/privacidad`, `/legal/envios` y `/legal/devoluciones`, con contenido en Markdown.
- [ ] FE: el footer consume `store-info` (el diseño visual queda para la etapa de UI).

**Escenarios**
```gherkin
Escenario: Datos del comercio visibles
  Dado que el admin cargó los datos del comercio
  Cuando entro a cualquier página
  Entonces el footer muestra WhatsApp, horarios, dirección, razón social y el QR de Data Fiscal

Escenario: Dato sin configurar
  Dado que el comercio no cargó la URL de Data Fiscal
  Cuando entro a la tienda
  Entonces el footer no muestra el bloque de Data Fiscal
  Y no aparece ningún texto de ejemplo
```

---

## Épica 8 — Calidad y crecimiento

### HU-27 · Suite de tests en verde y tests del dinero
**Prioridad:** Must · **Auditoría:** #15 · **Estado:** Hecha (salvo los tests del webhook, que llegan con la HU-18)

> Como **desarrollador** quiero tests que fallen cuando se rompe una regla de negocio para cambiar código sin miedo.

**Tareas**
- [x] QA: Testcontainers (Postgres, Redis, Neo4j) para los tests de integración.
- [x] QA: tests de `createOrder` (stock justo, sin stock, concurrencia, dirección ajena, carrito vacío) y de los permisos de los endpoints de admin. *(El webhook todavía no existe: sus tests van en la HU-18.)*
- [x] QA: arreglar o borrar los specs generados del front (hoy fallan 14 de 23) y testear el `ecommerce-store` y el interceptor.
- [x] OPS: cobertura informativa en la CI (sin umbral obligatorio).

**Escenarios**
```gherkin
Escenario: Suite en verde
  Dado el código de la rama main
  Cuando corre la CI
  Entonces pasan todos los tests de backend y frontend

Escenario: Alguien rompe la regla de stock
  Dado que un cambio vuelve a descontar stock sin chequear la cantidad disponible
  Cuando corre la CI
  Entonces falla el test "dos clientes compran la última unidad"
```

---

### HU-28 · Limpieza técnica
**Prioridad:** Could · **Auditoría:** #18, #19, #20

> Como **desarrollador** quiero menos código duplicado y muerto para que cada cambio se haga en un solo lugar.

**Tareas**
- [ ] BE: un mapper por entidad (Product, Address); borrar las copias en `RecommendationService` y `OrderService`.
- [ ] BE: principal propio con `userId` + `@AuthenticationPrincipal`; borrar los 7 `(Long) auth.getCredentials()` y el fallback `parseLong(email)` de `CartController`.
- [ ] BE: parsear el JWT una sola vez por request en `JwtAuthFilter`.
- [ ] BE: borrar `AddressService.findAll` y `deleteAll`.
- [ ] FE: quitar la dependencia `immer`, el signal `title` y los imports sin uso.
- [ ] BE: renombrar el paquete a minúsculas (`com.shopflow`), que es la convención de Java.

**Escenarios**
```gherkin
Escenario: Refactor sin cambios de comportamiento
  Dado que apliqué la limpieza
  Cuando corre la suite de la HU-27
  Entonces todos los tests siguen pasando

Escenario: Endpoint eliminado
  Cuando llamo a GET /api/products/priceRange/1/100
  Entonces recibo 404
```

---

### HU-29 · Analítica de conversión y SEO
**Prioridad:** Could

> Como **dueño del comercio** quiero saber cuánta gente compra y que Google indexe mis productos para vender más.

**Tareas**
- [ ] FE: eventos `view_item`, `add_to_cart`, `begin_checkout` y `purchase` (Plausible o GA4), enviados solo con consentimiento.
- [ ] FE: banner de consentimiento de cookies.
- [ ] FE: SSR o prerender de Angular para el catálogo y el detalle; `title`, `description` y Open Graph por producto.
- [ ] BE: `GET /sitemap.xml` con los productos activos.

**Escenarios**
```gherkin
Escenario: Medir una compra
  Dado que acepté las cookies de analítica
  Cuando completo una compra de $5.000
  Entonces se envía el evento "purchase" con valor 5000 y el id de la orden

Escenario: El usuario rechaza las cookies
  Dado que rechacé las cookies de analítica
  Cuando navego y compro
  Entonces no se envía ningún evento de analítica

Escenario: Compartir un producto
  Cuando comparto el link de un producto por WhatsApp
  Entonces la vista previa muestra su nombre, precio e imagen
```

---

## Uso de Redis y Neo4j en el proyecto (resumen para el portafolio)

| Herramienta | Uso | Historia |
|---|---|---|
| Redis | Carrito de cliente e invitado con TTL (hash) | HU-12, HU-13 |
| Redis | Rate limiting de login (`INCR` + `EXPIRE`) | HU-07 |
| Redis | Tokens de un solo uso con TTL (`GETDEL`): verificación de email y recuperación de contraseña | HU-09, HU-31 |
| Redis | Códigos de ingreso con intentos contados (hash + `HINCRBY`) y dispositivos de confianza con TTL renovable | HU-33 |
| Redis | Idempotencia de webhooks (`SET NX EX`) | HU-18 |
| Redis | Compra idempotente (`order:idem:{userId}:{key}`) y un checkout a la vez por cliente (`order:lock:{userId}`) | HU-17 |
| Redis | Colas de emails y de sincronización con Neo4j (lista o Stream) | HU-14, HU-20 |
| Redis | Caché de recomendaciones | HU-15 |
| Redis | Carritos abandonados (sorted set por actividad) | HU-24 |
| Neo4j | Grafo User–Product–Category con `WISHLISTED` y `PURCHASED` | HU-14, HU-15 |
| Neo4j | Filtrado colaborativo "otros también compraron" | HU-15 |
| Neo4j | Insight "muy deseados, poco vendidos" | HU-24 |
