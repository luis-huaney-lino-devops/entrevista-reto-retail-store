# -*- coding: utf-8 -*-
"""Prueba de humo de la identidad del cliente: cuenta, direcciones y favoritos.

Idempotente. Cada ejecucion monta su propio escenario -su cliente, sus
direcciones, sus favoritos- con un sufijo aleatorio, y no da por supuesto nada
de lo que dejo la ejecucion anterior.

Sobre el limite de intentos (RN-068): el registro admite 5 por IP y hora, asi
que una prueba que se ejecute varias veces seguidas desde la misma IP se
bloquearia a si misma. Cada ejecucion se presenta con una X-Forwarded-For
distinta, y la ultima seccion agota el limite a proposito desde una IP propia
para comprobar que existe.
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = "http://localhost:8080/api/v1"
ok = 0
fallos = []

SUFIJO = uuid.uuid4().hex[:8]
# Una IP por ejecucion: el limitador vive en memoria y cuenta por IP.
IP_PRUEBA = "203.0.113." + str(1 + int(SUFIJO[:2], 16) % 250)
EMAIL = "humo-cuenta-%s@ejemplo.pe" % SUFIJO
CONTRASENA = "frase larga de prueba"
CONTRASENA_NUEVA = "otra frase larga distinta"


def pedir(metodo, ruta, cuerpo=None, token=None, cabeceras=None, ip=None):
    url = BASE + ruta if ruta.startswith("/") else ruta
    datos = None
    cab = {"Accept": "application/json", "X-Forwarded-For": ip or IP_PRUEBA}
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode("utf-8")
        cab["Content-Type"] = "application/json"
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


def refresco_de(cabeceras):
    """El valor de la cookie refresco_tienda de un Set-Cookie."""
    cookie = cabeceras.get("Set-Cookie", "")
    if "refresco_tienda=" not in cookie:
        return None
    return cookie.split("refresco_tienda=")[1].split(";")[0]


def con_cookie(valor):
    return {"Cookie": "refresco_tienda=" + valor}


print("=== 1. Ubigeo publico ===")
s, departamentos, cab = pedir("GET", "/ubigeo/departamentos")
revisar("GET /ubigeo/departamentos sin sesion -> 200", s == 200, s)
revisar("trae los 25 departamentos del pais", departamentos and len(departamentos) >= 25,
        len(departamentos) if departamentos else None)
revisar("cada uno es {id, nombre} y nada mas",
        departamentos and set(departamentos[0].keys()) == {"id", "nombre"},
        departamentos[0] if departamentos else None)
revisar("vienen ordenados por nombre",
        departamentos and all(a["nombre"] <= b["nombre"] for a, b in zip(departamentos, departamentos[1:])))
revisar("toda respuesta lleva identificador de correlacion", "X-Correlation-Id" in cab)

departamento = departamentos[0]
s, provincias, _ = pedir("GET", "/ubigeo/departamentos/%d/provincias" % departamento["id"])
revisar("provincias del departamento -> 200 con contenido", s == 200 and len(provincias) >= 1,
        (s, len(provincias) if provincias else None))

provincia = provincias[0]
s, distritos, _ = pedir("GET", "/ubigeo/provincias/%d/distritos" % provincia["id"])
revisar("distritos de la provincia -> 200 con contenido", s == 200 and len(distritos) >= 1,
        (s, len(distritos) if distritos else None))
DISTRITO = distritos[0]["id"]

# Un segundo distrito para comprobar que editar una direccion lo cambia de
# verdad. Si la provincia solo tuviera uno, sirve el mismo.
OTRO_DISTRITO = distritos[1]["id"] if len(distritos) > 1 else DISTRITO

s, vacio, _ = pedir("GET", "/ubigeo/departamentos/9999/provincias")
revisar("un departamento inexistente devuelve lista vacia, no 404", s == 200 and vacio == [], (s, vacio))

print("\n=== 2. Registro (RN-061) ===")
s, cuerpo, _ = pedir("POST", "/cuenta/registro",
                     {"email": EMAIL, "nombre": "Cliente Humo", "contrasena": CONTRASENA,
                      "telefono": "999888777"})
revisar("registro nuevo -> 202", s == 202, (s, cuerpo))

s, repetido, _ = pedir("POST", "/cuenta/registro",
                       {"email": EMAIL, "nombre": "Otro Nombre", "contrasena": "una contrasena distinta"})
revisar("el MISMO correo vuelve a dar 202, no 409 (RN-061)", s == 202, (s, repetido))
revisar("y con el mismo cuerpo: nada distingue un correo nuevo de uno existente",
        repetido == cuerpo, (cuerpo, repetido))

s, cuerpo, _ = pedir("POST", "/cuenta/registro",
                     {"email": "corta-%s@ejemplo.pe" % SUFIJO, "nombre": "X", "contrasena": "corta"})
revisar("contrasena de menos de 10 caracteres -> 400 VALIDATION_ERROR (RN-062)",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))
revisar("y el error senala el campo exacto",
        cuerpo.get("errors") and cuerpo["errors"][0]["field"] == "contrasena", cuerpo.get("errors"))

s, cuerpo, _ = pedir("POST", "/cuenta/registro",
                     {"email": "esto-no-es-un-correo", "nombre": "X", "contrasena": CONTRASENA})
revisar("correo mal formado -> 400 VALIDATION_ERROR",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

print("\n=== 3. Acceso (RN-067) ===")
s, cuerpo, _ = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": "no es esta"})
revisar("contrasena incorrecta -> 401 INVALID_CREDENTIALS",
        s == 401 and cuerpo["code"] == "INVALID_CREDENTIALS", (s, cuerpo.get("code")))
mensaje_malo = cuerpo.get("detail")

s, cuerpo, _ = pedir("POST", "/cuenta/acceso",
                     {"email": "nadie-%s@ejemplo.pe" % SUFIJO, "contrasena": "no es esta"})
revisar("correo inexistente da el MISMO codigo (RN-067)",
        s == 401 and cuerpo["code"] == "INVALID_CREDENTIALS", (s, cuerpo.get("code")))
revisar("y el MISMO mensaje: no hay forma de saber que correos existen",
        cuerpo.get("detail") == mensaje_malo, (mensaje_malo, cuerpo.get("detail")))

s, sesion, cab = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA})
revisar("acceso correcto -> 200", s == 200, (s, sesion))
token = sesion["tokenAcceso"] if s == 200 else None
revisar("devuelve token de acceso y caducidad", token and sesion["expiraEnSegundos"] == 900,
        sesion.get("expiraEnSegundos") if sesion else None)
revisar("devuelve el cliente, no el administrador",
        sesion and sesion.get("cliente", {}).get("email") == EMAIL, sesion.get("cliente") if sesion else None)
revisar("NO devuelve el refresco en el cuerpo", "tokenRefresco" not in sesion)
revisar("NO devuelve el hash de la contrasena",
        "hashContrasena" not in sesion.get("cliente", {}) and "contrasena" not in sesion.get("cliente", {}))

cookie = cab.get("Set-Cookie", "")
revisar("el refresco viaja en cookie httpOnly",
        "refresco_tienda=" in cookie and "HttpOnly" in cookie, cookie[:140])
revisar("la cookie es SameSite=Lax y de ruta /api/v1/cuenta",
        "SameSite=Lax" in cookie and "Path=/api/v1/cuenta" in cookie, cookie[:140])
revisar("la cookie de la tienda NO es la del panel", "refresco_panel" not in cookie, cookie[:140])
refresco = refresco_de(cab)

s, cuerpo, _ = pedir("POST", "/cuenta/acceso",
                     {"email": EMAIL.upper(), "contrasena": CONTRASENA})
revisar("el correo en mayusculas entra en la MISMA cuenta (RN-060)",
        s == 200 and cuerpo["cliente"]["email"] == EMAIL, (s, cuerpo.get("cliente")))

print("\n=== 4. Las dos audiencias no se mezclan ===")
s, cuerpo, _ = pedir("GET", "/cuenta/yo")
revisar("la cuenta sin token -> 401 UNAUTHENTICATED",
        s == 401 and cuerpo["code"] == "UNAUTHENTICATED", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("GET", "/admin/productos", token=token)
revisar("un token de CLIENTE en el panel -> 403 WRONG_AUDIENCE",
        s == 403 and cuerpo["code"] == "WRONG_AUDIENCE", (s, cuerpo.get("code")))

s, panel, _ = pedir("POST", "/admin/acceso", {"usuario": "admin", "contrasena": "AdminRetail2026!"})
token_panel = panel["tokenAcceso"] if s == 200 else None
revisar("el panel sigue dejando entrar al administrador", s == 200, s)

s, cuerpo, _ = pedir("GET", "/cuenta/yo", token=token_panel)
revisar("un token de PANEL en la cuenta -> 403 WRONG_AUDIENCE",
        s == 403 and cuerpo["code"] == "WRONG_AUDIENCE", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("GET", "/cuenta/direcciones", token="esto.no.es.un.token")
revisar("token ilegible -> 401 UNAUTHENTICATED",
        s == 401 and cuerpo["code"] == "UNAUTHENTICATED", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("GET", "/productos?tamanoPagina=1", token=token)
revisar("el catalogo publico NO se rompe por llevar un token de cliente", s == 200, (s, cuerpo))
PRODUCTO = cuerpo["items"][0]["id"] if s == 200 and cuerpo["items"] else None
OTRO_PRODUCTO = None
s, pagina, _ = pedir("GET", "/productos?tamanoPagina=2")
if s == 200 and len(pagina["items"]) > 1:
    OTRO_PRODUCTO = pagina["items"][1]["id"]

print("\n=== 5. Perfil ===")
s, yo, _ = pedir("GET", "/cuenta/yo", token=token)
revisar("GET /cuenta/yo -> 200 con la cuenta propia", s == 200 and yo["email"] == EMAIL, (s, yo))
revisar("trae exactamente los campos del contrato",
        set(yo.keys()) == {"id", "emailVerificado", "email", "nombre", "telefono", "tieneContrasena"},
        list(yo.keys()) if yo else None)
revisar("quien se registro con contrasena la tiene", yo["tieneContrasena"] is True, yo)
revisar("y su correo nace sin verificar: comprar no lo exige (RN-063)",
        yo["emailVerificado"] is False, yo)
revisar("el registro duplicado NO piso el nombre de la cuenta existente (RN-061)",
        yo["nombre"] == "Cliente Humo", yo["nombre"])

s, yo, _ = pedir("PUT", "/cuenta/yo", {"nombre": "Cliente Humo Editado", "telefono": "988777666"}, token=token)
revisar("PUT /cuenta/yo cambia nombre y telefono",
        s == 200 and yo["nombre"] == "Cliente Humo Editado" and yo["telefono"] == "988777666", (s, yo))
revisar("y no cambia el correo, que es la identidad (RN-060)", yo["email"] == EMAIL, yo)

s, cuerpo, _ = pedir("PUT", "/cuenta/yo", {"telefono": "988777666"}, token=token)
revisar("el nombre es obligatorio -> 400", s == 400 and cuerpo["code"] == "VALIDATION_ERROR",
        (s, cuerpo.get("code")))

print("\n=== 6. Direcciones con ubigeo ===")
s, lista, _ = pedir("GET", "/cuenta/direcciones", token=token)
revisar("una cuenta recien creada no tiene direcciones", s == 200 and lista == [], (s, lista))

primera = {"distritoId": DISTRITO, "etiqueta": "Casa", "destinatario": "Cliente Humo",
           "telefono": "999888777", "calle": "Av. Los Olivos", "numero": "123",
           "referencia": "Piso 4", "codigoPostal": "15074",
           "latitud": -12.0464, "longitud": -77.0428, "predeterminada": False}
s, d1, _ = pedir("POST", "/cuenta/direcciones", primera, token=token)
revisar("crear direccion -> 201", s == 201, (s, d1))
revisar("la PRIMERA direccion queda predeterminada aunque se pida que no",
        d1 and d1["predeterminada"] is True, d1)
revisar("la respuesta trae los tres niveles del ubigeo resueltos",
        d1 and d1["distrito"]["nombre"] and d1["provincia"]["nombre"] and d1["departamento"]["nombre"],
        (d1.get("distrito"), d1.get("provincia"), d1.get("departamento")) if d1 else None)
revisar("y conserva las coordenadas", d1 and float(d1["latitud"]) == -12.0464, d1.get("latitud") if d1 else None)

segunda = dict(primera, etiqueta="Oficina", calle="Jr. Union", numero="456",
               latitud=None, longitud=None, predeterminada=False)
s, d2, _ = pedir("POST", "/cuenta/direcciones", segunda, token=token)
revisar("crear una segunda -> 201", s == 201, (s, d2))
revisar("la segunda NO se queda con la marca de predeterminada",
        d2 and d2["predeterminada"] is False, d2)
revisar("sin coordenadas, los dos campos vienen nulos",
        d2 and d2["latitud"] is None and d2["longitud"] is None, (d2.get("latitud"), d2.get("longitud")))

s, cuerpo, _ = pedir("POST", "/cuenta/direcciones",
                     dict(primera, latitud=-12.0464, longitud=None), token=token)
revisar("media coordenada no se guarda: o van las dos o ninguna",
        s == 201 and cuerpo["latitud"] is None and cuerpo["longitud"] is None,
        (s, cuerpo.get("latitud"), cuerpo.get("longitud")))
d3 = cuerpo
pedir("DELETE", "/cuenta/direcciones/%d" % d3["id"], token=token)

s, marcada, _ = pedir("POST", "/cuenta/direcciones/%d/predeterminada" % d2["id"], token=token)
revisar("marcar la segunda como predeterminada -> 200", s == 200 and marcada["predeterminada"] is True,
        (s, marcada))

s, lista, _ = pedir("GET", "/cuenta/direcciones", token=token)
revisar("solo hay UNA predeterminada", sum(1 for d in lista if d["predeterminada"]) == 1,
        [(d["etiqueta"], d["predeterminada"]) for d in lista])
revisar("y la predeterminada viene primero", lista[0]["predeterminada"] is True,
        [(d["etiqueta"], d["predeterminada"]) for d in lista])
revisar("la anterior quedo desmarcada",
        next(d for d in lista if d["id"] == d1["id"])["predeterminada"] is False, lista)

s, editada, _ = pedir("PUT", "/cuenta/direcciones/%d" % d1["id"],
                      dict(primera, distritoId=OTRO_DISTRITO, etiqueta="Casa de playa",
                           referencia=None, predeterminada=False), token=token)
revisar("editar una direccion -> 200", s == 200 and editada["etiqueta"] == "Casa de playa", (s, editada))
revisar("el distrito cambia con ella", editada["distrito"]["id"] == OTRO_DISTRITO, editada.get("distrito"))
revisar("y una referencia vaciada se guarda como nula", editada["referencia"] is None, editada)

s, lista, _ = pedir("GET", "/cuenta/direcciones", token=token)
revisar("editar con predeterminada:false NO deja al cliente sin ninguna marcada",
        sum(1 for d in lista if d["predeterminada"]) == 1,
        [(d["etiqueta"], d["predeterminada"]) for d in lista])

s, cuerpo, _ = pedir("POST", "/cuenta/direcciones", dict(primera, distritoId=999999), token=token)
revisar("un distrito inexistente -> 404 DISTRICT_NOT_FOUND",
        s == 404 and cuerpo["code"] == "DISTRICT_NOT_FOUND", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/cuenta/direcciones", dict(primera, etiqueta=""), token=token)
revisar("una etiqueta vacia -> 400 VALIDATION_ERROR",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))

# La prueba monta su propio segundo cliente: comprobar el aislamiento con una
# cuenta de la semilla haria que la segunda ejecucion dependiera de la primera.
EMAIL_INTRUSO = "humo-intruso-%s@ejemplo.pe" % SUFIJO
pedir("POST", "/cuenta/registro",
      {"email": EMAIL_INTRUSO, "nombre": "Intruso", "contrasena": CONTRASENA})
s, intruso, _ = pedir("POST", "/cuenta/acceso", {"email": EMAIL_INTRUSO, "contrasena": CONTRASENA})
token_intruso = intruso["tokenAcceso"] if s == 200 else None
revisar("el segundo cliente entra", s == 200, s)

s, cuerpo, _ = pedir("PUT", "/cuenta/direcciones/%d" % d1["id"], primera, token=token_intruso)
revisar("la direccion de otro cliente es 404, no 403: no se confirma que exista",
        s == 404 and cuerpo["code"] == "ADDRESS_NOT_FOUND", (s, cuerpo.get("code")))

s, ajenas, _ = pedir("GET", "/cuenta/direcciones", token=token_intruso)
revisar("y no aparece en su listado", s == 200 and ajenas == [], (s, ajenas))

print("\n=== 7. Eliminar una direccion es logico (RN-086) ===")
s, _, _ = pedir("DELETE", "/cuenta/direcciones/%d" % d2["id"], token=token)
revisar("DELETE de la predeterminada -> 204", s == 204, s)

s, lista, _ = pedir("GET", "/cuenta/direcciones", token=token)
revisar("desaparece del listado", all(d["id"] != d2["id"] for d in lista), [d["id"] for d in lista])
revisar("y la que queda hereda la marca de predeterminada",
        len(lista) == 1 and lista[0]["predeterminada"] is True, lista)

s, cuerpo, _ = pedir("DELETE", "/cuenta/direcciones/%d" % d2["id"], token=token)
revisar("borrarla otra vez -> 404, no un 500", s == 404 and cuerpo["code"] == "ADDRESS_NOT_FOUND",
        (s, cuerpo.get("code")))

print("\n=== 8. Favoritos ===")
s, favoritos, _ = pedir("GET", "/cuenta/favoritos", token=token)
revisar("una cuenta nueva no tiene favoritos", s == 200 and favoritos == [], (s, favoritos))

s, _, _ = pedir("PUT", "/cuenta/favoritos/%d" % PRODUCTO, token=token)
revisar("marcar un favorito -> 204", s == 204, s)

s, _, _ = pedir("PUT", "/cuenta/favoritos/%d" % PRODUCTO, token=token)
revisar("marcarlo otra vez -> 204 tambien: es idempotente", s == 204, s)

s, favoritos, _ = pedir("GET", "/cuenta/favoritos", token=token)
revisar("y sigue habiendo UNO, no dos", s == 200 and len(favoritos) == 1, (s, favoritos))
revisar("el favorito viene con la forma de una tarjeta de producto",
        favoritos and {"id", "nombre", "slug", "precio", "hayStock", "imagen"} <= set(favoritos[0]),
        list(favoritos[0].keys()) if favoritos else None)
revisar("y sin SKU, como cualquier otra tarjeta de la tienda",
        favoritos and "sku" not in favoritos[0], list(favoritos[0].keys()) if favoritos else None)

if OTRO_PRODUCTO:
    pedir("PUT", "/cuenta/favoritos/%d" % OTRO_PRODUCTO, token=token)
    s, favoritos, _ = pedir("GET", "/cuenta/favoritos", token=token)
    revisar("el ultimo marcado sale primero",
            s == 200 and favoritos[0]["id"] == OTRO_PRODUCTO, [f["id"] for f in favoritos])
    pedir("DELETE", "/cuenta/favoritos/%d" % OTRO_PRODUCTO, token=token)

s, ajenos, _ = pedir("GET", "/cuenta/favoritos", token=token_intruso)
revisar("los favoritos son de cada cliente, no compartidos", s == 200 and ajenos == [], (s, ajenos))

s, cuerpo, _ = pedir("PUT", "/cuenta/favoritos/999999", token=token)
revisar("un producto inexistente -> 404 PRODUCT_NOT_FOUND",
        s == 404 and cuerpo["code"] == "PRODUCT_NOT_FOUND", (s, cuerpo.get("code")))

s, _, _ = pedir("DELETE", "/cuenta/favoritos/%d" % PRODUCTO, token=token)
revisar("quitar un favorito -> 204", s == 204, s)
s, _, _ = pedir("DELETE", "/cuenta/favoritos/%d" % PRODUCTO, token=token)
revisar("quitarlo otra vez -> 204: tambien es idempotente", s == 204, s)
s, favoritos, _ = pedir("GET", "/cuenta/favoritos", token=token)
revisar("la lista se queda vacia", s == 200 and favoritos == [], (s, favoritos))

s, cuerpo, _ = pedir("GET", "/cuenta/favoritos")
revisar("los favoritos exigen sesion -> 401", s == 401, s)

print("\n=== 9. Cambiar la contrasena (RN-065) ===")
s, cuerpo, _ = pedir("POST", "/cuenta/yo/contrasena", {"contrasenaNueva": CONTRASENA_NUEVA}, token=token)
revisar("sin la actual, a quien SI la tiene -> 400 VALIDATION_ERROR (RN-065)",
        s == 400 and cuerpo["code"] == "VALIDATION_ERROR", (s, cuerpo.get("code")))
revisar("y el error senala contrasenaActual",
        cuerpo.get("errors") and cuerpo["errors"][0]["field"] == "contrasenaActual", cuerpo.get("errors"))

s, cuerpo, _ = pedir("POST", "/cuenta/yo/contrasena",
                     {"contrasenaActual": "no es esta", "contrasenaNueva": CONTRASENA_NUEVA}, token=token)
revisar("con la actual equivocada -> 401 INVALID_CREDENTIALS",
        s == 401 and cuerpo["code"] == "INVALID_CREDENTIALS", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/cuenta/yo/contrasena",
                     {"contrasenaActual": CONTRASENA, "contrasenaNueva": "corta"}, token=token)
revisar("una contrasena nueva debil -> 400 (RN-062)", s == 400 and cuerpo["code"] == "VALIDATION_ERROR",
        (s, cuerpo.get("code")))

s, _, _ = pedir("POST", "/cuenta/yo/contrasena",
                {"contrasenaActual": CONTRASENA, "contrasenaNueva": CONTRASENA_NUEVA},
                token=token, cabeceras=con_cookie(refresco))
revisar("cambio correcto -> 204", s == 204, s)

s, cuerpo, _ = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA_NUEVA})
revisar("se entra con la nueva", s == 200, (s, cuerpo.get("code")))
refresco = refresco_de(cab) or refresco

s, cuerpo, _ = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA})
revisar("y ya no con la vieja -> 401", s == 401 and cuerpo["code"] == "INVALID_CREDENTIALS",
        (s, cuerpo.get("code")))

print("\n=== 10. Rotacion del refresco ===")
s, sesion, cab = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA_NUEVA})
refresco = refresco_de(cab)
token = sesion["tokenAcceso"]
revisar("hay una sesion viva para rotar", refresco is not None)

s, cuerpo, _ = pedir("POST", "/cuenta/refrescar")
revisar("refrescar sin cookie -> 401", s == 401 and cuerpo["code"] == "UNAUTHENTICATED",
        (s, cuerpo.get("code")))

s, renovada, cab2 = pedir("POST", "/cuenta/refrescar", cabeceras=con_cookie(refresco))
revisar("refrescar emite una sesion nueva", s == 200 and renovada["tokenAcceso"], (s, renovada))
revisar("y devuelve el cliente otra vez", renovada and renovada["cliente"]["email"] == EMAIL,
        renovada.get("cliente") if renovada else None)
refresco2 = refresco_de(cab2)
revisar("el refresco rota: el nuevo es distinto", refresco2 and refresco2 != refresco)

s, cuerpo, _ = pedir("POST", "/cuenta/refrescar", cabeceras=con_cookie(refresco))
revisar("reusar el refresco anterior -> 401", s == 401, (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/cuenta/refrescar", cabeceras=con_cookie(refresco2))
revisar("la reutilizacion revoca la familia entera: el bueno tampoco sirve", s == 401,
        (s, cuerpo.get("code")))

s, sesion, cab = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA_NUEVA})
refresco = refresco_de(cab)
s, _, cab3 = pedir("POST", "/cuenta/salir", cabeceras=con_cookie(refresco))
revisar("salir -> 204", s == 204, s)
revisar("y borra la cookie", "refresco_tienda=" in cab3.get("Set-Cookie", "")
        and "Max-Age=0" in cab3.get("Set-Cookie", ""), cab3.get("Set-Cookie", "")[:140])

s, cuerpo, _ = pedir("POST", "/cuenta/refrescar", cabeceras=con_cookie(refresco))
revisar("el refresco ya no vale despues de salir -> 401", s == 401, (s, cuerpo.get("code")))

s, _, _ = pedir("POST", "/cuenta/salir")
revisar("salir sin cookie tambien -> 204, no un error", s == 204, s)

# Una tienda que adjunta el token de acceso a todas sus llamadas cerraria
# sesion con uno ya caducado. Si /salir lo validara, el cliente se quedaria sin
# poder salir justo cuando mas quiere hacerlo.
s, _, _ = pedir("POST", "/cuenta/salir", token="un.token.caducado")
revisar("salir con un token invalido tambien -> 204: las rutas de sesion no lo miran", s == 204, s)

s, cuerpo, _ = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA_NUEVA},
                     token="un.token.caducado")
revisar("y entrar con un token viejo colgando tampoco falla", s == 200, (s, cuerpo.get("code")))

print("\n=== 11. Acceso con Google ===")
s, cuerpo, _ = pedir("POST", "/cuenta/google", {"credencial": "no.es.un.token.de.google"})
if s == 503:
    revisar("sin GOOGLE_CLIENT_ID el endpoint lo dice claro -> 503 GOOGLE_NOT_CONFIGURED",
            cuerpo["code"] == "GOOGLE_NOT_CONFIGURED", cuerpo.get("code"))
    revisar("y el mensaje nombra la variable que falta",
            "GOOGLE_CLIENT_ID" in (cuerpo.get("detail") or ""), cuerpo.get("detail"))
else:
    revisar("con GOOGLE_CLIENT_ID configurado, una credencial invalida -> 401 INVALID_PROVIDER_TOKEN",
            s == 401 and cuerpo["code"] == "INVALID_PROVIDER_TOKEN", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/cuenta/google", {})
revisar("sin credencial -> 400 VALIDATION_ERROR", s == 400 and cuerpo["code"] == "VALIDATION_ERROR",
        (s, cuerpo.get("code")))

print("\n=== 12. Limite de intentos (RN-068) ===")
# Desde una IP propia: agotar el limite en la IP de la prueba dejaria sin
# ejecutar lo que venga despues.
IP_ATAQUE = "198.51.100." + SUFIJO[:2]
codigos = []
for intento in range(7):
    s, cuerpo, cab = pedir("POST", "/cuenta/acceso",
                           {"email": EMAIL, "contrasena": "fuerza bruta %d" % intento}, ip=IP_ATAQUE)
    codigos.append((s, cuerpo.get("code")))
    if s == 429:
        break

revisar("insistir con la contrasena acaba en 429 TOO_MANY_REQUESTS",
        codigos[-1][0] == 429 and codigos[-1][1] == "TOO_MANY_REQUESTS", codigos)
revisar("el limite no salta al primer intento: los 5 primeros son 401",
        len(codigos) >= 6 and all(c[0] == 401 for c in codigos[:5]), codigos)
revisar("y la respuesta dice cuanto hay que esperar",
        cuerpo.get("reintentarEn", 0) > 0, cuerpo)
revisar("con cabecera Retry-After, que es lo que lee un cliente HTTP (RN-068)",
        cab.get("Retry-After") is not None and int(cab["Retry-After"]) > 0, cab.get("Retry-After"))

# El limite por correo protege a la CUENTA, asi que sigue al correo y no a la
# IP: si se levantara cambiando de origen no serviria de nada contra un ataque
# repartido entre varias maquinas. El precio es que el dueno legitimo tambien
# espera esos minutos, y es el precio correcto.
s, cuerpo, _ = pedir("POST", "/cuenta/acceso", {"email": EMAIL, "contrasena": CONTRASENA_NUEVA})
revisar("el bloqueo sigue al correo: cambiar de IP no lo levanta",
        s == 429 and cuerpo["code"] == "TOO_MANY_REQUESTS", (s, cuerpo.get("code")))

s, cuerpo, _ = pedir("POST", "/cuenta/acceso",
                     {"email": EMAIL_INTRUSO, "contrasena": CONTRASENA}, ip=IP_ATAQUE)
revisar("pero solo a ese correo: otra cuenta desde la MISMA IP entra sin problema",
        s == 200, (s, cuerpo.get("code")))

print("\n" + "=" * 60)
print("PASAN %d   FALLAN %d" % (ok, len(fallos)))
for f in fallos:
    print("  - " + f)
sys.exit(1 if fallos else 0)
