# -*- coding: utf-8 -*-
"""Prueba de humo de extremo a extremo contra la API en marcha."""
import json
import struct
import sys
import urllib.error
import urllib.request
import uuid
import zlib

BASE = "http://localhost:8080/api/v1"
ok = 0
fallos = []


def pedir(metodo, ruta, cuerpo=None, token=None, cabeceras=None, cuerpo_crudo=None, tipo=None):
    url = BASE + ruta if ruta.startswith("/") else ruta
    datos = None
    cab = {"Accept": "application/json"}
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode("utf-8")
        cab["Content-Type"] = "application/json"
    if cuerpo_crudo is not None:
        datos = cuerpo_crudo
        cab["Content-Type"] = tipo
    if token:
        cab["Authorization"] = "Bearer " + token
    if cabeceras:
        cab.update(cabeceras)
    pet = urllib.request.Request(url, data=datos, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(pet) as r:
            texto = r.read().decode("utf-8")
            return r.status, (json.loads(texto) if texto else None), dict(r.headers)
    except urllib.error.HTTPError as e:
        texto = e.read().decode("utf-8")
        try:
            return e.code, json.loads(texto), dict(e.headers)
        except json.JSONDecodeError:
            return e.code, {"crudo": texto[:300]}, dict(e.headers)


def revisar(nombre, condicion, detalle=""):
    global ok
    if condicion:
        ok += 1
        print("  OK   " + nombre)
    else:
        fallos.append(nombre + (" | " + str(detalle) if detalle else ""))
        print("  FALLA " + nombre + (" | " + str(detalle)[:300] if detalle else ""))


def png(ancho, alto, color=(40, 120, 200)):
    """PNG minimo, escrito a mano para no depender de Pillow."""
    filas = b"".join(b"\x00" + bytes(color) * ancho for _ in range(alto))

    def trozo(tipo, datos):
        return (struct.pack(">I", len(datos)) + tipo + datos
                + struct.pack(">I", zlib.crc32(tipo + datos) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + trozo(b"IHDR", struct.pack(">IIBBBBB", ancho, alto, 8, 2, 0, 0, 0))
            + trozo(b"IDAT", zlib.compress(filas))
            + trozo(b"IEND", b""))


def multipart(campos, archivo):
    frontera = "----limite" + uuid.uuid4().hex
    partes = []
    for clave, valor in campos.items():
        partes.append(("--" + frontera + "\r\n"
                       + 'Content-Disposition: form-data; name="%s"\r\n\r\n' % clave
                       + valor + "\r\n").encode("utf-8"))
    nombre, contenido, tipo = archivo
    partes.append(("--" + frontera + "\r\n"
                   + 'Content-Disposition: form-data; name="archivo"; filename="%s"\r\n' % nombre
                   + "Content-Type: %s\r\n\r\n" % tipo).encode("utf-8"))
    partes.append(contenido)
    partes.append(("\r\n--" + frontera + "--\r\n").encode("utf-8"))
    return b"".join(partes), "multipart/form-data; boundary=" + frontera


print("=== 1. Catálogo público ===")
s, productos, cab = pedir("GET", "/productos?tamanoPagina=5")
revisar("GET /productos devuelve 200", s == 200, s)
revisar("la semilla trae un catálogo con fondo",
        productos and productos["totalItems"] >= 28,
        productos.get("totalItems") if productos else None)
revisar("la página trae 5 elementos", productos and len(productos["items"]) == 5)
revisar("el resumen NO expone el SKU", productos and "sku" not in productos["items"][0],
        list(productos["items"][0].keys()) if productos else None)
revisar("los campos JSON están en español",
        productos and {"precio", "precioAnterior", "hayStock"} <= set(productos["items"][0]))
revisar("toda respuesta lleva identificador de correlación", "X-Correlation-Id" in cab)

s, menu, _ = pedir("GET", "/categorias")
revisar("GET /categorias devuelve las categorías activas", s == 200 and len(menu) >= 6,
        len(menu) if menu else s)
revisar("cada categoría trae sus subcategorías", menu and len(menu[0]["subcategorias"]) >= 3,
        len(menu[0]["subcategorias"]) if menu else None)

# El producto sale del listado y no de una constante: la prueba es del
# endpoint, no de que la semilla contenga un artículo concreto.
elegido = productos["items"][0]["slug"]
s, detalle, _ = pedir("GET", "/productos/" + elegido)
revisar("detalle por slug", s == 200 and detalle["slug"] == elegido, s)
revisar("el detalle trae las 4 variantes de imagen",
        detalle and detalle["imagenes"]
        and {"miniatura", "tarjeta", "detalle", "original"} <= set(detalle["imagenes"][0]),
        detalle["imagenes"][:1] if detalle else None)
revisar("el detalle trae la ruta categoría/subcategoría",
        detalle and detalle["categoria"]["slug"] and detalle["subcategoria"]["slug"],
        (detalle.get("categoria"), detalle.get("subcategoria")) if detalle else None)

s, relacionados, _ = pedir("GET", "/productos/" + elegido + "/relacionados")
revisar("relacionados excluye el producto actual",
        s == 200 and all(p["slug"] != elegido for p in relacionados))

s, cuerpo, _ = pedir("GET", "/productos?precioMinimo=500&precioMaximo=100")
revisar("rango de precios invertido -> 422 INVALID_PRICE_RANGE",
        s == 422 and cuerpo["code"] == "INVALID_PRICE_RANGE", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("GET", "/productos?orden=inventado")
revisar("orden desconocido -> 400 VALIDATION_ERROR",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("GET", "/productos?texto=%25")
# Lo que dice RN-026 es que el comodín se escapa, no que no haya resultados:
# si algún producto lleva un «%» en el texto —«Malla de 90%»— encontrarlo es
# justo lo correcto. Lo que no puede pasar es que salga el catálogo entero.
revisar("buscar '%' no devuelve el catálogo entero (RN-026)",
        s == 200 and cuerpo["totalItems"] < productos["totalItems"],
        (cuerpo.get("totalItems"), productos["totalItems"]) if s == 200 else s)

s, cuerpo, _ = pedir("GET", "/productos/no-existe")
revisar("slug inexistente -> 404 PRODUCT_NOT_FOUND",
        s == 404 and cuerpo["code"] == "PRODUCT_NOT_FOUND", (s, cuerpo.get("code")))
revisar("el error trae correlationId", cuerpo.get("correlationId") is not None)

print("\n=== 2. Acceso al panel ===")
s, cuerpo, _ = pedir("GET", "/admin/productos")
revisar("panel sin token -> 401 UNAUTHENTICATED",
        s == 401 and cuerpo["code"] == "UNAUTHENTICATED", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/admin/acceso", {"usuario": "admin", "contrasena": "equivocada"})
revisar("contraseña incorrecta -> 401 INVALID_CREDENTIALS",
        s == 401 and cuerpo["code"] == "INVALID_CREDENTIALS", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/admin/acceso", {"usuario": "nadie", "contrasena": "equivocada"})
revisar("usuario inexistente da el MISMO código (RN-067)",
        s == 401 and cuerpo["code"] == "INVALID_CREDENTIALS", (s, cuerpo.get("code")))

s, sesion, cab = pedir("POST", "/admin/acceso", {"usuario": "admin", "contrasena": "AdminRetail2026!"})
revisar("acceso correcto -> 200", s == 200, (s, sesion))
token = sesion["tokenAcceso"] if s == 200 else None
revisar("devuelve token de acceso y caducidad", token and sesion["expiraEnSegundos"] == 900)
revisar("NO devuelve el refresco en el cuerpo", "tokenRefresco" not in sesion)
cookie = cab.get("Set-Cookie", "")
revisar("el refresco viaja en cookie httpOnly",
        "refresco_panel=" in cookie and "HttpOnly" in cookie, cookie[:120])
revisar("la cookie es SameSite=Lax y de ruta /api/v1/admin",
        "SameSite=Lax" in cookie and "Path=/api/v1/admin" in cookie, cookie[:120])
refresco = cookie.split("refresco_panel=")[1].split(";")[0] if "refresco_panel=" in cookie else None

s, cuerpo, _ = pedir("GET", "/admin/administradores/yo", token=token)
revisar("GET /admin/administradores/yo con token", s == 200 and cuerpo["usuario"] == "admin", (s, cuerpo))
revisar("nunca devuelve el hash de contraseña", "hashContrasena" not in cuerpo and "contrasena" not in cuerpo)

s, cuerpo, _ = pedir("GET", "/admin/productos", token="esto.no.es.un.token")
revisar("token ilegible -> 401", s == 401 and cuerpo["code"] == "UNAUTHENTICATED", (s, cuerpo.get("code")))

print("\n=== 3. Rotación del refresco ===")
s, sesion2, cab2 = pedir("POST", "/admin/refrescar", {}, cabeceras={"Cookie": "refresco_panel=" + refresco})
revisar("refrescar emite una sesión nueva", s == 200 and sesion2["tokenAcceso"], (s, sesion2))
refresco2 = cab2.get("Set-Cookie", "").split("refresco_panel=")[1].split(";")[0]
revisar("el refresco rota (token nuevo distinto)", refresco2 != refresco)

s, cuerpo, _ = pedir("POST", "/admin/refrescar", {}, cabeceras={"Cookie": "refresco_panel=" + refresco})
revisar("reusar el refresco anterior -> 401", s == 401, (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/admin/refrescar", {}, cabeceras={"Cookie": "refresco_panel=" + refresco2})
revisar("la reutilización revoca la familia entera", s == 401, (s, cuerpo.get("code")))

print("\n=== 4. Alta de catálogo desde el panel ===")
sufijo = uuid.uuid4().hex[:6]

s, marca, _ = pedir("POST", "/admin/marcas", {"nombre": "Marca Prueba " + sufijo}, token=token)
revisar("crear marca -> 201", s == 201, (s, marca))
revisar("el slug se deriva del nombre", marca and marca["slug"].startswith("marca-prueba-"), marca)

s, cuerpo, _ = pedir("POST", "/admin/marcas", {"nombre": "Marca Prueba " + sufijo}, token=token)
revisar("marca con nombre repetido -> 409 DUPLICATE_NAME",
        s == 409 and cuerpo["code"] == "DUPLICATE_NAME", (s, cuerpo.get("code")))

s, categoria, _ = pedir("POST", "/admin/categorias",
                        {"nombre": "Categoría Prueba " + sufijo, "orden": 99}, token=token)
revisar("crear categoría -> 201", s == 201, (s, categoria))

s, subcategoria, _ = pedir("POST", "/admin/subcategorias",
                           {"categoriaId": categoria["id"], "nombre": "Sub Prueba", "orden": 1}, token=token)
revisar("crear subcategoría -> 201", s == 201, (s, subcategoria))
revisar("el slug de la subcategoría incluye la categoría",
        subcategoria and "categoria-prueba" in subcategoria["slug"], subcategoria)

s, cuerpo, _ = pedir("POST", "/admin/subcategorias",
                     {"categoriaId": categoria["id"], "nombre": "Sub Prueba", "orden": 2}, token=token)
revisar("nombre repetido dentro de la categoría -> 409",
        s == 409 and cuerpo["code"] == "DUPLICATE_NAME", (s, cuerpo.get("code")))

# La misma prueba crea su propia pareja de categorías para comprobarlo: usar
# una de la semilla haría que la segunda ejecución chocara con lo que dejó la
# primera.
s, otraCategoria, _ = pedir("POST", "/admin/categorias",
                            {"nombre": "Categoría Gemela " + sufijo, "orden": 98}, token=token)
s, otra, _ = pedir("POST", "/admin/subcategorias",
                   {"categoriaId": otraCategoria["id"], "nombre": "Sub Prueba", "orden": 9}, token=token)
revisar("el mismo nombre SÍ se admite en otra categoría", s == 201, (s, otra))

print("\n=== 5. Producto ===")
nuevo = {
    "sku": "PRB-" + sufijo.upper(),
    "nombre": "Producto de Prueba " + sufijo,
    "descripcionCorta": "Creado por la prueba de humo.",
    "descripcion": "Producto creado automáticamente para verificar el alta.",
    "subcategoriaId": subcategoria["id"],
    "marcaId": marca["id"],
    "precio": 149.90,
    "precioAnterior": 199.90,
    "stock": 7,
    "destacado": False,
    "imagenIds": [],
}
s, producto, _ = pedir("POST", "/admin/productos", nuevo, token=token)
revisar("crear producto -> 201", s == 201, (s, producto))
revisar("nace inactivo", producto and producto["activo"] is False, producto)
revisar("calcula el porcentaje de descuento", producto and producto["porcentajeDescuento"] == 25,
        producto.get("porcentajeDescuento") if producto else None)
revisar("el panel SÍ ve el SKU", producto and producto["sku"] == nuevo["sku"])
revisar("registra quién lo creó", producto and producto["creadoPor"] == "admin",
        producto.get("creadoPor") if producto else None)

s, cuerpo, _ = pedir("POST", "/admin/productos", nuevo, token=token)
revisar("SKU repetido -> 409 DUPLICATE_SKU",
        s == 409 and cuerpo["code"] == "DUPLICATE_SKU", (s, cuerpo.get("code")))

malo = dict(nuevo, sku="OTRO-" + sufijo.upper(), precio=200.00, precioAnterior=100.00)
s, cuerpo, _ = pedir("POST", "/admin/productos", malo, token=token)
revisar("precio anterior menor -> 422 INVALID_COMPARE_PRICE",
        s == 422 and cuerpo["code"] == "INVALID_COMPARE_PRICE", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("PATCH", "/admin/productos/%d/estado" % producto["id"], {"activo": True}, token=token)
revisar("publicar sin imagen -> 422 PRODUCT_REQUIRES_IMAGE",
        s == 422 and cuerpo["code"] == "PRODUCT_REQUIRES_IMAGE", (s, cuerpo.get("code")))

# Destacar desde la lista: un PATCH con un booleano, no el producto entero.
s, destacado, _ = pedir("PATCH", "/admin/productos/%d/destacado" % producto["id"],
                        {"destacado": True}, token=token)
revisar("destacar en portada", s == 200 and destacado["destacado"] is True, (s, destacado))
revisar("y no toca nada más del producto",
        destacado["nombre"] == producto["nombre"] and destacado["stock"] == producto["stock"],
        (destacado.get("nombre"), destacado.get("stock")))
s, quitado, _ = pedir("PATCH", "/admin/productos/%d/destacado" % producto["id"],
                      {"destacado": False}, token=token)
revisar("y quitarlo de destacados", s == 200 and quitado["destacado"] is False, (s, quitado))

s, cuerpo, _ = pedir("POST", "/admin/productos",
                     dict(nuevo, sku="X-" + sufijo.upper(), precio=-5), token=token)
revisar("precio negativo -> 400 VALIDATION_ERROR con el campo",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

print("\n=== 6. Subida de imagen ===")
cuerpo_mp, tipo_mp = multipart({"textoAlt": "Imagen de prueba"},
                              ("prueba.png", png(600, 400), "image/png"))
s, archivo, _ = pedir("POST", "/admin/archivos", token=token, cuerpo_crudo=cuerpo_mp, tipo=tipo_mp)
revisar("subir PNG -> 201", s == 201, (s, archivo))
revisar("se convierte a WebP con 4 variantes",
        archivo and set(archivo["urls"]) == {"MINIATURA", "TARJETA", "DETALLE", "ORIGINAL"},
        archivo.get("urls") if archivo else None)
revisar("no amplía: el original conserva 600 px",
        archivo and archivo["ancho"] == 600, archivo.get("ancho") if archivo else None)

s, repetido, _ = pedir("POST", "/admin/archivos", token=token, cuerpo_crudo=cuerpo_mp, tipo=tipo_mp)
revisar("subir la misma imagen deduplica (mismo id)",
        s == 201 and repetido["id"] == archivo["id"], (s, repetido.get("id"), archivo.get("id")))

cuerpo_mp2, tipo_mp2 = multipart({"textoAlt": "Demasiado pequeña"},
                                ("chica.png", png(50, 50), "image/png"))
s, cuerpo, _ = pedir("POST", "/admin/archivos", token=token, cuerpo_crudo=cuerpo_mp2, tipo=tipo_mp2)
revisar("imagen de 50x50 -> 422 IMAGE_TOO_SMALL",
        s == 422 and cuerpo["code"] == "IMAGE_TOO_SMALL", (s, cuerpo.get("code")))

cuerpo_mp3, tipo_mp3 = multipart({"textoAlt": "No es imagen"},
                                ("falsa.png", b"esto no es una imagen, solo texto plano" * 3, "image/png"))
s, cuerpo, _ = pedir("POST", "/admin/archivos", token=token, cuerpo_crudo=cuerpo_mp3, tipo=tipo_mp3)
revisar("texto con extensión .png -> 422 UNSUPPORTED_IMAGE_TYPE (RN-070)",
        s == 422 and cuerpo["code"] == "UNSUPPORTED_IMAGE_TYPE", (s, cuerpo.get("code")))

print("\n=== 7. Publicar y ver en la tienda ===")
s, actualizado, _ = pedir("PUT", "/admin/productos/%d" % producto["id"],
                          dict(nuevo, imagenIds=[archivo["id"]]), token=token)
revisar("asignar imagen al producto", s == 200 and len(actualizado["imagenes"]) == 1, (s, actualizado))

s, publicado, _ = pedir("PATCH", "/admin/productos/%d/estado" % producto["id"], {"activo": True}, token=token)
revisar("publicar con imagen -> 200 y activo", s == 200 and publicado["activo"] is True, (s, publicado))

s, visto, _ = pedir("GET", "/productos/" + producto["slug"])
revisar("ya se ve en la tienda", s == 200 and visto["slug"] == producto["slug"], s)

s, _, _ = pedir("PATCH", "/admin/productos/%d/estado" % producto["id"], {"activo": False}, token=token)
revisar("despublicar -> 200", s == 200, s)
s, cuerpo, _ = pedir("GET", "/productos/" + producto["slug"])
revisar("despublicado -> 404 en la tienda", s == 404, s)
s, cuerpo, _ = pedir("GET", "/admin/productos/%d" % producto["id"], token=token)
revisar("pero sigue existiendo en el panel", s == 200 and cuerpo["activo"] is False, (s, cuerpo))

print("\n=== 8. Dependencias (RN-011) ===")
s, _, _ = pedir("PATCH", "/admin/productos/%d/estado" % producto["id"], {"activo": True}, token=token)
s, cuerpo, _ = pedir("PATCH", "/admin/subcategorias/%d/estado" % subcategoria["id"],
                     {"activo": False}, token=token)
revisar("desactivar subcategoría con productos activos -> 409 HAS_DEPENDENTS",
        s == 409 and cuerpo["code"] == "HAS_DEPENDENTS", (s, cuerpo.get("code")))
revisar("el error dice cuántos y cuáles bloquean",
        cuerpo.get("totalBloqueantes") == 1 and len(cuerpo.get("bloqueantes", [])) == 1, cuerpo)

s, cuerpo, _ = pedir("PATCH", "/admin/categorias/%d/estado" % categoria["id"], {"activo": False}, token=token)
revisar("desactivar categoría con subcategorías activas -> 409",
        s == 409 and cuerpo["code"] == "HAS_DEPENDENTS", (s, cuerpo.get("code")))

print("\n=== 9. Carrito y cupones ===")
s, carrito, _ = pedir("POST", "/carritos")
revisar("crear carrito -> 201 con UUID", s == 201 and len(carrito["id"]) == 36, (s, carrito))
idc = carrito["id"]

s, carrito, _ = pedir("POST", "/carritos/%s/items" % idc, {"productoId": producto["id"], "cantidad": 2})
revisar("agregar al carrito", s == 200 and carrito["totalUnidades"] == 2, (s, carrito))
revisar("el subtotal lo calcula el servidor",
        carrito and carrito["subtotal"] == round(producto["precio"] * 2, 2),
        (carrito.get("subtotal") if carrito else None, producto["precio"]))
# El identificador de la línea lo asigna la base al insertar: sin volcar antes
# de mapear salía null, y con null no hay con qué llamar a PATCH ni a DELETE.
revisar("la línea nueva vuelve con su id, no con null",
        carrito and carrito["items"] and carrito["items"][0]["id"] is not None,
        carrito["items"][0] if carrito and carrito["items"] else None)

s, cuerpo, _ = pedir("POST", "/carritos/%s/items" % idc, {"productoId": producto["id"], "cantidad": 50})
revisar("superar el stock acumulado -> 409 INSUFFICIENT_STOCK",
        s == 409 and cuerpo["code"] == "INSUFFICIENT_STOCK", (s, cuerpo.get("code")))
revisar("el error dice cuánto hay disponible", cuerpo.get("disponible") == 7, cuerpo)

s, cuerpo, _ = pedir("POST", "/carritos/%s/cupon" % idc, {"codigo": "BIENVENIDA10"})
revisar("aplicar cupón del 10%", s == 200 and cuerpo["descuento"] == 29.98, (s, cuerpo.get("descuento")))
revisar("el total descuenta", cuerpo.get("total") == 269.82, cuerpo.get("total"))

s, cuerpo, _ = pedir("POST", "/carritos/%s/cupon" % idc, {"codigo": "EXPIRADO"})
revisar("cupón caducado -> 422 COUPON_NOT_APPLICABLE",
        s == 422 and cuerpo["code"] == "COUPON_NOT_APPLICABLE", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/carritos/%s/cupon" % idc, {"codigo": "VERANO25"})
revisar("cupón con mínimo no alcanzado -> 422 COUPON_MIN_NOT_MET",
        s == 422 and cuerpo["code"] == "COUPON_MIN_NOT_MET", (s, cuerpo.get("code")))
revisar("el error dice el subtotal actual y el mínimo",
        cuerpo.get("subtotalActual") == 299.80 and cuerpo.get("subtotalMinimo") == 300.00, cuerpo)

# El id 1 no es necesariamente un producto con stock: se busca uno que lo tenga.
s, conStock, _ = pedir("GET", "/productos?conStock=true&precioMinimo=200&tamanoPagina=1")
otroId = conStock["items"][0]["id"]
s, cuerpo, _ = pedir("POST", "/carritos/%s/items" % idc, {"productoId": otroId, "cantidad": 1})
revisar("agregar un segundo producto con stock", s == 200, (s, cuerpo))
s, cuerpo, _ = pedir("GET", "/carritos/" + idc)
revisar("el cupón sigue aplicado y ahora activo", cuerpo.get("cuponAplicado") == "BIENVENIDA10", cuerpo)

s, cuerpo, _ = pedir("GET", "/carritos/" + str(uuid.uuid4()))
revisar("carrito inexistente -> 404 CART_NOT_FOUND",
        s == 404 and cuerpo["code"] == "CART_NOT_FOUND", (s, cuerpo.get("code")))

print("\n=== 10. Permisos por rol ===")
s, cuerpo, _ = pedir("POST", "/admin/administradores",
                     {"usuario": "operador" + sufijo, "contrasena": "unaFraseLargaYSegura",
                      "nombre": "Operador", "rol": "ADMINISTRADOR"}, token=token)
revisar("superadministrador puede crear administradores", s == 201, (s, cuerpo))

s, sesion3, _ = pedir("POST", "/admin/acceso",
                      {"usuario": "operador" + sufijo, "contrasena": "unaFraseLargaYSegura"})
revisar("el nuevo administrador puede entrar", s == 200, (s, sesion3))
token_op = sesion3["tokenAcceso"] if s == 200 else None

s, cuerpo, _ = pedir("GET", "/admin/administradores", token=token_op)
revisar("un ADMINISTRADOR no lista administradores -> 403 FORBIDDEN",
        s == 403 and cuerpo["code"] == "FORBIDDEN", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("GET", "/admin/productos", token=token_op)
revisar("pero sí gestiona catálogo", s == 200, s)

s, cuerpo, _ = pedir("POST", "/admin/administradores",
                     {"usuario": "corta" + sufijo, "contrasena": "corta", "nombre": "X"}, token=token)
revisar("contraseña de menos de 10 caracteres -> 400",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

print("\n=== 11. CORS para el panel ===")


def preflight(origen):
    pet = urllib.request.Request(
        BASE + "/admin/acceso",
        method="OPTIONS",
        headers={"Origin": origen, "Access-Control-Request-Method": "POST",
                 "Access-Control-Request-Headers": "content-type"})
    try:
        with urllib.request.urlopen(pet) as r:
            return r.status, dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers)


s, cab = preflight("http://localhost:5173")
revisar("preflight del panel -> 200", s == 200, s)
revisar("permite el origen del panel",
        cab.get("Access-Control-Allow-Origin") == "http://localhost:5173",
        cab.get("Access-Control-Allow-Origin"))
revisar("permite credenciales (cookie de refresco)",
        cab.get("Access-Control-Allow-Credentials") == "true",
        cab.get("Access-Control-Allow-Credentials"))
revisar("expone X-Correlation-Id al JavaScript",
        "X-Correlation-Id" in (cab.get("Access-Control-Expose-Headers") or ""),
        cab.get("Access-Control-Expose-Headers"))

s, _ = preflight("http://origen.no.autorizado")
revisar("un origen desconocido se rechaza", s == 403, s)

print("\n=== 12. Órdenes ===")
s, listado, _ = pedir("GET", "/admin/ordenes?tamanoPagina=5", token=token)
revisar("listado de órdenes", s == 200 and listado["totalItems"] > 0,
        (s, listado.get("totalItems")))
revisar("ordenadas de la más reciente a la más antigua",
        listado["items"][0]["creadoEn"] >= listado["items"][-1]["creadoEn"])

s, pendientes, _ = pedir("GET", "/admin/ordenes?estado=PENDIENTE", token=token)
revisar("filtro por estado", s == 200 and all(o["estado"] == "PENDIENTE" for o in pendientes["items"]),
        s)

# A donde se puede ir desde cada estado. Es la misma tabla que EstadoOrden, y
# repetirla aqui es el sentido de la prueba: si alguien cambia la maquina de
# estados sin querer, esto lo dice.
SIGUIENTES = {
    "PENDIENTE": ["CANCELADA", "PAGADA"],
    "PAGADA": ["CANCELADA", "ENVIADA"],
    "ENVIADA": ["ENTREGADA"],
}

# Cualquier orden viva, no forzosamente una PENDIENTE.
#
# La prueba mueve una orden hasta cancelarla, asi que cada ejecucion consume
# una: exigir que fuera PENDIENTE la hacia fallar en cuanto se agotaban las de
# la semilla, y no hay checkout todavia que cree mas. El pozo de ordenes
# abiertas es finito; se repone recreando la base con `down -v`.
s, abiertas, _ = pedir("GET", "/admin/ordenes?tamanoPagina=100", token=token)
vivas = [o for o in abiertas["items"] if o["estado"] in SIGUIENTES]
revisar("queda alguna orden sin cerrar con la que probar", len(vivas) > 0,
        "solo hay órdenes entregadas o canceladas; recrea la base para reponer la demo")

idOrden = vivas[0]["id"]
estadoInicial = vivas[0]["estado"]
# El salto invalido: dos pasos por delante del estado actual.
SALTO = {"PENDIENTE": "ENTREGADA", "PAGADA": "ENTREGADA", "ENVIADA": "PAGADA"}
s, detalle, _ = pedir("GET", "/admin/ordenes/%d" % idOrden, token=token)
revisar("detalle con líneas", s == 200 and len(detalle["items"]) > 0, s)
revisar("las líneas copian nombre, sku y precio",
        {"nombreProducto", "sku", "precioUnitario"} <= set(detalle["items"][0]))
revisar("los importes cuadran con las líneas",
        abs(sum(l["totalLinea"] for l in detalle["items"]) - detalle["subtotal"]) < 0.01,
        (detalle["subtotal"], [l["totalLinea"] for l in detalle["items"]]))
revisar("las transiciones ofrecidas son las de su estado",
        sorted(detalle["transicionesPermitidas"]) == SIGUIENTES[estadoInicial],
        (estadoInicial, detalle["transicionesPermitidas"]))

# Desde la orden se llega al cliente y al chat: sin el id, el panel solo puede
# enseñar un nombre escrito a mano en el pedido.
revisar("la orden dice quién la hizo",
        detalle["clienteId"] is not None and detalle["clienteNombre"], detalle.get("clienteNombre"))
revisar("y si ya hay un hilo sobre ella, cuál es",
        "conversacionId" in detalle, sorted(detalle))

s, hilo, _ = pedir("POST", "/admin/conversaciones",
                   {"clienteId": detalle["clienteId"], "ordenId": idOrden,
                    "asunto": "Sobre la orden " + detalle["numero"],
                    "mensaje": "Hola, te escribo por tu pedido."}, token=token)
revisar("abrir una conversación desde la orden", s == 201, (s, hilo))
s, detalle2, _ = pedir("GET", "/admin/ordenes/%d" % idOrden, token=token)
revisar("y la orden ya apunta a ese hilo",
        detalle2["conversacionId"] == hilo["conversacion"]["id"],
        (detalle2.get("conversacionId"), hilo["conversacion"]["id"]))

s, cuerpo, _ = pedir("PATCH", "/admin/ordenes/%d/estado" % idOrden,
                     {"estado": SALTO[estadoInicial]}, token=token)
revisar("saltarse un paso -> 422 INVALID_ORDER_TRANSITION",
        s == 422 and cuerpo["code"] == "INVALID_ORDER_TRANSITION", (s, cuerpo.get("code")))
revisar("el error dice el estado actual y a dónde sí se puede ir",
        cuerpo.get("estadoActual") == estadoInicial
        and sorted(cuerpo.get("transicionesPermitidas", [])) == SIGUIENTES[estadoInicial],
        cuerpo)

# Un paso legítimo, el que toque desde donde esté.
siguiente = [e for e in SIGUIENTES[estadoInicial] if e != "CANCELADA"][0]
s, avanzada, _ = pedir("PATCH", "/admin/ordenes/%d/estado" % idOrden, {"estado": siguiente}, token=token)
revisar("avanzar un paso", s == 200 and avanzada["estado"] == siguiente, (s, avanzada))
revisar("registra quién la movió", avanzada.get("actualizadoPor") == "admin",
        avanzada.get("actualizadoPor"))

# Cancelar devuelve el stock: se compara el del producto antes y después.
# Desde ENVIADA ya no se puede cancelar, así que ese tramo solo corre cuando la
# orden quedó en un estado que lo admite.
if "CANCELADA" in SIGUIENTES.get(siguiente, []):
    idProducto = detalle["items"][0]["productoId"]
    cantidad = detalle["items"][0]["cantidad"]
    s, antes, _ = pedir("GET", "/admin/productos/%d" % idProducto, token=token)
    s, cancelada, _ = pedir("PATCH", "/admin/ordenes/%d/estado" % idOrden,
                            {"estado": "CANCELADA"}, token=token)
    revisar("cancelar una orden ya pagada", s == 200 and cancelada["estado"] == "CANCELADA", s)
    s, despues, _ = pedir("GET", "/admin/productos/%d" % idProducto, token=token)
    revisar("cancelar devuelve el stock (RN-055)",
            despues["stock"] == antes["stock"] + cantidad,
            (antes["stock"], cantidad, despues["stock"]))

    s, cuerpo, _ = pedir("PATCH", "/admin/ordenes/%d/estado" % idOrden,
                         {"estado": "PAGADA"}, token=token)
    revisar("una cancelada ya no revive -> 422", s == 422, (s, cuerpo.get("code")))

print("\n=== 13. Vistas de producto ===")
# Un producto cualquiera de la tienda, buscado en el panel por su SKU.
s, enTienda, _ = pedir("GET", "/productos?tamanoPagina=1")
slugFicha = enTienda["items"][0]["slug"]
s, enPanel, _ = pedir("GET", "/admin/productos?" + urllib.parse.urlencode(
    {"texto": enTienda["items"][0]["nombre"]}), token=token)
skuFicha = enPanel["items"][0]["sku"]

s, ficha, _ = pedir("GET", "/productos/" + slugFicha)
s, antesVistas, _ = pedir("GET", "/admin/productos?" + urllib.parse.urlencode({"texto": skuFicha}),
                          token=token)
vistasAntes = antesVistas["items"][0]["vistas"]
pedir("GET", "/productos/" + slugFicha)
pedir("GET", "/productos/" + slugFicha)
s, despuesVistas, _ = pedir("GET", "/admin/productos?" + urllib.parse.urlencode({"texto": skuFicha}),
                            token=token)
revisar("abrir la ficha en la tienda suma vistas",
        despuesVistas["items"][0]["vistas"] == vistasAntes + 2,
        (vistasAntes, despuesVistas["items"][0]["vistas"]))
revisar("la tienda NO expone el contador", "vistas" not in ficha, list(ficha)[:12])

print("\n=== 14. Tablero ===")
s, m, _ = pedir("GET", "/admin/metricas", token=token)
revisar("métricas en una sola petición", s == 200, s)
revisar("trae las seis secciones",
        {"resumen", "ventasPorDia", "ordenesPorEstado", "masVistos", "masVendidos", "stockBajo"} <= set(m))
revisar("la serie de ventas es densa: 31 días seguidos",
        len(m["ventasPorDia"]) == 31, len(m["ventasPorDia"]))
fechas = [p["fecha"] for p in m["ventasPorDia"]]
revisar("sin huecos ni repeticiones en las fechas", len(set(fechas)) == 31)
revisar("incluye los cinco estados aunque alguno esté vacío",
        len(m["ordenesPorEstado"]) == 5, len(m["ordenesPorEstado"]))
revisar("el resumen cuadra con la serie",
        abs(sum(p["total"] for p in m["ventasPorDia"]) - m["resumen"]["ventas30Dias"]) < 0.01,
        (sum(p["total"] for p in m["ventasPorDia"]), m["resumen"]["ventas30Dias"]))
revisar("las ventas excluyen las canceladas",
        m["resumen"]["ventas30Dias"] > 0 and m["resumen"]["ordenes30Dias"] > 0, m["resumen"])
revisar("hay productos más vistos, con su imagen",
        len(m["masVistos"]) > 0 and m["masVistos"][0]["vistas"] > 0, m["masVistos"][:1])
revisar("los más vistos vienen de mayor a menor",
        all(a["vistas"] >= b["vistas"] for a, b in zip(m["masVistos"], m["masVistos"][1:])))
revisar("stock bajo solo lista productos con menos de 10",
        all(p["stock"] < 10 for p in m["stockBajo"]), m["stockBajo"])

s, cuerpo, _ = pedir("GET", "/admin/metricas")
revisar("el tablero exige sesión -> 401", s == 401, s)

print("\n=== 15. Eliminar no es despublicar (RN-086) ===")
s, _, _ = pedir("DELETE", "/admin/productos/%d" % producto["id"], token=token)
revisar("DELETE -> 204", s == 204, s)
s, cuerpo, _ = pedir("GET", "/admin/productos/%d" % producto["id"], token=token)
revisar("ya no aparece en el panel", s == 404, s)
s, cuerpo, _ = pedir("GET", "/productos/" + producto["slug"])
revisar("ni en la tienda", s == 404, s)

s, papelera, _ = pedir("GET", "/admin/papelera", token=token)
fila = next((e for e in papelera if e["tipo"] == "producto" and e["id"] == producto["id"]), None)
revisar("sigue en la papelera, no se perdió", fila is not None,
        [e["nombre"] for e in papelera[:5]])
revisar("y consta quién lo eliminó", fila and fila["eliminadoPor"] == "admin", fila)

print("\n=== 16. Opiniones públicas (RN-090 … RN-095) ===")
# El escenario lo deja la semilla: opiniones derivadas de órdenes ENTREGADAS,
# con su insignia, y productos sin ninguna. Aquí solo se lee -escribir exige
# sesión de cliente y eso lo cubre humo-cuenta.py-, así que esta sección no
# deja nada detrás y se puede repetir tantas veces como haga falta.
s, mejores, _ = pedir("GET", "/productos?orden=calificacion&tamanoPagina=48")
conOpiniones = next((p for p in mejores["items"] if (p.get("calificacionConteo") or 0) >= 2), None)
revisar("la semilla deja productos con opiniones reales", conOpiniones is not None,
        [(p["slug"], p.get("calificacionConteo")) for p in mejores["items"][:3]])

s, ultima, _ = pedir("GET", "/productos?orden=calificacion&tamanoPagina=48&pagina=%d"
                     % mejores["totalPaginas"])
sinOpiniones = next((p for p in ultima["items"] if (p.get("calificacionConteo") or 0) == 0), None)

if conOpiniones:
    slugOp = conOpiniones["slug"]
    s, opiniones, cab = pedir("GET", "/productos/%s/opiniones" % slugOp)
    revisar("GET /productos/{slug}/opiniones sin sesión -> 200", s == 200, s)
    revisar("viene paginado con el mismo sobre que el catálogo",
            {"items", "pagina", "tamanoPagina", "totalItems", "totalPaginas"} <= set(opiniones),
            list(opiniones))
    revisar("toda respuesta lleva identificador de correlación", "X-Correlation-Id" in cab)

    primera = opiniones["items"][0]
    revisar("cada opinión trae nota, título, cuerpo y fecha",
            {"calificacion", "titulo", "cuerpo", "creadoEn", "compraVerificada", "autor"} <= set(primera),
            list(primera))
    revisar("la nota está entre 1 y 5 (RN-095)", 1 <= primera["calificacion"] <= 5,
            primera["calificacion"])
    revisar("el autor sale abreviado: nombre y letra del apellido",
            primera["autor"].count(" ") <= 1, primera["autor"])
    revisar("un endpoint público NO expone el correo ni el id del cliente",
            "email" not in primera and "clienteId" not in primera and "cliente" not in primera,
            list(primera))

    revisar("el total de opiniones cuadra con el conteo del producto (RN-093)",
            opiniones["totalItems"] == conOpiniones["calificacionConteo"],
            (opiniones["totalItems"], conOpiniones["calificacionConteo"]))

    s, ficha, _ = pedir("GET", "/productos/" + slugOp)
    desglose = ficha.get("desgloseCalificacion")
    revisar("la ficha trae el desglose por estrellas, de 5 a 1",
            desglose and [d["estrellas"] for d in desglose] == [5, 4, 3, 2, 1], desglose)
    revisar("vienen las cinco barras aunque alguna valga cero",
            desglose and len(desglose) == 5, len(desglose) if desglose else None)
    revisar("las barras suman exactamente el conteo",
            sum(d["cantidad"] for d in desglose) == ficha["calificacionConteo"],
            (sum(d["cantidad"] for d in desglose), ficha["calificacionConteo"]))
    esperado = round(sum(d["estrellas"] * d["cantidad"] for d in desglose)
                     / max(1, ficha["calificacionConteo"]), 1)
    revisar("y el promedio es esa media, no un número escrito a mano (RN-093)",
            abs(float(ficha["calificacionPromedio"]) - esperado) <= 0.05,
            (ficha["calificacionPromedio"], esperado))
    revisar("el porcentaje lo calcula el servidor",
            all(0 <= d["porcentaje"] <= 100 for d in desglose), desglose)

    s, paginada, _ = pedir("GET", "/productos/%s/opiniones?tamanoPagina=1" % slugOp)
    s2, segunda, _ = pedir("GET", "/productos/%s/opiniones?tamanoPagina=1&pagina=2" % slugOp)
    revisar("la paginación tiene orden total: la página 2 no repite la 1",
            paginada["items"][0]["id"] != segunda["items"][0]["id"],
            (paginada["items"][0]["id"], segunda["items"][0]["id"]))

    s, porNota, _ = pedir("GET", "/productos/%s/opiniones?orden=mejores&tamanoPagina=48" % slugOp)
    notas = [o["calificacion"] for o in porNota["items"]]
    revisar("orden=mejores devuelve de mayor a menor nota",
            s == 200 and all(a >= b for a, b in zip(notas, notas[1:])), notas)

    s, cuerpo, _ = pedir("GET", "/productos/%s/opiniones?orden=inventado" % slugOp)
    revisar("un orden desconocido -> 400 VALIDATION_ERROR",
            s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))
    revisar("y el error señala el campo orden",
            cuerpo.get("errors") and cuerpo["errors"][0]["field"] == "orden", cuerpo.get("errors"))

    s, cuerpo, _ = pedir("GET", "/productos/%s/opiniones?tamanoPagina=99" % slugOp)
    revisar("una página de 99 se rechaza, no se recorta (RN-024)",
            s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

# La insignia tiene que existir de verdad en los datos de demostración: si la
# semilla dejara de sembrar órdenes ENTREGADAS, nadie se enteraría.
hayVerificada = False
for p in mejores["items"][:5]:
    s, lista, _ = pedir("GET", "/productos/%s/opiniones?tamanoPagina=48" % p["slug"])
    if s == 200 and any(o["compraVerificada"] for o in lista["items"]):
        hayVerificada = True
        break
revisar("hay opiniones de compra verificada en los datos de demostración (RN-092)", hayVerificada)

if sinOpiniones:
    s, vacias, _ = pedir("GET", "/productos/%s/opiniones" % sinOpiniones["slug"])
    revisar("un producto sin opiniones devuelve una página vacía, no un 404",
            s == 200 and vacias["items"] == [] and vacias["totalItems"] == 0, (s, vacias))
    s, ficha0, _ = pedir("GET", "/productos/" + sinOpiniones["slug"])
    revisar("y su promedio es 0 con conteo 0: cero no es «malo», es «todavía nadie»",
            float(ficha0["calificacionPromedio"]) == 0 and ficha0["calificacionConteo"] == 0,
            (ficha0["calificacionPromedio"], ficha0["calificacionConteo"]))
    revisar("su desglose son cinco ceros, no una lista vacía",
            len(ficha0["desgloseCalificacion"]) == 5
            and all(d["cantidad"] == 0 and d["porcentaje"] == 0 for d in ficha0["desgloseCalificacion"]),
            ficha0["desgloseCalificacion"])

s, cuerpo, _ = pedir("GET", "/productos/no-existe-nada/opiniones")
revisar("las opiniones de un producto inexistente -> 404 PRODUCT_NOT_FOUND",
        s == 404 and cuerpo["code"] == "PRODUCT_NOT_FOUND", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/cuenta/opiniones",
                     {"productoId": 1, "calificacion": 5, "titulo": "Anónima", "cuerpo": "Sin cuenta."})
revisar("opinar sin sesión -> 401: no hay opiniones anónimas (RN-091)",
        s == 401 and cuerpo["code"] == "UNAUTHENTICATED", (s, cuerpo.get("code")))

print("\n" + "=" * 60)
print("PASAN %d   FALLAN %d" % (ok, len(fallos)))
for f in fallos:
    print("  - " + f)
sys.exit(1 if fallos else 0)
