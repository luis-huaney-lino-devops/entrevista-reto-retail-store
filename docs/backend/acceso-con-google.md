# Activar el acceso con Google

El código está hecho y probado. Lo único que falta es una credencial, que no
puede vivir en el repositorio. Esta guía es lo que hay que hacer para que el
botón «Continuar con Google» aparezca y funcione.

**Tiempo: unos diez minutos.** No hace falta tarjeta ni cuenta de pago.

---

## Qué está construido ya

| Pieza | Dónde | Estado |
| --- | --- | --- |
| Endpoint `POST /api/v1/cuenta/google` | `cuenta/web/CuentaControlador.java` | Hecho |
| Verificación de la firma contra el JWKS de Google | `cuenta/servicio/VerificadorTokenGoogle.java` | Hecho |
| Caché de las claves públicas | `cuenta/servicio/ClavesGoogleJwks.java` | Hecho |
| Vinculación con una cuenta existente | `cuenta/servicio/ServicioAccesoGoogle.java` | Hecho |
| Botón en la tienda | `apps/storefront/componentes/cuenta/BotonGoogle.tsx` | Hecho |

Mientras no haya credencial:

- La API responde **`503 GOOGLE_NOT_CONFIGURED`** con un mensaje que dice
  exactamente qué falta.
- La tienda **no pinta el botón**. Es deliberado: enseñar un botón que va a
  fallar es peor que no tenerlo, porque quien lo pulsa cree que el fallo es
  suyo.

---

## 1. Crear el proyecto en Google Cloud

1. Entra en <https://console.cloud.google.com/> con una cuenta de Google.
2. Arriba, en el selector de proyecto, **Nuevo proyecto**. Ponle un nombre
   (`retail-store`, por ejemplo) y créalo.
3. Espera a que el selector muestre el proyecto nuevo antes de seguir.

## 2. Configurar la pantalla de consentimiento

Es lo que verá la persona cuando Google le pregunte si te deja entrar.

1. Menú lateral → **APIs y servicios** → **Pantalla de consentimiento de OAuth**.
2. Tipo de usuario: **Externo**. (**Interno** solo existe si tienes Google
   Workspace, y limita el acceso a tu propia organización.)
3. Rellena lo obligatorio: nombre de la aplicación, correo de soporte, correo
   del desarrollador.
4. **Ámbitos**: no añadas ninguno. Los tres que necesitas —`openid`, `email`,
   `profile`— los pide Identity Services por su cuenta y no requieren
   revisión de Google.
5. **Usuarios de prueba**: mientras la aplicación esté «En pruebas», solo
   podrán entrar los correos que añadas aquí. Añade el tuyo. Para abrirlo a
   cualquiera hay que pulsar **Publicar aplicación**; con estos tres ámbitos
   no hace falta que Google la verifique.

## 3. Crear el ID de cliente

1. **APIs y servicios** → **Credenciales** → **Crear credenciales** →
   **ID de cliente de OAuth**.
2. Tipo de aplicación: **Aplicación web**.
3. **Orígenes autorizados de JavaScript** — aquí es donde se equivoca todo el
   mundo. Va el origen de **la tienda**, no el de la API, y **sin barra final**:

   ```
   http://localhost:3000
   ```

   En producción, añade también el dominio real (`https://tienda.midominio.com`).

4. **URI de redirección autorizados**: **déjalo vacío**. Esta integración usa
   Google Identity Services, que devuelve el token en el propio navegador
   mediante una ventana emergente; no hay redirección que autorizar. Si algún
   día se migra al flujo de Authorization Code, entonces sí hará falta.

5. Crea y copia el **ID de cliente**. Tiene esta forma:

   ```
   123456789012-a1b2c3d4e5f6g7h8i9j0.apps.googleusercontent.com
   ```

   **El «secreto de cliente» que también te da no se usa en esta integración.**
   No lo pongas en ninguna variable: no hace falta, y un secreto que no se usa
   es un secreto que se filtra sin que nadie note la pérdida.

---

## 4. Ponerlo en las dos mitades

El **mismo** ID de cliente va en los dos sitios. No son dos credenciales: es
una, que el navegador usa para pedir el token y la API para comprobar que ese
token es para ella.

### API

```bash
GOOGLE_CLIENT_ID=123456789012-a1b2c3d4e5f6g7h8i9j0.apps.googleusercontent.com
```

En desarrollo, exportándola antes de arrancar:

```bash
export GOOGLE_CLIENT_ID="123456789012-....apps.googleusercontent.com"
cd backend/java && DB_PUERTO_HOST=5433 mvn spring-boot:run
```

```powershell
$env:GOOGLE_CLIENT_ID = '123456789012-....apps.googleusercontent.com'
```

En `deploy/docker-compose.dev.yml` va como variable de entorno del servicio de
la API. **No la escribas en `application.yml`**: ese archivo sí viaja en el
repositorio.

### Tienda

En `apps/storefront/.env.local` (que no se versiona):

```
NEXT_PUBLIC_GOOGLE_CLIENT_ID=123456789012-....apps.googleusercontent.com
```

Y **reinicia `npm run dev`**: Vite y Next leen las variables al arrancar, no en
cada recarga.

> El prefijo `NEXT_PUBLIC_` significa que acaba dentro del JavaScript que
> descarga el navegador. Aquí está bien: el ID de cliente es público por
> diseño, cualquiera puede leerlo en el HTML de cualquier sitio que use Google.
> Lo que lo protege no es el secreto, es la lista de orígenes autorizados del
> paso 3.

---

## 5. Comprobar que funciona

1. Abre `http://localhost:3000/acceso`. **El botón «Continuar con Google» tiene
   que aparecer.** Si no aparece, la variable de la tienda no llegó: revisa que
   el archivo se llame `.env.local` y que reiniciaste el servidor.
2. Pulsa, elige tu cuenta y acepta.
3. Deberías acabar dentro, en `/mi-cuenta`, con tu nombre.

Y desde la línea de comandos, que la API ya no dice que le falta configuración:

```bash
curl -s -X POST http://localhost:8080/api/v1/cuenta/google \
  -H "Content-Type: application/json" -d '{"credencial":"esto-no-es-un-token"}'
```

- Sin configurar → `503` con `"code": "GOOGLE_NOT_CONFIGURED"`.
- Configurado → `401` con `"code": "INVALID_PROVIDER_TOKEN"`, que es lo
  correcto: el token es basura, pero la comprobación ya se está haciendo.

---

## Qué hace la API cuando llega el token

Vale la pena saberlo, porque explica por qué algunos casos se rechazan.

1. **Comprueba la firma** contra las claves públicas de Google. Sin esto,
   cualquiera podría fabricar un token diciendo ser quien quisiera; es el
   único paso que de verdad autentica.
2. **Comprueba el `aud`** contra tu ID de cliente. Un token legítimo emitido
   para *otra* aplicación no sirve aquí.
3. **Exige `email_verified`** (RN-064). Google entrega a veces cuentas cuyo
   correo no ha verificado. Aceptarla sería marcar ese correo como verificado
   en nuestra base y, si esa dirección pertenece a otra persona, entregarle su
   cuenta al primero que pase.
4. **Busca por el `sub`**, no por el correo. El `sub` es el identificador
   estable de la cuenta en Google; el correo se puede cambiar.
5. Si no hay identidad con ese `sub` pero **sí una cuenta con ese correo**,
   las **vincula** en vez de crear una segunda. Quien se registró con
   contraseña y luego entra con Google espera encontrar sus pedidos, no una
   cuenta vacía.

---

## Problemas frecuentes

| Síntoma | Causa | Solución |
| --- | --- | --- |
| El botón no se pinta | Falta `NEXT_PUBLIC_GOOGLE_CLIENT_ID` o no se reinició la tienda | Revisa `.env.local` y reinicia `npm run dev` |
| `origin_mismatch` en la ventana de Google | El origen no está en la lista del paso 3, o lo pusiste con barra final | `http://localhost:3000`, sin `/` al final |
| `403 access_denied` | La aplicación está «En pruebas» y ese correo no es usuario de prueba | Añádelo, o publica la aplicación |
| `503 GOOGLE_NOT_CONFIGURED` | La API no recibió `GOOGLE_CLIENT_ID` | Exporta la variable **antes** de arrancar; se lee al iniciar |
| `401 INVALID_PROVIDER_TOKEN` con un token real | El `aud` no coincide: la tienda y la API usan IDs distintos | Tiene que ser el mismo en los dos sitios |
| `422 EMAIL_NOT_VERIFIED_BY_PROVIDER` | La cuenta de Google no tiene el correo verificado | Es correcto que se rechace. Verifícalo en Google |

---

## Producción

- Un **proyecto de Google distinto** para producción, con su propio ID de
  cliente. Compartirlo con desarrollo significa que cualquiera con el entorno
  local puede emitir tokens que la producción acepta.
- En orígenes autorizados, solo **`https://`**. Google permite `http://` para
  `localhost` y para nada más, que es exactamente lo correcto.
- La variable, en el gestor de secretos del despliegue. Nunca en la imagen de
  Docker ni en el repositorio.
- Cuando se publique la aplicación, revisa la pantalla de consentimiento: el
  nombre y el logotipo que pongas ahí son lo que ve la persona en el momento
  de decidir si te da acceso.

---

## Lo que esta integración **no** hace todavía

- **No se puede desvincular Google** de una cuenta. Hace falta el endpoint y,
  con él, el cuidado de RN-065: desvincular a quien no tiene contraseña lo
  dejaría sin forma de entrar.
- **No hay un segundo proveedor.** La tabla `identidad_externa` está preparada
  —`proveedor` es una columna, no una suposición—, pero su `CHECK` solo admite
  `GOOGLE`. Añadir otro es ampliar ese `CHECK` y escribir su verificador.
- **No se refresca el token de Google.** No hace falta: se usa una sola vez,
  para probar quién eres; a partir de ahí la sesión es nuestra.
