# Envío de correo

El correo es la única forma que tiene el sistema de alcanzar a una persona fuera
de la aplicación. Eso lo hace crítico para la seguridad (recuperación de
contraseña) y, a la vez, lo convierte en el punto donde es más fácil colgar una
petición HTTP esperando a un servidor ajeno.

---

## 1. La regla que manda sobre las demás

**El envío nunca ocurre dentro de la petición HTTP.** Se encola y se responde.

```text
  MAL                                  BIEN
  ───                                  ────
  POST /auth/recuperar                 POST /auth/recuperar
    ├─ genera token                      ├─ genera token
    ├─ SMTP.send()  ← 8 s               ├─ encola el envío
    └─ responde                          └─ responde  (30 ms)
                                              │
                                         (en segundo plano)
                                              └─ SMTP.send() con reintentos
```

Con envío en línea, un servidor de correo lento convierte cada recuperación en
8 segundos de espera; uno caído, en un `500` al usuario **aunque el token se
haya creado correctamente**. El usuario entonces reintenta, y se generan tres
tokens para una sola solicitud.

Consecuencia que hay que aceptar: la respuesta «te hemos enviado un correo» es
una promesa de intentarlo, no un acuse de entrega. Es lo correcto — ningún
sistema puede garantizar la entrega en el momento de responder.

---

## 2. Transaccionalidad: el detalle que se rompe

El correo se encola **después de que la transacción confirme**, nunca dentro.

```text
  MAL                                    BIEN
  ───                                    ────
  BEGIN                                  BEGIN
    guarda token                           guarda token
    encola correo   ← ventana            COMMIT
    ... rollback                         encola correo   ← seguro
  (correo enviado con token que          (el correo solo sale si el token
   nunca existió)                         existe de verdad)
```

Si se encola dentro y la transacción revierte, se envía un enlace con un token
que no está en la base: el usuario recibe un correo y el enlace falla. Al revés
el peor caso es no enviar un correo cuyo token sí existe — recuperable pidiendo
otro.

En Spring: `TransactionSynchronizationManager.registerSynchronization(...)` con
`afterCommit`, o publicar un evento con
`@TransactionalEventListener(phase = AFTER_COMMIT)`.

> Contraste deliberado con la auditoría, que se escribe **dentro** de la
> transacción. No es incoherencia: la auditoría es parte del hecho y debe
> revertir con él; el correo es un efecto externo que no se puede deshacer una
> vez enviado. Cada uno va donde su riesgo es menor.

---

## 3. Infraestructura

| Entorno | Transporte | Notas |
| --- | --- | --- |
| Desarrollo | **Mailpit** (`localhost:1025`) | Captura todo, no entrega nada. Interfaz en `localhost:8025` |
| Producción | SMTP del proveedor | Credenciales por variable de entorno |

Mailpit ya está corriendo en el compose de desarrollo. Es lo correcto para esto:
**en desarrollo nunca se envía correo de verdad**. Un `@gmail.com` real en un
dato de prueba, con un servidor real configurado, termina en el buzón de alguien.

```text
  docker compose -f deploy/docker-compose.dev.yml up -d mailpit
  → SMTP  localhost:1025
  → web   http://localhost:8025
```

### Salvaguarda contra envíos accidentales

En cualquier entorno que no sea producción, si `MAIL_ALLOWED_DOMAINS` está
definido solo se envía a esos dominios; el resto se registra y se descarta. Es
la red que evita el accidente clásico: restaurar un volcado de producción en
un entorno de pruebas y notificar a diez mil clientes reales.

---

## 4. Los correos del sistema

| Plantilla | Cuándo | Contiene |
| --- | --- | --- |
| `verificacion-email` | Al registrarse | Enlace con token, 24 h |
| `cuenta-ya-existe` | Registro con un correo ya registrado | Enlace para entrar y para recuperar. **No dice que exista la cuenta a quien lo provocó**, porque solo lo recibe el dueño del buzón |
| `recuperar-contrasena` | Solicitud de recuperación | Enlace con token, 30 min |
| `contrasena-cambiada` | Tras cambiarla | Aviso, sin enlaces de acción |
| `establecer-contrasena` | Cliente de Google que quiere contraseña | Enlace con token |
| `orden-confirmada` | Al crear una orden | Número, líneas, totales |

Cada uno se envía en **texto plano y HTML** (multipart). El texto plano no es
ceremonia: muchos clientes de correo corporativos bloquean HTML, y un correo de
recuperación que llega vacío es un usuario que no puede entrar.

### Qué no lleva un correo, nunca

* Contraseñas, ni nuevas ni temporales.
* El token fuera del enlace (para que no se pueda copiar suelto de una captura).
* Datos personales más allá del nombre.
* Imágenes remotas de terceros — delatan la apertura del correo.

---

## 5. Plantillas

Thymeleaf, en `src/main/resources/templates/email/`. Una plantilla por correo,
con su versión `.txt` junto a la `.html`.

```text
templates/email/
├── layout.html                  cabecera, pie, estilos en línea
├── verificacion-email.html
├── verificacion-email.txt
├── recuperar-contrasena.html
├── recuperar-contrasena.txt
└── ...
```

Los estilos van **en línea**, no en un `<style>`: Gmail descarta las hojas de
estilo. Y la maquetación va con tablas, no con flexbox, por la misma clase de
razón. Es feo y es lo que funciona.

Toda variable se escapa. Un nombre de cliente es entrada de usuario y termina
dentro de un HTML que se envía a un tercero.

---

## 6. Reintentos y fallos

```text
  intento 1 ──✗──▶ espera 1 min
  intento 2 ──✗──▶ espera 5 min
  intento 3 ──✗──▶ espera 15 min
  intento 4 ──✗──▶ se marca FALLIDO y se registra con nivel ERROR
```

Retroceso exponencial, cuatro intentos. Un fallo definitivo **no se le muestra
al usuario**: ya se le respondió. Queda en el log y en la tabla de envíos.

Distinción que importa: un rechazo permanente (`550 buzón no existe`) **no se
reintenta**, porque reintentar contra un buzón inexistente daña la reputación
del dominio remitente. Solo se reintentan los fallos transitorios (timeout,
`4xx` de SMTP).

### Registro de envíos

Una tabla `correos_enviados` con: destinatario, plantilla, estado
(`PENDIENTE`/`ENVIADO`/`FALLIDO`), intentos, último error, instantes. Sin ella,
«no me llegó el correo» es imposible de investigar.

**No guarda el cuerpo**: contiene tokens de un solo uso. Guarda qué plantilla y
a quién.

---

## 7. Configuración

| Variable | Por defecto (dev) | Para qué |
| --- | --- | --- |
| `MAIL_HOST` | `localhost` | |
| `MAIL_PORT` | `1025` | Mailpit |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | vacío | Mailpit no pide credenciales |
| `MAIL_FROM` | `no-responder@retailstore.test` | |
| `MAIL_FROM_NAME` | `Retail Store` | |
| `MAIL_ALLOWED_DOMAINS` | *(vacío = todos)* | Salvaguarda, §3 |
| `APP_PUBLIC_URL` | `http://localhost:3000` | Base de los enlaces del correo |

`APP_PUBLIC_URL` merece atención: **los enlaces nunca se construyen desde la
petición entrante**. Tomar el `Host` de la petición permite envenenarlo, y el
correo de recuperación saldría con un enlace al dominio del atacante llevando
un token válido. Es un ataque conocido y la única defensa es no confiar en esa
cabecera.

---

## 8. Pruebas

| Nivel | Qué verifica |
| --- | --- |
| Unitaria | Que el servicio **encole** el correo correcto con las variables correctas. El transporte es un doble |
| Integración | Que la plantilla renderice sin variables sin resolver y que el enlace apunte a `APP_PUBLIC_URL` |
| Manual | Mailpit: leer el correo real y pulsar el enlace |

Lo que hay que probar de verdad, y suele olvidarse:

* Que **no** se encole nada si la transacción revierte.
* Que el enlace lleve el token en claro y la base solo el hash.
* Que un fallo de SMTP **no** rompa la petición HTTP.
* Que el correo de recuperación se envíe **igual** para un correo inexistente
  (no se envía nada, pero la respuesta y el tiempo son los mismos).

---

## 9. Lista de revisión

* [ ] ¿El envío está fuera de la petición HTTP?
* [ ] ¿Se encola **después** del commit?
* [ ] ¿Un fallo de correo deja intacta la respuesta al usuario?
* [ ] ¿El enlace se construye con `APP_PUBLIC_URL` y no con el `Host` recibido?
* [ ] ¿Hay versión en texto plano?
* [ ] ¿Se escapan las variables en el HTML?
* [ ] ¿Los rechazos permanentes se distinguen de los transitorios?
* [ ] ¿El registro de envíos omite el cuerpo?
* [ ] ¿La salvaguarda de dominios está activa fuera de producción?
