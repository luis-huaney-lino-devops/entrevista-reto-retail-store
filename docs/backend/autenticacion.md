# Autenticación

Dos audiencias que no se mezclan, y tres flujos de acceso para la primera.

```text
┌──────────────────────────────────────────────────────────────┐
│  CLIENTE  ·  tienda (storefront)                             │
│    · registro con correo + contraseña                        │
│    · acceso con Google                                       │
│    · recuperación de contraseña                              │
│    · verificación de correo                                  │
├──────────────────────────────────────────────────────────────┤
│  ADMINISTRADOR  ·  panel                                     │
│    · usuario + contraseña, y nada más                        │
└──────────────────────────────────────────────────────────────┘
```

**No comparten tabla, ni endpoint de acceso, ni token.** Un token de cliente
jamás abre el panel, ni por un fallo de configuración: no llevan el mismo
emisor ni la misma audiencia, y el filtro del panel exige ambas cosas.

Por qué importa: el patrón habitual —una tabla `usuarios` con un campo `rol`—
hace que **cualquier** fallo en el registro público sea una escalada a
administrador. Si un atacante consigue que el registro escriba `rol=ADMIN`, ya
está dentro. Con tablas separadas no existe ese camino: el registro público
escribe en `clientes`, y `clientes` no da acceso al panel bajo ninguna
circunstancia.

---

## 1. Por qué el administrador no tiene recuperación por correo

Es una decisión, no una omisión (ADR-0008).

El flujo de «he olvidado mi contraseña» es la superficie más usada para tomar
cuentas privilegiadas: basta comprometer el buzón, o el proveedor de correo, o
interceptar el enlace. Para una cuenta de cliente el riesgo es proporcionado —
alguien podría comprar con tu tarjeta. Para una cuenta que edita precios y ve
todas las órdenes, no lo es.

Alternativa cuando un administrador pierde el acceso: otro se lo restablece
desde el panel, o se cambia por configuración y se reinicia. Es más incómodo y
es correcto.

---

## 2. Tokens

### Qué se emite

| | Cliente | Administrador |
| --- | --- | --- |
| Algoritmo | HS256 | HS256 |
| Emisor (`iss`) | `retail-store` | `retail-store` |
| **Audiencia (`aud`)** | **`storefront`** | **`admin`** |
| Vigencia | 15 min | 15 min |
| Refresco | sí, cookie httpOnly, 30 días | sí, cookie httpOnly, 12 horas |
| Claims | `sub` (id), `email`, `nombre` | `sub` (id), `usuario` |

**La audiencia es lo que separa a los dos mundos.** El filtro del panel valida
`aud == "admin"`; el de la tienda, `aud == "storefront"`. Un token de cliente
presentado al panel se rechaza aunque la firma sea válida, porque la firma no es
lo único que se comprueba.

### Por qué acceso corto + refresco largo

Un token de acceso de 15 minutos no se puede revocar, y no hace falta: caduca
solo. El refresco sí se puede revocar, porque está en base de datos.

```text
  Acceso     15 min   en memoria (JS)        no revocable, irrelevante por corto
  Refresco   30 días  cookie httpOnly        revocable, rotativo, no visible a JS
```

El token de acceso vive **en memoria** del navegador, nunca en `localStorage`:
lo que está en `localStorage` lo lee cualquier script que consiga un XSS. El
refresco vive en una cookie `httpOnly` + `Secure` + `SameSite=Lax`, que el
JavaScript no puede leer en absoluto.

### Rotación del refresco

Cada uso de un refresco lo invalida y emite uno nuevo. Si un refresco ya usado
vuelve a presentarse, es señal de que alguien lo copió: se revoca **toda la
familia** de tokens de ese cliente y se le obliga a entrar de nuevo.

```text
  R1 ──usa──▶ R2 ──usa──▶ R3          normal
  R1 ──usa──▶ R2
  R1 ──usa──▶ ✗  detectado             se revoca R1, R2, R3: la sesión entera
```

Es lo que convierte un robo de cookie en una molestia en lugar de un acceso
permanente.

---

## 3. Registro con correo y contraseña

```text
  POST /api/v1/auth/registro
        │
        ├─▶ valida forma (correo, contraseña mínima, nombre)
        ├─▶ ¿el correo ya existe?
        │      sí  ──▶ RESPUESTA IDÉNTICA a la de éxito  (ver abajo)
        │      no  ──▶ crea cliente, emailVerificado = false
        ├─▶ genera token VERIFICAR_EMAIL (24 h), guarda su HASH
        └─▶ encola correo de verificación
                │
                ▼
      El cliente pulsa el enlace
        POST /api/v1/auth/verificar-email  { token }
        └─▶ emailVerificado = true, marca el token como usado
```

**El registro responde lo mismo exista o no el correo.** `202 Accepted` y el
mismo mensaje: «Si el correo es válido, te hemos enviado un enlace». Si
respondiera `409 correo ya registrado`, cualquiera podría comprobar si una
persona tiene cuenta en la tienda probando su correo. Eso es enumeración de
usuarios, y es un problema de privacidad real: revela hábitos de compra.

Cuando el correo ya existe, en lugar del correo de verificación se envía uno
distinto: «alguien intentó registrarse con tu correo; si fuiste tú, entra
aquí». Así el usuario legítimo entiende qué pasó.

### ¿Puede comprar sin verificar el correo?

Sí. Bloquear la compra hasta verificar el correo sacrifica ventas reales por un
riesgo que no existe: el pedido ya lleva un correo de contacto, y el pago —
cuando lo haya — es la verificación de verdad.

Lo que **sí** exige correo verificado: cambiar la contraseña y ver el historial
de pedidos. Ambos dan acceso a datos, y ahí sí importa que el correo sea suyo.

### Política de contraseñas

Mínimo 10 caracteres. **Sin** exigencia de mayúsculas, dígitos ni símbolos.

Eso contradice la costumbre y sigue la recomendación actual (NIST SP 800-63B):
las reglas de composición empujan a la gente a `Password1!`, que es predecible,
mientras que una frase larga es fuerte y memorable. Lo que sí se hace es
rechazar las contraseñas más comunes mediante una lista de bloqueo.

Hash: **BCrypt, coste 12**. Guardado con prefijo de algoritmo (`{bcrypt}`) para
poder subir el coste o cambiar de algoritmo más adelante sin tocar las filas
existentes.

---

## 4. Acceso con Google

Se usa **Authorization Code con PKCE**, no el flujo implícito ni el token de ID
enviado desde el navegador.

```text
  Tienda                Backend                   Google
    │                      │                         │
    │  GET /auth/google ──▶│                         │
    │                      │ genera state + verifier │
    │  ◀── 302 a Google ───│ (state en cookie corta) │
    │                                                │
    │ ──────── el usuario se autentica ─────────────▶│
    │                                                │
    │  ◀──── 302 a /auth/google/callback?code ───────│
    │                      │                         │
    │  GET callback ──────▶│                         │
    │                      │ valida state            │
    │                      │ canjea code ──────────▶ │
    │                      │ ◀──── id_token ──────── │
    │                      │ valida firma, iss,      │
    │                      │ aud, exp, nonce         │
    │  ◀── sesión ─────────│                         │
```

**El canje del código ocurre en el backend**, nunca en el navegador: exige el
`client_secret`, que no puede viajar al cliente. Y el `id_token` se valida
contra las claves públicas de Google (firma, emisor, audiencia, expiración,
nonce) en lugar de confiar en lo que llegue.

El parámetro `state` es obligatorio y se comprueba: es lo que impide que alguien
complete un inicio de sesión ajeno en el navegador de la víctima.

### Vinculación de cuentas: el caso delicado

Qué pasa cuando alguien entra con Google y ese correo ya tiene cuenta con
contraseña.

| Situación | Qué se hace |
| --- | --- |
| No existe cuenta con ese correo | Se crea el cliente, `emailVerificado = true` (Google ya lo verificó), sin contraseña |
| Existe, y Google dice `email_verified = true` | **Se vinculan**: se añade la identidad externa a la cuenta existente |
| Existe, y Google dice `email_verified = false` | **No se vincula.** Se pide entrar con contraseña y vincular desde el perfil |
| Ya existe esa identidad externa | Acceso normal |

La tercera fila es la importante. Si se vinculara con un correo no verificado
por Google, bastaría con crear una cuenta de Google que declare el correo de la
víctima para apropiarse de su cuenta en la tienda. Es un ataque conocido y la
comprobación de `email_verified` es lo único que lo impide.

### Desvincular

Se permite **solo si el cliente tiene contraseña**. Quitar la única forma de
entrar deja la cuenta inaccesible (invariante I-12). Si no tiene, primero se
establece una por correo.

---

## 5. Recuperación de contraseña

```text
  POST /api/v1/auth/recuperar  { email }
        │
        ├─▶ SIEMPRE responde 202 con el mismo mensaje
        │
        ├─ existe el correo ──▶ token RECUPERAR_CONTRASENA (30 min, un solo uso)
        │                       guarda el HASH, encola el correo
        └─ no existe ─────────▶ no hace nada, tarda lo mismo

  POST /api/v1/auth/restablecer  { token, nuevaContrasena }
        ├─▶ hashea el token y lo busca
        ├─▶ ¿existe, no usado, no expirado?
        ├─▶ cambia la contraseña, marca el token como usado
        ├─▶ REVOCA TODAS LAS SESIONES del cliente
        └─▶ envía correo «tu contraseña ha cambiado»
```

Cinco detalles, cada uno por una razón:

**Respuesta idéntica exista o no el correo.** Mismo cuerpo, mismo código, y
tiempo comparable. Si difiriera, el endpoint sería un comprobador de «quién
tiene cuenta aquí».

**Se guarda el hash del token.** El token en claro solo existe en el correo. Con
acceso de lectura a la base no se puede fabricar un enlace válido.

**30 minutos y un solo uso.** Los correos quedan en bandejas durante años; un
enlace de recuperación que sigue funcionando es una puerta abierta.

**Se revocan todas las sesiones.** Si alguien cambia la contraseña es porque
sospecha que alguien más entró. Dejar vivas las sesiones existentes vacía de
sentido el cambio.

**Se avisa del cambio por correo.** Si el cliente no lo pidió, ese correo es la
única señal que va a recibir.

### Límite de intentos

| Endpoint | Límite |
| --- | --- |
| `POST /auth/recuperar` | 3 por correo cada 15 min · 10 por IP cada hora |
| `POST /auth/acceso` | 5 por correo cada 15 min · 20 por IP cada 15 min |
| `POST /auth/registro` | 5 por IP cada hora |

Superado el límite: `429` con `Retry-After`. Sin esto, el endpoint de
recuperación es un generador gratuito de correos a cualquier dirección.

### Cuando no hay contraseña (entró con Google)

Pedir «tu contraseña actual» a quien nunca tuvo una no tiene sentido. Para ese
caso el flujo es **establecer**, no cambiar: se envía un token
`ESTABLECER_CONTRASENA` al correo verificado y se fija la primera contraseña.
La interfaz debe distinguir los dos casos, no mostrar un formulario que el
usuario no puede completar.

---

## 6. Tiempo constante y enumeración

Tres endpoints deben tardar lo mismo tengan o no éxito: acceso, registro y
recuperación.

En el acceso, si el correo no existe **hay que verificar igualmente contra un
hash señuelo**. Sin eso, la respuesta a un correo inexistente vuelve en 2 ms y
la de uno existente en 250 ms (lo que tarda BCrypt), y esa diferencia es un
oráculo perfectamente medible desde fuera.

```java
// El hash señuelo se calcula una vez al arrancar, sobre una contraseña aleatoria.
var hash = cliente != null ? cliente.getHashContrasena() : HASH_SENUELO;
boolean ok = encoder.matches(contrasenaEnviada, hash);
if (!ok || cliente == null) {
    throw new CredencialesInvalidasException();   // mensaje único
}
```

El mensaje es el mismo para correo inexistente, contraseña incorrecta y cuenta
desactivada: `401 CREDENCIALES_INVALIDAS`, «Correo o contraseña incorrectos».

---

## 7. Cierre de sesión

| Acción | Efecto |
| --- | --- |
| `POST /auth/salir` | Revoca **este** refresco y borra la cookie |
| `POST /auth/salir-todo` | Revoca **todos** los refrescos del cliente |
| Cambio de contraseña | Revoca todos (§5) |
| Desactivación del cliente | Revoca todos |

El token de acceso no se revoca: caduca en 15 minutos. Mantener una lista negra
de tokens de acceso obligaría a consultarla en cada petición, que es justo lo
que los tokens sin estado evitan. Quince minutos de ventana es el precio, y está
elegido a conciencia.

---

## 8. Configuración

Nada de esto se versiona. Todo por variable de entorno.

| Variable | Para qué |
| --- | --- |
| `JWT_SECRET` | Firma HS256. Mínimo 32 caracteres o **la aplicación no arranca** |
| `JWT_ACCESS_TTL` | Vigencia del acceso (`15m`) |
| `JWT_REFRESH_TTL` | Vigencia del refresco (`30d` cliente, `12h` admin) |
| `GOOGLE_CLIENT_ID` | |
| `GOOGLE_CLIENT_SECRET` | |
| `GOOGLE_REDIRECT_URI` | Debe coincidir **exactamente** con la consola de Google |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` | Acceso inicial al panel |
| `PASSWORD_RESET_TTL` | `30m` |
| `EMAIL_VERIFICATION_TTL` | `24h` |

`ADMIN_PASSWORD_HASH` es un hash, no una contraseña en claro: así la variable de
entorno no revela el acceso ni aunque alguien lea el entorno del proceso.

---

## 9. Qué queda fuera, y por qué

| Fuera | Motivo |
| --- | --- |
| Segundo factor (2FA/TOTP) | Sin pagos reales no hay nada que proteger que lo justifique. El diseño lo admite sin cambios estructurales. |
| Más proveedores (Facebook, Apple) | La tabla de identidades externas ya es multi-proveedor; añadir uno es configuración, no rediseño. |
| Permisos finos de administrador | Un solo rol de administrador. Con más de un perfil de gestión habría que introducir roles. |
| Sesiones con estado en servidor | JWT sin estado es suficiente y evita almacenamiento compartido entre instancias. |

---

## 10. Lista de revisión

* [ ] ¿El token lleva `aud` y el filtro la comprueba?
* [ ] ¿El acceso vive en memoria y el refresco en cookie `httpOnly`?
* [ ] ¿El refresco rota, y un reúso revoca la familia?
* [ ] ¿Registro, acceso y recuperación responden igual exista o no el correo?
* [ ] ¿Se verifica contra un hash señuelo cuando el correo no existe?
* [ ] ¿Se guarda el **hash** del token de un solo uso?
* [ ] ¿El token de recuperación caduca y se marca como usado?
* [ ] ¿Cambiar la contraseña revoca todas las sesiones y avisa por correo?
* [ ] ¿Se comprueba `email_verified` antes de vincular una cuenta de Google?
* [ ] ¿Se valida el `state` en el retorno de Google?
* [ ] ¿Hay límite de intentos en acceso, registro y recuperación?
* [ ] ¿La aplicación se niega a arrancar sin `JWT_SECRET` válido?
