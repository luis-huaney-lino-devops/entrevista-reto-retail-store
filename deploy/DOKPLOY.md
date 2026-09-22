# Despliegue en Dokploy

Un solo servicio de tipo **Compose** levanta los tres procesos de la aplicación
y un nginx de entrada que reparte por dominio. La base de datos **no** se
levanta aquí: ya existe como servicio Postgres de Dokploy y se alcanza por la
red interna.

```
                Internet
                   │
           Traefik (Dokploy)          80/443 · certificados Let's Encrypt
                   │                  los tres dominios apuntan al mismo sitio
                   ▼
            retail-proxy  (nginx)     mira el Host y decide
                   │
      ┌────────────┼────────────┐
      ▼            ▼            ▼
  tienda.*     admin.*       api.*
  retail-      retail-       retail-api
  tienda       panel         (Spring Boot)
  (Next.js)    (nginx+Vite)       │
                                  ▼
                    entrevista-storebasededatos-fjl4k6:5432/store-db
                          (servicio Postgres de Dokploy)
```

Por qué un nginx si Traefik ya sabe enrutar: porque el reparto por dominio
queda **dentro del repositorio**, versionado y reproducible, en vez de en la
configuración de una instancia concreta de Dokploy. Traefik solo termina el TLS
y entrega todo al mismo contenedor.

---

## Antes de empezar

### 1. Los tres dominios tienen que compartir dominio registrable

`tienda.tudominio.com`, `admin.tudominio.com` y `api.tudominio.com`: **sí**.
`mitienda.com` + `panel.otracosa.com`: **no**.

No es estética. La cookie de refresco se emite desde el dominio de la API con
`SameSite=Lax` y sin `Domain` (`CuentaControlador`, `AccesoPanelControlador`).
Bajo un mismo dominio registrable las peticiones son *same-site* y el navegador
la manda; desde otro dominio deja de mandarla y la sesión se cae en cada
recarga, sin ningún error que lo explique.

### 2. DNS

Tres registros `A` al IP del VPS. Los tres, no un comodín en uno solo:

```
tienda.tudominio.com.  A  <IP del VPS>
admin.tudominio.com.   A  <IP del VPS>
api.tudominio.com.     A  <IP del VPS>
```

Que resuelvan **antes** de desplegar: Let's Encrypt valida por HTTP-01 y si el
nombre no resuelve, Traefik reintenta con espera creciente y el sitio se queda
sin certificado un buen rato.

### 3. El repositorio tiene que estar en un Git accesible

Dokploy construye desde un repositorio, no desde tu disco. Ahora mismo este no
tiene remoto (`git remote -v` sale vacío): hay que subirlo a GitHub, GitLab o
un Gitea propio antes de nada.

---

## Crear el servicio en Dokploy

1. En el proyecto, **Create Service → Compose**.
2. **Provider**: el repositorio y la rama (`master`).
3. **Compose Path**: `deploy/docker-compose.dokploy.yml`
4. **Environment**: pega el bloque de variables (abajo) con los valores reales.
5. **Deploy**.

No hace falta añadir dominios en la pestaña *Domains*: las etiquetas de Traefik
ya están en el compose y cubren los tres. Añadirlos por la interfaz genera un
segundo juego de etiquetas para lo mismo.

### Variables

Plantilla completa y comentada en [`dokploy.env.example`](dokploy.env.example).
Lo mínimo:

| Variable | Qué es |
| --- | --- |
| `DOMINIO_TIENDA` `DOMINIO_ADMIN` `DOMINIO_API` | Los tres nombres. Sin `https://` |
| `DB_HOST` `DB_PUERTO` `DB_NOMBRE` `DB_USUARIO` `DB_CLAVE` | Las piezas de la cadena de conexión **interna** del servicio Postgres |
| `JWT_SECRETO` | Mínimo 32 bytes. `openssl rand -base64 48` |

El resto tiene valor por defecto. `DB_CLAVE`, `JWT_SECRETO` y los tres dominios
no: si falta alguno, el despliegue **falla al arrancar** con el nombre de la que
falta, en vez de levantar algo medio roto.

`DB_HOST` es el nombre del contenedor Postgres, el que aparece en la cadena
interna que da Dokploy (`postgresql://usuario:clave@ESTO:5432/base`). Funciona
porque `retail-api` está en `dokploy-network`, la misma red que la base.

---

## Qué pasa en el primer despliegue

Tarda. La construcción compila el backend con Maven y los dos frontends con
npm, y luego Flyway aplica `V001__esquema.sql` y `V002__datos.sql` — medio MB
de datos de semilla— antes de que la API abra el puerto. El `start_period` del
healthcheck es de 3 minutos justamente por eso: con menos, Docker mata el
contenedor a mitad de la migración y el siguiente intento se encuentra la base
a medias.

El repositorio de Maven va en una caché de BuildKit (`--mount=type=cache`), no
en una capa de la imagen: el primer despliegue se baja las dependencias y los
siguientes las reutilizan aunque cambie el `pom.xml`. Si alguien le da a *Clean
Cache* en Dokploy, el siguiente despliegue vuelve a tardar lo que tardó el
primero. No es un fallo.

La base `store-db` se puebla sola: catálogo, categorías y administradores salen
de `V002`. No hay que restaurar ningún volcado.

**La portada sale vacía durante el primer minuto.** No es un fallo, y conviene
saberlo antes de pensar que el despliegue se rompió.

Compose construye *todas* las imágenes antes de arrancar ningún contenedor, así
que durante `next build` la API todavía no existe y cada lectura falla con
`ENOTFOUND retail-api`. El build no se cae porque la tienda envuelve todas esas
llamadas en `tolerante()`, pero lo que quede prerrenderizado se hornea sin
datos.

En la práctica afecta a una sola página. Esto es lo que sale del build:

| Ruta | Cómo se genera | Efecto |
| --- | --- | --- |
| `/` | estática, `revalidate = 60` | **vacía hasta la primera visita pasado el minuto** |
| `/sitemap.xml` | estática, `revalidate = 600` | solo portada y `/productos` hasta que caduque |
| `/productos` | dinámica | siempre al día |
| `/c/[categoria]`, `/productos/[slug]` | `generateStaticParams` devolvió vacío | se generan bajo demanda, al día |

El ISR no regenera antes de que pase su `revalidate`: hasta el minuto sirve la
versión vacía tal cual. Pasado el minuto, la primera visita recibe todavía la
vacía y dispara la regeneración en segundo plano; la siguiente ya viene
completa. Para no dejársela al primer visitante de verdad, después de desplegar:

```bash
sleep 70 && curl -s -o /dev/null https://tienda.tudominio.com/          && sleep 5 && curl -s -o /dev/null https://tienda.tudominio.com/
```

---

## Comprobar que quedó bien

```bash
curl -I https://api.tudominio.com/health        # 200
curl -I https://tienda.tudominio.com/           # 200
curl -I https://admin.tudominio.com/            # 200

# El reparto por Host de verdad: un nombre que no es ninguno de los tres
# tiene que morir sin respuesta (el `default_server` devuelve 444).
curl -sS -H 'Host: cualquiera.com' http://<IP del VPS>/ ; echo "salida=$?"
```

En el navegador, lo que de verdad prueba la cadena entera:

- La portada de la tienda **con fotos**. Si el catálogo sale sin imágenes, el
  problema es `ALMACEN_URL_PUBLICA` o los `remotePatterns` de `next.config.mjs`
  — mira la consola por errores de `/_next/image`.
- Entrar al panel en `/productos` y recargar la página estando dentro: si
  vuelve al login, es la cookie de refresco, y casi siempre es el punto 1 de
  «Antes de empezar».
- Abrir el chat: si el WebSocket no conecta, mira que `CORS_ALLOWED_ORIGINS`
  lleve el dominio desde el que estás. `ConfiguracionWebSocket` usa esa misma
  lista para `setAllowedOrigins`.

---

## Cosas que conviene saber

**Cambiar un dominio obliga a reconstruir, no a reiniciar.** Todo lo que
empieza por `NEXT_PUBLIC_` y `VITE_` se incrusta en el bundle al construir.
Cambiar `DOMINIO_API` en la interfaz y pulsar *Restart* deja a los dos
frontends hablando con el dominio viejo. Hay que **Redeploy**.

**Las migraciones aplicadas no se editan.** Flyway guarda el checksum del
archivo entero; cambiar hasta un comentario de `V001` o `V002` hace que la API
se niegue a arrancar contra una base ya migrada. Lo que toque va en un archivo
nuevo.

**`ALMACEN_TIPO=local` guarda las imágenes en volúmenes del VPS**
(`archivos_productos`, `archivos_chat`). Sobreviven a un redespliegue, pero no
son un respaldo ni se replican. Para algo serio, `ALMACEN_TIPO=r2` y las cuatro
variables `R2_*` (ADR-0009). Las fotos de la semilla van *dentro* de la imagen,
no en el volumen: así una imagen nueva trae sus fotos nuevas sin tocar datos.

**Los nombres de servicio llevan prefijo `retail-`.** En `dokploy-network`
conviven todos los proyectos del VPS; un alias genérico como `api` puede acabar
resolviendo al de otra pila.

**El reparto por dominio vive en
[`nginx/gateway.conf.template`](nginx/gateway.conf.template).** Es una
plantilla: la imagen de nginx corre `envsubst` al arrancar y solo sustituye lo
que empieza por `DOMINIO_`. Para probar la sintaxis sin desplegar:

```bash
docker build -t proxy-test deploy/nginx
docker run --rm -e DOMINIO_TIENDA=a.ej.com -e DOMINIO_ADMIN=b.ej.com \
  -e DOMINIO_API=c.ej.com --entrypoint sh proxy-test \
  -c '/docker-entrypoint.sh nginx -t'
```

---

## Qué pasa con `docker-compose.prod.yml` y el `Caddyfile`

Siguen ahí y son la otra vía: VPS pelado, sin Dokploy, con Caddy haciendo de
proxy y su propio Postgres en un contenedor. No se usan en este despliegue. Si
alguna vez se toca el reparto de dominios, hay que tocar el de los dos o dejar
claro cuál manda.
