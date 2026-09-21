# -*- coding: utf-8 -*-
"""
Prueba de extremo a extremo del chat, las notificaciones y la papelera.

Va aparte de `humo-api.py` porque necesita WebSockets y una librería que la
otra no usa: quien solo quiera comprobar la API REST no debería instalar nada.

    pip install websocket-client
    python pruebas/humo-chat.py
"""
import json
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

import websocket

BASE = "http://localhost:8080/api/v1"
WS = "ws://localhost:8080/ws/chat"
PANEL = "http://localhost:5173"
ok = 0
fallos = []


def pedir(metodo, ruta, cuerpo=None, token=None, crudo=None, tipo=None):
    datos = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else crudo
    cabeceras = {"Accept": "application/json"}
    if cuerpo is not None:
        cabeceras["Content-Type"] = "application/json"
    if crudo is not None:
        cabeceras["Content-Type"] = tipo
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    peticion = urllib.request.Request(BASE + ruta, data=datos, headers=cabeceras, method=metodo)
    try:
        with urllib.request.urlopen(peticion) as respuesta:
            texto = respuesta.read().decode("utf-8")
            return respuesta.status, (json.loads(texto) if texto else None)
    except urllib.error.HTTPError as error:
        texto = error.read().decode("utf-8")
        try:
            return error.code, json.loads(texto)
        except json.JSONDecodeError:
            return error.code, {"crudo": texto[:200]}


def revisar(nombre, condicion, detalle=""):
    global ok
    if condicion:
        ok += 1
        print("  OK   " + nombre)
    else:
        fallos.append(nombre + (" | " + str(detalle) if detalle else ""))
        print("  FALLA " + nombre + (" | " + str(detalle)[:300] if detalle else ""))


def esperar(socket, tipo, intentos=6):
    """
    Lee hasta encontrar un frame del tipo pedido.

    Una acción produce varios eventos —el mensaje para el hilo y la
    actualización de la bandeja para el panel—, y el orden entre ellos no es
    contrato. Afirmar sobre «el siguiente frame» haría que la prueba fallara
    por algo que no es un defecto.
    """
    for _ in range(intentos):
        evento = json.loads(socket.recv())
        if evento.get("tipo") == tipo:
            return evento
    raise AssertionError("no llegó ningún frame de tipo " + tipo)


def png(ancho, alto):
    filas = b"".join(b"\x00" + bytes((30, 120, 200)) * ancho for _ in range(alto))

    def trozo(tipo, datos):
        return (struct.pack(">I", len(datos)) + tipo + datos
                + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + trozo(b"IHDR", struct.pack(">IIBBBBB", ancho, alto, 8, 2, 0, 0, 0))
            + trozo(b"IDAT", zlib.compress(filas))
            + trozo(b"IEND", b""))


def pdf(con_javascript=False):
    """PDF mínimo válido. Con contenido activo si se pide, para probar el rechazo."""
    accion = "/OpenAction << /S /JavaScript /JS (app.alert('hola')) >>" if con_javascript else ""
    cuerpo = (
        "%PDF-1.4\n"
        "1 0 obj << /Type /Catalog /Pages 2 0 R " + accion + " >> endobj\n"
        "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
        "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >> endobj\n"
        "trailer << /Root 1 0 R >>\n"
        "%%EOF\n")
    return cuerpo.encode("latin-1")


def multipart(nombre_archivo, contenido, tipo):
    frontera = "----l" + uuid.uuid4().hex
    partes = [
        ("--%s\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"%s\"\r\n"
         "Content-Type: %s\r\n\r\n" % (frontera, nombre_archivo, tipo)).encode("utf-8"),
        contenido,
        ("\r\n--%s--\r\n" % frontera).encode("utf-8"),
    ]
    return b"".join(partes), "multipart/form-data; boundary=" + frontera


# ---------------------------------------------------------------------------
print("=== 1. Sesión y datos de partida ===")
s, sesion = pedir("POST", "/admin/acceso", {"usuario": "admin", "contrasena": "AdminRetail2026!"})
revisar("acceso al panel", s == 200, s)
token = sesion["tokenAcceso"]

s, clientes = pedir("GET", "/admin/clientes", token=token)
revisar("hay clientes con su historial de compras",
        s == 200 and clientes["totalItems"] >= 8 and clientes["items"][0]["ordenes"] > 0,
        (s, clientes.get("totalItems")))
idCliente = clientes["items"][0]["id"]

s, detalle = pedir("GET", "/admin/clientes/%d" % idCliente, token=token)
revisar("el detalle del cliente trae sus órdenes",
        s == 200 and len(detalle["ordenes"]) > 0, s)
revisar("nunca devuelve el hash de contraseña", "hashContrasena" not in json.dumps(detalle))

print("\n=== 2. Chat por WebSocket ===")
s, conversaciones = pedir("GET", "/admin/conversaciones", token=token)
revisar("bandeja de conversaciones", s == 200 and conversaciones["totalItems"] >= 3, s)

# Un hilo con pendientes, no el primero de la lista: la bandeja ordena por
# actividad, asi que cualquier conversacion creada antes por otra prueba se
# pondria delante y esta comprobacion pasaria a hablar de otra cosa.
conPendientes = [c for c in conversaciones["items"] if c["noLeidos"] > 0]
revisar("hay conversaciones sin leer", len(conPendientes) > 0,
        [(c["asunto"], c["noLeidos"]) for c in conversaciones["items"][:5]])
idConv = conPendientes[0]["id"]

s, hilo = pedir("GET", "/admin/conversaciones/%d" % idConv, token=token)
revisar("abrir el hilo devuelve sus mensajes", s == 200 and len(hilo["mensajes"]) >= 3, s)
revisar("abrirlo lo marca leído", hilo["conversacion"]["noLeidos"] == 0,
        hilo["conversacion"]["noLeidos"])
tokenHilo = hilo["tokenAcceso"]
revisar("el panel recibe el token del hilo para el lado cliente", bool(tokenHilo))

s, cuerpo = pedir("POST", "/admin/conversaciones/ticket-ws", {}, token=token)
revisar("ticket de WebSocket", s == 200 and cuerpo["ticket"], (s, cuerpo))
ticket = cuerpo["ticket"]

# El panel se conecta y se suscribe al hilo.
#
# Con Origin, que es como llega de verdad: el apreton de manos de un WebSocket
# no pasa por CORS, pero Spring si comprueba la lista de origenes permitidos.
# Sin esta cabecera la prueba pasaria aunque el panel no pudiera conectarse.
panel = websocket.create_connection(WS + "?ticket=" + urllib.parse.quote(ticket),
                                    origin=PANEL, timeout=8)
hola = esperar(panel, "LISTA")
revisar("el panel conecta desde su origen y se identifica como administrador",
        hola["rol"] == "ADMINISTRADOR", hola)

# Y desde otro origen no, o cualquier pagina abierta en el navegador de un
# administrador podria hablar con su chat.
s, cuerpo = pedir("POST", "/admin/conversaciones/ticket-ws", {}, token=token)
try:
    ajeno = websocket.create_connection(WS + "?ticket=" + urllib.parse.quote(cuerpo["ticket"]),
                                        origin="http://evil.example", timeout=5)
    ajeno.close()
    revisar("un origen no permitido no puede conectarse", False, "conectó")
except Exception:
    revisar("un origen no permitido no puede conectarse", True)

try:
    repetido = websocket.create_connection(WS + "?ticket=" + urllib.parse.quote(ticket), timeout=5)
    repetido.close()
    revisar("un ticket usado no vale dos veces (conexión rechazada)", False, "conectó")
except Exception:
    revisar("un ticket usado no vale dos veces (conexión rechazada)", True)

panel.send(json.dumps({"tipo": "SUSCRIBIR", "conversacionId": idConv}))
confirmacion = esperar(panel, "SUSCRITO")
revisar("el panel se suscribe al hilo", confirmacion["conversacionId"] == idConv, confirmacion)

# El cliente se conecta con el token del hilo y escribe.
cliente = websocket.create_connection(WS + "?conversacion=" + tokenHilo, timeout=8)
holaCliente = esperar(cliente, "LISTA")
revisar("el cliente conecta con el token del hilo", holaCliente["rol"] == "CLIENTE", holaCliente)

cliente.send(json.dumps({"tipo": "MENSAJE", "cuerpo": "¿Me pueden ayudar?"}))
entregado = esperar(panel, "MENSAJE")
revisar("el mensaje del cliente llega al panel en vivo",
        entregado["mensaje"]["cuerpo"] == "¿Me pueden ayudar?", entregado)
revisar("y llega con el autor correcto", entregado["mensaje"]["autor"] == "CLIENTE")
revisar("y con su id ya asignado", entregado["mensaje"]["id"] is not None, entregado["mensaje"])
esperar(cliente, "MENSAJE")  # el eco de su propio mensaje

# El panel responde; le llega al cliente.
panel.send(json.dumps({"tipo": "MENSAJE", "conversacionId": idConv, "cuerpo": "Claro, dime."}))
recibido = esperar(cliente, "MENSAJE")
revisar("la respuesta del panel llega al cliente en vivo",
        recibido["mensaje"]["autor"] == "ADMINISTRADOR", recibido)
esperar(panel, "MENSAJE")  # el eco de su propia respuesta

# El cliente no puede espiar otro hilo.
cliente.send(json.dumps({"tipo": "SUSCRIBIR", "conversacionId": 999}))
rechazo = esperar(cliente, "ERROR")
revisar("el cliente no puede suscribirse a otro hilo", rechazo["code"] == "FORBIDDEN", rechazo)

# Un mensaje vacío se rechaza por el socket, sin cerrar la conexión.
panel.send(json.dumps({"tipo": "MENSAJE", "conversacionId": idConv, "cuerpo": "   "}))
error = esperar(panel, "ERROR")
revisar("un mensaje sin texto ni adjuntos se rechaza", error["code"] == "VALIDATION_ERROR", error)
revisar("y la conexión sigue viva", panel.connected)

# Una conexión sin credencial no entra.
try:
    intruso = websocket.create_connection(WS, timeout=5)
    intruso.close()
    revisar("sin ticket ni token no se puede conectar", False, "conectó")
except Exception:
    revisar("sin ticket ni token no se puede conectar", True)

def enlaceA(notificacion):
    """El id del producto al que apunta una notificacion, o None."""
    enlace = notificacion.get("enlace") or ""
    cola = enlace.rsplit("/", 1)[-1]
    return int(cola) if cola.isdigit() else None


print("\n=== 3. Notificaciones ===")
s, bandeja = pedir("GET", "/admin/notificaciones", token=token)
revisar("la campana tiene pendientes", s == 200 and bandeja["pendientes"] > 0, (s, bandeja))
revisar("el mensaje del cliente generó notificación",
        any(n["tipo"] == "MENSAJE_CLIENTE" for n in bandeja["items"]),
        [n["tipo"] for n in bandeja["items"]])

antes = bandeja["pendientes"]
idNotificacion = bandeja["items"][0]["id"]
s, cuerpo = pedir("POST", "/admin/notificaciones/%d/leida" % idNotificacion, {}, token=token)
revisar("marcar una como leída baja el contador",
        s == 200 and cuerpo["pendientes"] == antes - 1, (s, cuerpo, antes))


def guardarStock(producto, stock):
    """Guarda el producto con otro stock, respetando el resto de sus datos."""
    return pedir("PUT", "/admin/productos/%d" % producto["id"], {
        "nombre": producto["nombre"],
        "descripcionCorta": producto["descripcionCorta"],
        "descripcion": producto["descripcion"],
        "subcategoriaId": producto["subcategoria"]["id"],
        "marcaId": producto["marca"]["id"] if producto["marca"] else None,
        "precio": producto["precio"],
        "precioAnterior": producto["precioAnterior"],
        "stock": stock,
        "destacado": producto["destacado"],
        "imagenIds": [i["archivoId"] for i in producto["imagenes"]],
    }, token=token)


# La prueba monta su propio escenario en vez de confiar en la semilla: si
# diera por hecho que un producto sigue a cero, la segunda ejecución
# fallaría por el estado que dejó la primera.
s, publicados = pedir("GET", "/admin/productos?" + urllib.parse.urlencode(
    {"activo": "true", "tamanoPagina": 1}), token=token)
revisar("hay un producto publicado con el que probar", s == 200 and publicados["items"], s)
s, producto = pedir("GET", "/admin/productos/%d" % publicados["items"][0]["id"], token=token)
idAgotado = producto["id"]

s, _ = guardarStock(producto, 0)
revisar("dejar un producto publicado sin stock", s == 200, s)

s, bandeja2 = pedir("GET", "/admin/notificaciones", token=token)
revisar("se avisa de que está agotado",
        any(n["tipo"] == "STOCK_AGOTADO" and n["severidad"] == "URGENTE"
            and enlaceA(n) == idAgotado for n in bandeja2["items"]),
        [n["titulo"] for n in bandeja2["items"]])

# Reponer cierra el aviso sin que nadie lo lea.
s, _ = guardarStock(producto, 25)
revisar("reponer el stock", s == 200, s)

s, bandeja3 = pedir("GET", "/admin/notificaciones", token=token)
revisar("el aviso de agotado se cierra solo al reponer",
        not any(n["tipo"] == "STOCK_AGOTADO" and enlaceA(n) == idAgotado for n in bandeja3["items"]),
        [n["titulo"] for n in bandeja3["items"]])

# Y por debajo del umbral avisa, pero sin urgencia: todavía se puede vender.
s, _ = guardarStock(producto, 3)
revisar("quedarse con pocas unidades avisa también", s == 200, s)
s, bandeja4 = pedir("GET", "/admin/notificaciones", token=token)
revisar("y ese aviso es de stock bajo, no de agotado",
        any(n["tipo"] == "STOCK_BAJO" and enlaceA(n) == idAgotado for n in bandeja4["items"]),
        [(n["tipo"], n["titulo"]) for n in bandeja4["items"]])

guardarStock(producto, producto["stock"])

print("\n=== 4. Adjuntos: solo imagen o PDF, y revisados ===")
cuerpoMp, tipoMp = multipart("captura.png", png(600, 400), "image/png")
s, adjunto = pedir("POST", "/admin/conversaciones/adjuntos", token=token, crudo=cuerpoMp, tipo=tipoMp)
revisar("adjuntar una imagen la convierte a WebP",
        s == 201 and adjunto["tipoMime"] == "image/webp" and adjunto["esImagen"], (s, adjunto))

cuerpoMp, tipoMp = multipart("factura.pdf", pdf(), "application/pdf")
s, adjuntoPdf = pedir("POST", "/admin/conversaciones/adjuntos", token=token, crudo=cuerpoMp, tipo=tipoMp)
revisar("adjuntar un PDF limpio", s == 201 and adjuntoPdf["tipoMime"] == "application/pdf", (s, adjuntoPdf))

cuerpoMp, tipoMp = multipart("malicioso.pdf", pdf(con_javascript=True), "application/pdf")
s, cuerpo = pedir("POST", "/admin/conversaciones/adjuntos", token=token, crudo=cuerpoMp, tipo=tipoMp)
revisar("un PDF con JavaScript se rechaza",
        s == 422 and cuerpo["code"] == "DANGEROUS_ATTACHMENT", (s, cuerpo.get("code")))
revisar("y dice qué construcción lo delató", cuerpo.get("construccion") is not None, cuerpo)

cuerpoMp, tipoMp = multipart("hoja.xlsx", b"PK\x03\x04" + b"contenido de un zip" * 4, "application/vnd.ms-excel")
s, cuerpo = pedir("POST", "/admin/conversaciones/adjuntos", token=token, crudo=cuerpoMp, tipo=tipoMp)
revisar("cualquier otro formato se rechaza",
        s == 422 and cuerpo["code"] == "UNSUPPORTED_ATTACHMENT_TYPE", (s, cuerpo.get("code")))

cuerpoMp, tipoMp = multipart("virus.pdf", b"MZ\x90\x00 esto es un ejecutable" * 6, "application/pdf")
s, cuerpo = pedir("POST", "/admin/conversaciones/adjuntos", token=token, crudo=cuerpoMp, tipo=tipoMp)
revisar("un ejecutable con extensión .pdf se rechaza (RN-070)",
        s == 422 and cuerpo["code"] == "UNSUPPORTED_ATTACHMENT_TYPE", (s, cuerpo.get("code")))

# Enviar el mensaje con los dos adjuntos válidos.
panel.send(json.dumps({
    "tipo": "MENSAJE", "conversacionId": idConv,
    "cuerpo": "Te adjunto lo que pediste",
    "adjuntoIds": [adjunto["id"], adjuntoPdf["id"]]}))
conAdjuntos = esperar(cliente, "MENSAJE")
revisar("el mensaje llega con sus dos adjuntos",
        len(conAdjuntos["mensaje"]["adjuntos"]) == 2, conAdjuntos["mensaje"]["adjuntos"])

s, cuerpo = pedir("POST", "/admin/conversaciones/%d/mensajes" % idConv,
                  {"cuerpo": "otra vez", "adjuntoIds": [adjunto["id"]]}, token=token)
revisar("un adjunto ya enviado no se puede reutilizar",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

# El PDF se sirve para descargar, no para abrirse dentro de la página.
peticion = urllib.request.Request(adjuntoPdf["url"], method="GET")
with urllib.request.urlopen(peticion) as respuesta:
    cabeceras = dict(respuesta.headers)
revisar("el PDF se sirve con Content-Disposition: attachment",
        cabeceras.get("Content-Disposition") == "attachment", cabeceras.get("Content-Disposition"))
revisar("y con nosniff", cabeceras.get("X-Content-Type-Options") == "nosniff",
        cabeceras.get("X-Content-Type-Options"))

panel.close()
cliente.close()

print("\n=== 5. Eliminación lógica y papelera ===")
sufijo = uuid.uuid4().hex[:6]
s, marca = pedir("POST", "/admin/marcas", {"nombre": "Marca Temporal " + sufijo}, token=token)
revisar("crear una marca para eliminarla", s == 201, s)
idMarca = marca["id"]

s, _ = pedir("DELETE", "/admin/marcas/%d" % idMarca, token=token)
revisar("eliminar devuelve 204", s == 204, s)

s, listado = pedir("GET", "/admin/marcas?" + urllib.parse.urlencode({"texto": "Marca Temporal"}),
                   token=token)
revisar("ya no aparece en el listado",
        all(m["id"] != idMarca for m in listado["items"]), [m["nombre"] for m in listado["items"]])

s, cuerpo = pedir("GET", "/admin/marcas/%d" % idMarca, token=token)
revisar("consultarla por id da 404", s == 404 and cuerpo["code"] == "BRAND_NOT_FOUND",
        (s, cuerpo.get("code")))

s, papelera = pedir("GET", "/admin/papelera", token=token)
eliminada = next((e for e in papelera if e["tipo"] == "marca" and e["id"] == idMarca), None)
revisar("está en la papelera", eliminada is not None, [e["nombre"] for e in papelera[:5]])
revisar("con quién la eliminó", eliminada and eliminada["eliminadoPor"] == "admin",
        eliminada and eliminada["eliminadoPor"])
revisar("y cuándo", eliminada and eliminada["eliminadoEn"] is not None)

# El nombre se libera: se puede volver a crear uno igual.
s, repetida = pedir("POST", "/admin/marcas", {"nombre": "Marca Temporal " + sufijo}, token=token)
revisar("el nombre de lo eliminado se libera", s == 201, (s, repetida))
pedir("DELETE", "/admin/marcas/%d" % repetida["id"], token=token)

s, restaurada = pedir("POST", "/admin/papelera/restaurar", {"tipo": "marca", "id": idMarca}, token=token)
revisar("restaurar desde la papelera", s == 200, (s, restaurada))

s, recuperada = pedir("GET", "/admin/marcas/%d" % idMarca, token=token)
revisar("vuelve a existir", s == 200, s)
revisar("y vuelve desactivada, no se publica sola", recuperada["activa"] is False, recuperada)

s, cuerpo = pedir("POST", "/admin/papelera/restaurar", {"tipo": "orden", "id": 1}, token=token)
revisar("no se puede restaurar lo que no se elimina",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

print("\n=== 6. Eliminar no es lo mismo que desactivar ===")
s, categorias = pedir("GET", "/admin/categorias", token=token)
conHijas = next(c for c in categorias["items"] if c["activa"])
s, cuerpo = pedir("DELETE", "/admin/categorias/%d" % conHijas["id"], token=token)
revisar("no se elimina una categoría con subcategorías activas",
        s == 409 and cuerpo["code"] == "HAS_DEPENDENTS", (s, cuerpo.get("code")))
revisar("y dice qué lo bloquea", len(cuerpo.get("bloqueantes", [])) > 0, cuerpo)

print("\n" + "=" * 60)
print("PASAN %d   FALLAN %d" % (ok, len(fallos)))
for f in fallos:
    print("  - " + f)
sys.exit(1 if fallos else 0)
