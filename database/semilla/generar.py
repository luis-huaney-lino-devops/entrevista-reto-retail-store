# -*- coding: utf-8 -*-
"""
Genera `database/migrations/V002__datos.sql` y las imágenes que necesita.

Por qué un generador y no SQL escrito a mano: los datos salen de tres fuentes
—el ubigeo del Perú, el catálogo real de una ferretería y sus logotipos— y
mantenerlos a mano significaría teclear 1 800 distritos y calcular el ancho,
el alto y el hash de 255 imágenes. Esto se ejecuta una vez y deja un SQL
determinista: la misma entrada produce byte a byte el mismo archivo.

Uso:

    python database/semilla/generar.py

Entradas (rutas al final del archivo, en FUENTES):
  - Los seeders de ubigeo de otro proyecto, en PHP.
  - El volcado JSON de marcas, categorías y subcategorías.
  - La carpeta de logotipos e imágenes de categoría.

Salidas:
  - database/migrations/V002__datos.sql
  - backend/java/datos/archivos/semilla/**  (WebP, listo para el almacén local)
"""
from __future__ import annotations

import hashlib
import html
import json
import pathlib
import re
import unicodedata

from PIL import Image

# --------------------------------------------------------------------- rutas

AQUI = pathlib.Path(__file__).resolve().parent
RAIZ = AQUI.parent.parent
SALIDA_SQL = RAIZ / "database" / "migrations" / "V002__datos.sql"
# Dentro del almacén local del backend, bajo `semilla/`, que es la única rama
# de `datos/` que sí viaja en el repositorio.
SALIDA_IMG = RAIZ / "backend" / "java" / "datos" / "archivos" / "semilla"

URL_BASE = "http://localhost:8080/archivos"

# Los cuatro tamaños del catálogo. Nunca se amplía: si el original es menor que
# la variante, la variante se queda en el tamaño del original (RN-073).
VARIANTES = [("MINIATURA", 160), ("TARJETA", 480), ("DETALLE", 1200), ("ORIGINAL", 2400)]


# ------------------------------------------------------------------ utilidades

def limpiar(texto: str) -> str:
    """El volcado trae entidades HTML: «Iluminaci&oacute;n LED»."""
    return html.unescape(texto or "").strip()


def slug(texto: str) -> str:
    """Mismas reglas que `Slug.java`: minúsculas, sin acentos, con guiones."""
    sin_acentos = "".join(
        c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn"
    )
    limpio = re.sub(r"[^a-zA-Z0-9]+", "-", sin_acentos).strip("-").lower()
    return re.sub(r"-{2,}", "-", limpio)


def sql(valor) -> str:
    """Literal SQL. None es NULL; el resto se escapa duplicando la comilla."""
    if valor is None:
        return "NULL"
    if isinstance(valor, bool):
        return "true" if valor else "false"
    if isinstance(valor, (int, float)):
        return str(valor)
    return "'" + str(valor).replace("'", "''") + "'"


# -------------------------------------------------------------------- imágenes

def convertir(origen: pathlib.Path, destino_rel: str) -> dict | None:
    """
    Convierte una imagen a WebP en sus variantes y devuelve lo que necesita la
    fila de `archivo`. None si el archivo no se puede leer.

    Se aplana sobre blanco: muchos logotipos son PNG con transparencia, y un
    WebP con alfa sobre la tarjeta blanca de la tienda se ve bien, pero sobre
    cualquier otro fondo aparece el recorte. Un fondo explícito es predecible.
    """
    try:
        with Image.open(origen) as img:
            img = img.convert("RGBA")
            fondo = Image.new("RGBA", img.size, (255, 255, 255, 255))
            fondo.alpha_composite(img)
            plano = fondo.convert("RGB")
            ancho, alto = plano.size

            salidas = {}
            # Un logotipo de 300 px cabe entero en TARJETA, DETALLE y ORIGINAL:
            # escribirlo tres veces son tres copias del mismo archivo. Se guarda
            # una y las variantes que no se distinguen apuntan a ella.
            por_tamano: dict[tuple[int, int], dict] = {}
            for nombre, lado in VARIANTES:
                copia = plano.copy()
                # thumbnail no amplía nunca: si cabe, lo deja como está.
                copia.thumbnail((lado, lado), Image.LANCZOS)
                medida = (copia.width, copia.height)

                if medida in por_tamano:
                    salidas[nombre] = por_tamano[medida]
                    continue

                ruta = SALIDA_IMG / f"{destino_rel}-{nombre.lower()}.webp"
                ruta.parent.mkdir(parents=True, exist_ok=True)
                copia.save(ruta, "WEBP", quality=82, method=6)
                salidas[nombre] = por_tamano[medida] = {
                    "clave": f"semilla/{destino_rel}-{nombre.lower()}.webp",
                    "ancho": copia.width,
                    "alto": copia.height,
                    "bytes": ruta.stat().st_size,
                }
    except Exception as fallo:  # noqa: BLE001 — una imagen rota no puede parar la semilla
        print(f"  ! {origen.name}: {fallo}")
        return None

    principal = salidas["ORIGINAL"]
    return {
        "clave": principal["clave"],
        "nombre_original": origen.name,
        "bytes": principal["bytes"],
        "ancho": principal["ancho"],
        "alto": principal["alto"],
        # El hash del contenido de la fuente: es lo que identifica la imagen,
        # no el nombre del archivo, que en el volcado viene repetido con
        # sufijos («logo_2.png», «logo_3.png»).
        "hash": hashlib.sha256(origen.read_bytes()).hexdigest(),
        "variantes": salidas,
    }


# ---------------------------------------------------------------------- ubigeo

def leer_ubigeo(dir_seeders: pathlib.Path) -> tuple[list, list, list]:
    """
    Extrae departamentos, provincias y distritos de los seeders de Laravel.

    Los distritos van indexados por el número de provincia 1..194 en el mismo
    orden en que las lista el seeder de provincias; aquí se reconstruye ese
    orden para traducir cada número a su provincia.
    """
    def texto(nombre):
        return (dir_seeders / nombre).read_text(encoding="utf-8")

    departamentos = re.findall(
        r"'([^']+)'", texto("DepartamentosTableSeeder.php").split("DEPARTAMENTOS = [")[1].split("];")[0]
    )

    provincias = []
    bloque = texto("ProvinciasTableSeeder.php").split("PROVINCIAS = [")[1].split("\n    ];")[0]
    for linea in re.finditer(r"'departamento'\s*=>\s*'([^']+)'.*?'provincias'\s*=>\s*\[(.*?)\]", bloque, re.S):
        depa = linea.group(1)
        for prov in re.findall(r"'([^']+)'", linea.group(2)):
            provincias.append((depa, prov))

    distritos = []
    bloque = texto("DistritosTableSeeder.php").split("DISTRITOS = [")[1].split("\n    ];")[0]
    for linea in re.finditer(r"(\d+)\s*=>\s*\[(.*?)\]", bloque, re.S):
        indice = int(linea.group(1))
        depa, prov = provincias[indice - 1]
        for dist in re.findall(r"'([^']+)'", linea.group(2)):
            distritos.append((depa, prov, dist))

    return departamentos, provincias, distritos


# -------------------------------------------------------------------- catálogo

def leer_volcado(ruta: pathlib.Path) -> list[dict]:
    datos = json.loads(ruta.read_text(encoding="utf-8"))
    return next(x for x in datos if x.get("type") == "table")["data"]


# ==========================================================================
# Productos
# ==========================================================================
#
# El volcado no trae productos, solo marcas y categorías, así que el catálogo
# se escribe aquí. Cada entrada es:
#
#   (subcategoría, nombre, marca, precio, precio_anterior, stock, descripción)
#
# Los precios son de referencia del mercado peruano en soles. La marca se
# enlaza por nombre: si no existiera, el producto queda sin marca en vez de
# fallar, que es exactamente lo que permite el esquema.
PRODUCTOS = [
    # ------------------------------------------------- Cementos y Agregados
    ("Cementos y Agregados", "Cemento Portland Tipo I 42.5 kg", "Cemento Sol", 34.90, None, 480,
     "Bolsa de 42.5 kg. Uso general en obras de concreto armado y albañilería."),
    ("Cementos y Agregados", "Cemento Portland Tipo V 42.5 kg", "Cemento Andino", 39.50, 42.00, 260,
     "Alta resistencia a sulfatos. Para cimentaciones en suelos agresivos."),
    ("Cementos y Agregados", "Cemento Extraforte 42.5 kg", "Cemento Pacasmayo", 36.80, None, 320,
     "Mayor resistencia inicial: permite desencofrar antes."),
    ("Cementos y Agregados", "Yeso Constructor 18 kg", "Cemento Nacional", 14.20, None, 190,
     "Para tarrajeo interior y molduras. Fragua en 25 minutos."),
    ("Cementos y Agregados", "Aditivo Impermeabilizante Chema 1 gl", "Chema", 48.90, 56.00, 75,
     "Reduce la absorción capilar del concreto. Rinde 20 m² por galón."),
    ("Cementos y Agregados", "Sika 1 Impermeabilizante 4 kg", "Sika", 62.40, None, 58,
     "Aditivo en polvo para morteros impermeables en cisternas y sótanos."),

    # ----------------------------------------------------- Fierros y Aceros
    ("Fierros y Aceros", "Fierro Corrugado 1/2\" x 9 m", "Aceros Arequipa", 38.60, None, 640,
     "Grado 60, ASTM A615. La varilla estándar de columnas y vigas."),
    ("Fierros y Aceros", "Fierro Corrugado 3/8\" x 9 m", "Siderperu", 21.90, None, 720,
     "Grado 60. Para estribos, losas aligeradas y muros de contención."),
    ("Fierros y Aceros", "Alambre Negro Recocido N°16 x kg", "Prodac", 6.80, None, 400,
     "Amarre de armaduras. Rollo por kilogramo."),
    ("Fierros y Aceros", "Malla Electrosoldada 15x15 cm 2.4 m", "Aceros Arequipa", 128.00, 145.00, 85,
     "Panel de 2.40 × 6.00 m para losas y pavimentos."),
    ("Fierros y Aceros", "Tubo Cuadrado LAF 2\" x 2 mm x 6 m", "Tupemesa", 89.50, None, 110,
     "Acero laminado en frío para estructuras livianas y rejas."),

    # -------------------------------------------------- Pinturas y Barnices
    ("Pinturas y Barnices", "Látex Supermate Blanco 1 gl", "Anypsa", 52.90, None, 240,
     "Interiores. Acabado mate, rinde 40 m² por galón y por mano."),
    ("Pinturas y Barnices", "Esmalte Sintético Brillante 1 gl", "Tekno", 78.50, 89.00, 160,
     "Sobre metal y madera. Secado al tacto en 4 horas."),
    ("Pinturas y Barnices", "Pintura Látex Premium 5 gl", "Vencedor", 219.00, None, 70,
     "Lavable, alto poder cubriente. Balde de 5 galones."),
    ("Pinturas y Barnices", "Base Imprimante Sellador 1 gl", "CPP", 44.00, None, 130,
     "Sella el muro antes de pintar y reduce el consumo de látex."),
    ("Pinturas y Barnices", "Barniz Marino Transparente 1 gl", "Sherwin Williams", 112.00, None, 45,
     "Protección UV para madera expuesta a la intemperie."),
    ("Pinturas y Barnices", "Thinner Acrílico 1 gl", "Anypsa", 32.50, None, 180,
     "Diluyente para esmaltes y lacas. Envase metálico."),

    # ----------------------------------------------------- Drywall y Techos
    ("Drywall y Techos", "Plancha Drywall Standard 1.22 x 2.44 m", "Gyplac", 38.90, None, 300,
     "Espesor 1/2\". Tabiquería interior y falsos cielos."),
    ("Drywall y Techos", "Plancha Drywall Resistente a la Humedad", "Volcán", 52.00, 58.00, 140,
     "Placa verde para baños y cocinas."),
    ("Drywall y Techos", "Calamina Galvanizada 0.22 mm x 3.60 m", "Calaminon", 41.50, None, 220,
     "Techado económico. Perfil ondulado tradicional."),
    ("Drywall y Techos", "Teja Andina Fibrocemento 1.14 x 0.72 m", "Eternit", 34.80, None, 260,
     "Cobertura ligera con aspecto de teja, sin estructura pesada."),
    ("Drywall y Techos", "Plancha Traslúcida Fibraforte 1.10 x 3.00 m", "Fibraforte", 68.00, None, 95,
     "Ilumina el interior sin abrir el techo. Resistente al granizo."),

    # --------------------------------------------- Herramientas Eléctricas
    ("Herramientas Eléctricas", "Taladro Percutor 1/2\" 750 W", "Bosch", 389.00, 449.00, 42,
     "Con maletín y juego de brocas. Velocidad variable y reversa."),
    ("Herramientas Eléctricas", "Rotomartillo SDS-Plus 800 W", "Makita", 729.00, None, 28,
     "Para concreto y perforaciones de anclaje hasta 26 mm."),
    ("Herramientas Eléctricas", "Amoladora Angular 4 1/2\" 850 W", "DeWalt", 315.00, None, 55,
     "Corte y desbaste. Protector regulable sin herramienta."),
    ("Herramientas Eléctricas", "Sierra Circular 7 1/4\" 1400 W", "Milwaukee", 640.00, 720.00, 22,
     "Profundidad de corte 65 mm a 90°. Base de aluminio."),
    ("Herramientas Eléctricas", "Atornillador Inalámbrico 12V", "Ingco", 189.00, None, 90,
     "Dos baterías de litio, cargador y 20 puntas."),
    ("Herramientas Eléctricas", "Esmeril de Banco 6\" 370 W", "Total", 268.00, None, 34,
     "Dos piedras de grano distinto y protectores de chispa."),
    ("Herramientas Eléctricas", "Compresora de Aire 50 L 2 HP", "Einhell", 899.00, 999.00, 15,
     "Para pintado, clavadoras neumáticas e inflado."),

    # ------------------------------------------------ Herramientas Manuales
    ("Herramientas Manuales", "Juego de Llaves Mixtas 12 piezas", "Stanley", 128.00, None, 120,
     "De 8 a 22 mm en acero cromo vanadio."),
    ("Herramientas Manuales", "Martillo Carpintero 16 oz Mango Fibra", "Truper", 38.90, None, 210,
     "Cabeza forjada y mango antivibración."),
    ("Herramientas Manuales", "Alicate Universal 8\"", "Knipex", 96.00, None, 65,
     "Mordaza templada por inducción. Aislamiento hasta 1000 V."),
    ("Herramientas Manuales", "Juego de Destornilladores 6 piezas", "Wera", 142.00, 165.00, 48,
     "Plano y estrella, con mango ergonómico antideslizante."),
    ("Herramientas Manuales", "Flexómetro 5 m x 25 mm", "Bahco", 29.50, None, 300,
     "Cinta con doble cara y freno automático."),
    ("Herramientas Manuales", "Nivel de Aluminio 24\"", "Bellota", 54.00, None, 95,
     "Tres burbujas: horizontal, vertical y 45°."),
    ("Herramientas Manuales", "Carretilla Buggy 90 L Llanta Neumática", "Pretul", 219.00, None, 40,
     "Bandeja reforzada para obra. Capacidad 150 kg."),

    # ---------------------------------------------------- Protección Personal
    ("Protección Personal", "Casco de Seguridad Dieléctrico", "Steelpro", 32.90, None, 380,
     "Clase E, hasta 20 000 V. Suspensión de seis puntos."),
    ("Protección Personal", "Lentes de Seguridad Antiempañante", "3M", 18.50, None, 520,
     "Policarbonato con tratamiento antirrayadura. UV400."),
    ("Protección Personal", "Guantes de Nitrilo Reforzado (par)", "Ansell", 14.90, None, 640,
     "Agarre en húmedo y resistencia a la abrasión."),
    ("Protección Personal", "Respirador Media Cara con Filtros", "MSA", 148.00, 172.00, 70,
     "Contra vapores orgánicos y partículas. Filtros incluidos."),
    ("Protección Personal", "Arnés de Cuerpo Entero 4 Anillas", "Delta Plus", 210.00, None, 45,
     "Trabajo en altura. Certificado EN 361."),
    ("Protección Personal", "Orejeras de Copa 27 dB", "3M", 46.00, None, 150,
     "Atenuación 27 dB. Diadema regulable."),

    # ---------------------------------------------------- Calzado de Seguridad
    ("Calzado de Seguridad", "Botín Dieléctrico Punta de Acero", "Bata Industrials", 189.00, None, 130,
     "Suela antideslizante y resistente a hidrocarburos. Tallas 38 a 45."),
    ("Calzado de Seguridad", "Zapato de Seguridad Punta Composite", "CAT", 268.00, 299.00, 60,
     "Punta no metálica: no conduce ni activa detectores."),
    ("Calzado de Seguridad", "Bota de Jebe Caña Alta con Punta", "Steelpro", 79.90, None, 180,
     "Para obra húmeda y trabajos de concreto."),

    # ------------------------------------------------------- Limpieza y Aseo
    ("Limpieza y Aseo", "Escoba de Cerda Plástica con Mango", "Hermex", 18.90, None, 260,
     "Cerda resistente para interiores y exteriores."),
    ("Limpieza y Aseo", "Trapeador Industrial 400 g", "Truper", 24.50, None, 200,
     "Cabeza de algodón reforzado, mango de madera."),
    ("Limpieza y Aseo", "Balde Graduado 20 L", "Rotoplas", 21.00, None, 240,
     "Con marcas de volumen. Polipropileno de alta densidad."),
    ("Limpieza y Aseo", "Papel Toalla Institucional x 6", "Kimberly-Clark", 58.00, None, 110,
     "Rollo de 180 m. Para dispensador de pared."),

    # --------------------------------------------------- Escaleras y Varios
    ("Escaleras y Varios", "Escalera Tijera Aluminio 7 pasos", "Truper", 268.00, 310.00, 55,
     "Capacidad 120 kg. Peldaños antideslizantes."),
    ("Escaleras y Varios", "Escalera Extensible 24 pasos", "Gladiator", 620.00, None, 18,
     "Aluminio reforzado, altura útil 6.5 m."),
    ("Escaleras y Varios", "Andamio Modular 1.50 x 2.00 m", "Tupemesa", 480.00, None, 24,
     "Cuerpo galvanizado. Se apila para alcanzar más altura."),

    # -------------------------------------------- Herramientas de Jardín
    ("Herramientas de Jardín", "Tijera de Podar Profesional 8\"", "Bellota", 78.00, None, 95,
     "Hoja de acero al carbono con recubrimiento antiadherente."),
    ("Herramientas de Jardín", "Pala Cuchara Mango Largo", "Collins", 54.90, None, 150,
     "Hoja templada para tierra compacta."),
    ("Herramientas de Jardín", "Cortacésped a Gasolina 139 cc", "Stihl", 1890.00, 2100.00, 12,
     "Ancho de corte 46 cm, cinco alturas y bolsa recolectora."),
    ("Herramientas de Jardín", "Desbrozadora 2 Tiempos 43 cc", "Husqvarna", 1420.00, None, 16,
     "Con arnés, cuchilla y cabezal de nylon."),
    ("Herramientas de Jardín", "Rastrillo Metálico 14 Dientes", "Truper", 38.00, None, 170,
     "Mango de madera barnizada de 1.40 m."),

    # ---------------------------------------------------- Riego y Mangueras
    ("Riego y Mangueras", "Manguera Reforzada 1/2\" x 50 m", "Pavco Wavin", 129.00, None, 85,
     "Tres capas con malla textil. No se colapsa al doblarse."),
    ("Riego y Mangueras", "Aspersor Sectorial Regulable", "Tigre", 24.90, None, 210,
     "Cobertura de 0 a 360°. Base con estaca."),
    ("Riego y Mangueras", "Pistola de Riego 7 Funciones", "Karson", 32.00, None, 160,
     "Cuerpo metálico con gatillo de bloqueo."),
    ("Riego y Mangueras", "Electrobomba Periférica 0.5 HP", "Pedrollo", 389.00, 430.00, 38,
     "Para riego y presurización doméstica. Caudal 40 L/min."),

    # ----------------------------------------------- Fertilizantes y Semillas
    ("Fertilizantes y Semillas", "Abono Foliar Completo 1 L", "Cantol", 34.50, None, 140,
     "Nitrógeno, fósforo y potasio más micronutrientes."),
    ("Fertilizantes y Semillas", "Compost Orgánico 20 kg", "GENÉRICO", 28.00, None, 200,
     "Materia orgánica estabilizada para huerto y macetas."),
    ("Fertilizantes y Semillas", "Semilla de Césped Americano 1 kg", "GENÉRICO", 46.00, None, 120,
     "Germina en 10 días. Rinde 35 m²."),

    # ------------------------------------------------- Cerraduras y Chapas
    ("Cerraduras y Chapas", "Cerradura de Sobreponer 3 Golpes", "Yale", 148.00, None, 110,
     "Cilindro de latón y cinco pines. Tres llaves incluidas."),
    ("Cerraduras y Chapas", "Cerradura de Pomo Exterior", "Schlage", 98.00, 118.00, 130,
     "Acabado satinado, resistente a la corrosión."),
    ("Cerraduras et Chapas".replace(" et ", " y "), "Cerradura Digital con Huella", "Ezviz", 689.00, None, 25,
     "Huella, clave y llave física. Registra hasta 50 usuarios."),
    ("Cerraduras y Chapas", "Cerradura Embutir Alta Seguridad", "Mul-T-Lock", 420.00, None, 32,
     "Llave patentada de copia restringida."),

    # -------------------------------------------------- Bisagras y Rieles
    ("Bisagras y Rieles", "Bisagra Capuchina 3\" (par)", "Hermex", 12.50, None, 420,
     "Acero laminado con acabado bronce."),
    ("Bisagras y Rieles", "Riel Telescópico 45 cm (par)", "Scanavini", 38.00, None, 180,
     "Extensión total con rodamientos. Carga 35 kg."),
    ("Bisagras y Rieles", "Bisagra Cierre Suave para Mueble", "Papaiz", 9.90, None, 500,
     "Cierre amortiguado, apertura 110°."),

    # ------------------------------------------------ Candados y Seguridad
    ("Candados y Seguridad", "Candado de Acero 50 mm", "Master Lock", 46.00, None, 240,
     "Arco endurecido y cuerpo de latón macizo."),
    ("Candados y Seguridad", "Candado Antibullying 70 mm", "Travex", 78.00, 92.00, 95,
     "Arco protegido. Resiste corte con cizalla."),
    ("Candados y Seguridad", "Cámara IP Wi-Fi Interior 1080p", "Hikvision", 189.00, None, 60,
     "Visión nocturna, audio bidireccional y ranura microSD."),
    ("Candados y Seguridad", "Kit Videovigilancia 4 Cámaras", "Dahua", 1290.00, 1450.00, 14,
     "DVR de 4 canales, cámaras bullet y un disco de 1 TB."),

    # ------------------------------------------------ Tuberias y Conexiones
    ("Tuberias y Conexiones", "Tubo PVC Agua 1/2\" x 5 m Clase 10", "Pavco Wavin", 18.90, None, 460,
     "Presión de trabajo 150 psi. Unión a simple presión."),
    ("Tuberias y Conexiones", "Tubo PVC Desagüe 4\" x 3 m", "Nicoll", 42.00, None, 280,
     "Para montantes y colectores. Campana integrada."),
    ("Tuberias y Conexiones", "Codo PVC 90° 1/2\"", "Tigre", 1.80, None, 1200,
     "Unión a presión. Compatible con toda la línea de 1/2\"."),
    ("Tuberias y Conexiones", "Pegamento PVC 1/4 gl", "Chema", 32.00, None, 190,
     "Fraguado rápido. Para tubería de agua fría y desagüe."),
    ("Tuberias y Conexiones", "Tanque de Agua 1100 L", "Rotoplas", 689.00, 760.00, 30,
     "Tricapa con protección UV y tapa hermética."),

    # -------------------------------------------------- Griferia y Llaves
    ("Griferia y Llaves", "Grifería Monomando de Lavatorio", "Vainsa", 189.00, None, 90,
     "Cartucho cerámico de 35 mm. Acabado cromado."),
    ("Griferia y Llaves", "Mezcladora de Ducha Cromada", "Italgrif", 148.00, 178.00, 110,
     "Con ducha de mano y soporte regulable."),
    ("Griferia y Llaves", "Llave de Paso Esférica 1/2\"", "Genebre", 28.00, None, 340,
     "Cuerpo de latón forjado, paso total."),
    ("Griferia y Llaves", "Grifo de Jardín Cromado", "Cobra", 34.50, None, 220,
     "Rosca para manguera. Perilla tipo cruz."),

    # ------------------------------------------- Sanitarios y Accesorios
    ("Sanitarios y Accesorios", "Inodoro One Piece con Asiento", "Trebol", 549.00, 620.00, 45,
     "Descarga dual 3/6 L. Incluye asiento de cierre suave."),
    ("Sanitarios y Accesorios", "Lavatorio de Pedestal Blanco", "Trebol", 189.00, None, 75,
     "Porcelana vitrificada. Juego completo con pedestal."),
    ("Sanitarios y Accesorios", "Juego de Accesorios de Baño 5 piezas", "Vainsa", 128.00, None, 95,
     "Toallero, jabonera, papelera, gancho y portarrollo."),
    ("Sanitarios et Accesorios".replace(" et ", " y "), "Terma Eléctrica 50 L", "Rheem", 899.00, None, 22,
     "Tanque esmaltado con ánodo de magnesio. 1500 W."),

    # -------------------------------------------------- Cables y Conductores
    ("Cables y Conductores", "Cable THW 14 AWG x 100 m", "Indeco", 168.00, None, 140,
     "Cobre electrolítico, aislamiento 600 V."),
    ("Cables y Conductores", "Cable THW 12 AWG x 100 m", "Celsa", 248.00, None, 120,
     "Para circuitos de tomacorrientes. 600 V."),
    ("Cables et Conductores".replace(" et ", " y "), "Cable Vulcanizado 2x14 x 100 m", "Indeco", 389.00, 430.00, 60,
     "Uso a la intemperie y en obra. Cubierta reforzada."),
    ("Cables y Conductores", "Cable Coaxial RG6 x 100 m", "Centelsa", 158.00, None, 70,
     "Para CCTV y televisión. Malla de 90%."),

    # ------------------------------------ Interruptores y Tomacorrientes
    ("Interruptores y Tomacorrientes", "Interruptor Simple Empotrar", "Bticino", 14.90, None, 500,
     "Línea Modus. 10 A, 250 V."),
    ("Interruptores y Tomacorrientes", "Tomacorriente Doble con Tierra", "Schneider Electric", 22.50, None, 420,
     "16 A con puesta a tierra. Placa incluida."),
    ("Interruptores y Tomacorrientes", "Interruptor Termomagnético 2x20 A", "ABB", 48.00, None, 260,
     "Curva C, poder de corte 6 kA. Riel DIN."),
    ("Interruptores y Tomacorrientes", "Tablero de Distribución 12 Polos", "Chint", 128.00, 149.00, 90,
     "Empotrar, con barra de neutro y tierra."),

    # ------------------------------------------------------ Iluminación LED
    ("Iluminación LED", "Foco LED 12 W Luz Fría E27", "Philips", 12.90, None, 800,
     "1 200 lúmenes, 6500 K. Equivale a 100 W incandescentes."),
    ("Iluminación LED", "Panel LED Empotrar 18 W Redondo", "Opalux", 34.00, None, 320,
     "Luz uniforme sin puntos. Driver incluido."),
    ("Iluminación LED", "Reflector LED 50 W Exterior IP65", "Ledvance", 78.00, 92.00, 140,
     "Resistente a lluvia y polvo. Soporte orientable."),
    ("Iluminación LED", "Cinta LED 5 m RGB con Control", "Megabright", 46.00, None, 180,
     "Adhesiva, cortable cada 5 cm. Fuente y control remoto."),
    ("Iluminación LED", "Tubo LED T8 18 W 1.20 m", "Sylvania", 24.50, None, 260,
     "Reemplaza al fluorescente sin cambiar la luminaria."),

    # -------------------------------------------------- Tornillos y Pernos
    ("Tornillos y Pernos", "Perno Hexagonal 1/2\" x 4\" (caja 50)", "Hermex", 89.00, None, 130,
     "Grado 5 con tuerca y arandela plana."),
    ("Tornillos y Pernos", "Tornillo Autorroscante 8 x 1\" (caja 500)", "Pretul", 42.00, None, 210,
     "Cabeza plana estrella. Para drywall y metal delgado."),
    ("Tornillos y Pernos", "Tornillo para Drywall 6 x 1 5/8\" (caja 1000)", "Hermex", 58.00, None, 180,
     "Punta aguda y cabeza trompeta. Fosfatado negro."),

    # ----------------------------------------------------- Clavos y Grapas
    ("Clavos y Grapas", "Clavo con Cabeza 2 1/2\" x kg", "Prodac", 7.20, None, 520,
     "Acero al carbono para encofrado y carpintería."),
    ("Clavos y Grapas", "Grapa para Cerco 1\" x kg", "Prodac", 9.80, None, 300,
     "Galvanizada, para postes de madera."),
    ("Clavos y Grapas", "Clavo de Concreto 1 1/2\" x kg", "Prodac", 12.50, None, 240,
     "Templado, penetra en concreto sin doblarse."),

    # --------------------------------------------------- Anclajes y Tarugos
    ("Anclajes y Tarugos", "Tarugo Plástico N°8 (bolsa 100)", "Hermex", 9.50, None, 600,
     "Con reborde antigiro. Para broca de 8 mm."),
    ("Anclajes y Tarugos", "Anclaje de Expansión 1/2\" x 3\"", "Hilti", 8.90, None, 380,
     "Para carga media en concreto fisurado."),
    ("Anclajes y Tarugos", "Anclaje Químico 300 ml", "Sika", 128.00, 145.00, 65,
     "Resina epóxica de dos componentes. Con boquilla mezcladora."),

    # ------------------------------------------------------------ Cables usb
    ("Cables usb", "Cable USB-C a USB-A 1 m Trenzado", "Nexxt", 24.90, None, 300,
     "Carga rápida hasta 3 A y datos a 480 Mbps."),
    ("Cables usb", "Cable HDMI 2.0 de 3 m", "Nexxt", 46.00, None, 180,
     "4K a 60 Hz. Conectores chapados en oro."),
    ("Cables usb", "Extensión USB 3.0 de 5 m Activa", "Nexxt", 68.00, None, 90,
     "Con amplificador de señal para cámaras e impresoras."),
]


# ==========================================================================
# Generación
# ==========================================================================

def generar(fuentes: dict) -> None:
    partes: list[str] = []
    w = partes.append

    w(CABECERA)

    # ------------------------------------------------------------- ubigeo
    print("Ubigeo…")
    departamentos, provincias, distritos = leer_ubigeo(fuentes["seeders"])
    print(f"  {len(departamentos)} departamentos, {len(provincias)} provincias, {len(distritos)} distritos")

    w("\n-- =========================================================================\n"
      "-- 1. Ubigeo del Perú\n"
      "-- =========================================================================\n"
      "--\n"
      "-- 25 departamentos, 194 provincias y 1 874 distritos. Se insertan por\n"
      "-- nombre y se resuelven las claves con subconsultas, no con identificadores\n"
      "-- fijos: así el archivo no depende de en qué orden asignó la secuencia.\n")

    w("\nINSERT INTO departamento (nombre) VALUES\n    "
      + ",\n    ".join(f"({sql(d)})" for d in departamentos) + ";\n")

    w("\nINSERT INTO provincia (fk_id_departamento, nombre)\n"
      "SELECT d.id_departamento, v.provincia\n"
      "FROM (VALUES\n    "
      + ",\n    ".join(f"({sql(dep)}, {sql(pro)})" for dep, pro in provincias)
      + "\n) AS v(departamento, provincia)\n"
        "JOIN departamento d ON d.nombre = v.departamento;\n")

    # En lotes: un solo VALUES de 1 874 filas hace que el plan de Postgres
    # ocupe más memoria de la que merece un seed.
    for inicio in range(0, len(distritos), 400):
        lote = distritos[inicio:inicio + 400]
        w("\nINSERT INTO distrito (fk_id_provincia, nombre)\n"
          "SELECT p.id_provincia, v.distrito\n"
          "FROM (VALUES\n    "
          + ",\n    ".join(f"({sql(dep)}, {sql(pro)}, {sql(dis)})" for dep, pro, dis in lote)
          + "\n) AS v(departamento, provincia, distrito)\n"
            "JOIN departamento d ON d.nombre = v.departamento\n"
            "JOIN provincia p ON p.fk_id_departamento = d.id_departamento AND p.nombre = v.provincia;\n")

    # ---------------------------------------------------------- imágenes
    print("Imágenes…")
    archivos: list[dict] = []   # filas de `archivo`
    por_clave: dict[str, str] = {}  # etiqueta lógica -> clave del archivo
    vistos: dict[str, str] = {}     # hash -> clave, para no duplicar

    def registrar(etiqueta: str, origen: pathlib.Path, destino: str, alt: str) -> None:
        if not origen.exists():
            return
        datos = convertir(origen, destino)
        if not datos:
            return
        # Deduplicación por contenido, igual que hace ServicioArchivo: el
        # volcado trae el mismo logotipo con varios nombres.
        if datos["hash"] in vistos:
            por_clave[etiqueta] = vistos[datos["hash"]]
            return
        datos["texto_alt"] = alt
        archivos.append(datos)
        vistos[datos["hash"]] = datos["clave"]
        por_clave[etiqueta] = datos["clave"]

    marcas = leer_volcado(fuentes["marcas"])
    categorias = leer_volcado(fuentes["categorias"])
    subcategorias = leer_volcado(fuentes["subcategorias"])

    for m in marcas:
        nombre = limpiar(m["brand_name"])
        if m.get("brand_logo"):
            registrar(f"marca:{nombre}", fuentes["logos"] / m["brand_logo"],
                      f"marca/{slug(nombre)}", f"Logotipo de {nombre}")

    for c in categorias:
        nombre = limpiar(c["category_name"])
        if c.get("category_image"):
            registrar(f"categoria:{nombre}", fuentes["cat_img"] / c["category_image"],
                      f"categoria/{slug(nombre)}", f"Categoría {nombre}")

    for s in subcategorias:
        nombre = limpiar(s["subcategory_name"])
        if s.get("subcategory_image"):
            registrar(f"subcategoria:{nombre}", fuentes["sub_img"] / s["subcategory_image"],
                      f"subcategoria/{slug(nombre)}", f"Subcategoría {nombre}")

    print(f"  {len(archivos)} archivos únicos")

    w("\n\n-- =========================================================================\n"
      "-- 2. Archivos\n"
      "-- =========================================================================\n"
      "--\n"
      "-- Imágenes reales, ya convertidas a WebP en cuatro tamaños por\n"
      "-- `database/semilla/generar.py` y servidas por el almacén local desde\n"
      "-- `backend/java/datos/archivos/semilla/`. No son marcadores: el ancho, el\n"
      "-- alto, el peso y el hash de cada fila salen del archivo de verdad.\n")

    for inicio in range(0, len(archivos), 60):
        lote = archivos[inicio:inicio + 60]
        w("\nINSERT INTO archivo (clave, nombre_original, tipo_mime, bytes, ancho, alto,"
          " texto_alt, hash_contenido, url_publica) VALUES\n    "
          + ",\n    ".join(
              f"({sql(a['clave'])}, {sql(a['nombre_original'])}, 'image/webp', {a['bytes']}, "
              f"{a['ancho']}, {a['alto']}, {sql(a['texto_alt'])}, {sql(a['hash'])}, "
              f"{sql(URL_BASE + '/' + a['clave'])})"
              for a in lote) + ";\n")

    filas_var = []
    for a in archivos:
        for nombre, v in a["variantes"].items():
            filas_var.append((a["clave"], nombre, v))
    for inicio in range(0, len(filas_var), 120):
        lote = filas_var[inicio:inicio + 120]
        w("\nINSERT INTO archivo_variante (fk_id_archivo, nombre, clave, url_publica, ancho, alto, bytes)\n"
          "SELECT a.id_archivo, v.nombre, v.clave, v.url, v.ancho, v.alto, v.bytes\n"
          "FROM (VALUES\n    "
          + ",\n    ".join(
              f"({sql(clave)}, {sql(nombre)}, {sql(v['clave'])}, "
              f"{sql(URL_BASE + '/' + v['clave'])}, {v['ancho']}, {v['alto']}, {v['bytes']})"
              for clave, nombre, v in lote)
          + "\n) AS v(archivo, nombre, clave, url, ancho, alto, bytes)\n"
            "JOIN archivo a ON a.clave = v.archivo;\n")

    # ---------------------------------------------------------- catálogo
    print("Catálogo…")
    w("\n\n-- =========================================================================\n"
      "-- 3. Marcas, categorías y subcategorías\n"
      "-- =========================================================================\n"
      "--\n"
      "-- Catálogo real de una ferretería: 219 marcas con su logotipo, 10\n"
      "-- categorías y 26 subcategorías. El logotipo se enlaza por la clave del\n"
      "-- archivo, que es única.\n")

    w("\nINSERT INTO marca (nombre, slug, descripcion, fk_id_archivo, activa)\n"
      "SELECT v.nombre, v.slug, v.descripcion, a.id_archivo, true\n"
      "FROM (VALUES\n    ")
    filas = []
    for m in marcas:
        nombre = limpiar(m["brand_name"])
        filas.append(
            f"({sql(nombre)}, {sql(slug(nombre))}, {sql(limpiar(m.get('description')) or None)}, "
            f"{sql(por_clave.get(f'marca:{nombre}'))})")
    w(",\n    ".join(filas))
    w("\n) AS v(nombre, slug, descripcion, clave_archivo)\n"
      "LEFT JOIN archivo a ON a.clave = v.clave_archivo;\n")

    w("\nINSERT INTO categoria (nombre, slug, descripcion, fk_id_archivo, orden, activa)\n"
      "SELECT v.nombre, v.slug, v.descripcion, a.id_archivo, v.orden, true\n"
      "FROM (VALUES\n    ")
    filas = []
    for i, c in enumerate(categorias, start=1):
        nombre = limpiar(c["category_name"])
        filas.append(
            f"({sql(nombre)}, {sql(slug(nombre))}, {sql(limpiar(c.get('description')) or None)}, "
            f"{sql(por_clave.get(f'categoria:{nombre}'))}, {i})")
    w(",\n    ".join(filas))
    w("\n) AS v(nombre, slug, descripcion, clave_archivo, orden)\n"
      "LEFT JOIN archivo a ON a.clave = v.clave_archivo;\n")

    cat_por_id = {c["category_id"]: limpiar(c["category_name"]) for c in categorias}

    w("\nINSERT INTO subcategoria (fk_id_categoria, nombre, slug, descripcion, fk_id_archivo, orden, activa)\n"
      "SELECT c.id_categoria, v.nombre, v.slug, v.descripcion, a.id_archivo, v.orden, true\n"
      "FROM (VALUES\n    ")
    filas = []
    for i, s in enumerate(subcategorias, start=1):
        nombre = limpiar(s["subcategory_name"])
        padre = cat_por_id.get(s["category_id"])
        # El slug lleva el nombre de la categoría detrás: «Accesorios» puede
        # existir bajo dos categorías y el slug es único en todo el catálogo.
        filas.append(
            f"({sql(padre)}, {sql(nombre)}, {sql(slug(nombre) + '-' + slug(padre or ''))}, "
            f"{sql(limpiar(s.get('description')) or None)}, "
            f"{sql(por_clave.get(f'subcategoria:{nombre}'))}, {i})")
    w(",\n    ".join(filas))
    w("\n) AS v(categoria, nombre, slug, descripcion, clave_archivo, orden)\n"
      "JOIN categoria c ON c.nombre = v.categoria\n"
      "LEFT JOIN archivo a ON a.clave = v.clave_archivo;\n")

    # --------------------------------------------------------- productos
    print(f"Productos… ({len(PRODUCTOS)})")
    nombres_sub = {limpiar(s["subcategory_name"]) for s in subcategorias}
    for p in PRODUCTOS:
        if p[0] not in nombres_sub:
            raise SystemExit(f"La subcategoría «{p[0]}» no existe en el volcado")

    w("\n\n-- =========================================================================\n"
      "-- 4. Productos\n"
      "-- =========================================================================\n"
      "--\n"
      "-- El volcado no traía productos, así que el catálogo se escribe en\n"
      "-- `generar.py` con precios de referencia del mercado peruano.\n"
      "--\n"
      "-- La imagen es el logotipo de la marca. No es una foto del producto y no\n"
      "-- pretende serlo: es lo que hay, es real, y una rejilla de logotipos de\n"
      "-- marcas que existen se lee mucho mejor que una de imágenes de relleno\n"
      "-- sacadas de un servicio aleatorio.\n")

    w("\nINSERT INTO producto (sku, nombre, slug, descripcion_corta, descripcion,\n"
      "                      fk_id_subcategoria, fk_id_marca, precio, precio_anterior, stock,\n"
      "                      destacado, activo, calificacion_promedio, calificacion_conteo)\n"
      "SELECT v.sku, v.nombre, v.slug, v.descripcion_corta, v.descripcion,\n"
      "       s.id_subcategoria, m.id_marca, v.precio, v.precio_anterior, v.stock,\n"
      "       v.destacado, true,\n"
      "       -- En cero, y no una fórmula sobre el SKU: el promedio y el conteo\n"
      "       -- se derivan de las opiniones reales (RN-093). Las opiniones de\n"
      "       -- demostración se siembran al final, después de las órdenes -la\n"
      "       -- insignia de compra verificada necesita una orden ENTREGADA-, y\n"
      "       -- es ese bloque el que deja estas dos columnas con su valor.\n"
      "       0, 0\n"
      "FROM (VALUES\n    ")
    filas = []
    for i, (sub, nombre, marca, precio, anterior, stock, corta) in enumerate(PRODUCTOS, start=1):
        sku = f"FER-{slug(sub)[:6].upper()}-{i:03d}"
        descripcion = (
            f"{corta}\n\n"
            f"**Marca:** {marca}\n\n"
            "### Detalles\n\n"
            "- Producto nuevo, en su empaque original.\n"
            "- Garantía del fabricante contra defectos de fábrica.\n"
            "- Factura o boleta electrónica al momento de la compra.\n"
        )
        filas.append(
            f"({sql(sku)}, {sql(nombre)}, {sql(slug(nombre))}, {sql(corta)}, {sql(descripcion)},\n     "
            f"{sql(sub)}, {sql(marca)}, {precio}, {sql(anterior)}, {stock}, "
            f"{sql(anterior is not None)})")
    w(",\n    ".join(filas))
    w("\n) AS v(sku, nombre, slug, descripcion_corta, descripcion, subcategoria, marca,\n"
      "       precio, precio_anterior, stock, destacado)\n"
      "JOIN subcategoria s ON s.nombre = v.subcategoria\n"
      "LEFT JOIN marca m ON m.nombre = v.marca;\n")

    w("\n-- La imagen del producto es la de su marca.\n"
      "INSERT INTO producto_imagen (fk_id_producto, fk_id_archivo, orden)\n"
      "SELECT p.id_producto, m.fk_id_archivo, 0\n"
      "FROM producto p\n"
      "JOIN marca m ON m.id_marca = p.fk_id_marca\n"
      "WHERE m.fk_id_archivo IS NOT NULL;\n"
      "\n-- Y si la marca no tiene logotipo, la de su subcategoría: publicar exige\n"
      "-- al menos una imagen (RN-009), así que ninguno puede quedarse sin ella.\n"
      "INSERT INTO producto_imagen (fk_id_producto, fk_id_archivo, orden)\n"
      "SELECT p.id_producto, s.fk_id_archivo, 0\n"
      "FROM producto p\n"
      "JOIN subcategoria s ON s.id_subcategoria = p.fk_id_subcategoria\n"
      "WHERE s.fk_id_archivo IS NOT NULL\n"
      "  AND NOT EXISTS (SELECT 1 FROM producto_imagen pi WHERE pi.fk_id_producto = p.id_producto);\n"
      "\n-- Lo que aun así se quedó sin imagen no puede estar publicado.\n"
      "UPDATE producto SET activo = false\n"
      "WHERE NOT EXISTS (SELECT 1 FROM producto_imagen pi WHERE pi.fk_id_producto = producto.id_producto);\n")

    w(RESTO)

    SALIDA_SQL.write_text("".join(partes), encoding="utf-8")
    print(f"\n{SALIDA_SQL.relative_to(RAIZ)}  ({SALIDA_SQL.stat().st_size // 1024} kB)")
    print(f"{SALIDA_IMG.relative_to(RAIZ)}  ({sum(1 for _ in SALIDA_IMG.rglob('*.webp'))} imágenes)")


CABECERA = """-- ===========================================================================
-- DATOS
-- ===========================================================================
--
-- GENERADO por `database/semilla/generar.py`. No se edita a mano: se cambia el
-- generador y se vuelve a ejecutar.
--
-- Contiene el ubigeo del Perú, el catálogo con marcas y logotipos reales, y un
-- histórico de demostración de 90 días. Todo determinista: las mismas entradas
-- producen el mismo archivo. Un seed con random() da una tienda distinta en
-- cada máquina y hace imposible decir «aquí debería salir 12».
--
-- Las claves foráneas se resuelven por nombre con subconsultas, nunca con
-- identificadores fijos: así no depende de en qué orden repartió la secuencia.
"""

RESTO = """

-- =========================================================================
-- 5. Identidad del panel
-- =========================================================================
--
-- La contraseña es un hash BCrypt de coste 12 de `AdminRetail2026!`. Está
-- escrita en el repositorio a propósito: es una cuenta de demostración, y una
-- credencial que hay que pedirle a alguien convierte «levanta esto» en un
-- correo. **Cámbiala antes de exponer esto a cualquier red.**

INSERT INTO administrador (usuario, hash_contrasena, nombre, rol) VALUES
    ('admin',
     '{bcrypt}$2a$12$zTUp8.yCL8ZdVPCEocdf3eKuUmnbafp9qBRzqsLXt8Hd9EckVdeka',
     'Administrador General',
     'SUPERADMINISTRADOR');

-- =========================================================================
-- 6. Cupones
-- =========================================================================

INSERT INTO cupon (codigo, tipo, valor, subtotal_minimo, inicia_en, termina_en, usos_maximos) VALUES
    ('BIENVENIDA10', 'PORCENTAJE', 10,    50.00,  now() - interval '1 day', now() + interval '90 days', NULL),
    ('ENVIO20',      'MONTO_FIJO', 20.00, 100.00, now() - interval '1 day', now() + interval '90 days', 500),
    ('VERANO25',     'PORCENTAJE', 25,    300.00, now() - interval '1 day', now() + interval '30 days', 100),
    -- Caducado a propósito: sirve para comprobar que la tienda lo rechaza.
    ('EXPIRADO',     'PORCENTAJE', 50,    0,      now() - interval '60 days', now() - interval '30 days', NULL);

-- =========================================================================
-- 7. Clientes
-- =========================================================================

INSERT INTO cliente (email, email_verificado, nombre, telefono, creado_en)
SELECT
    lower(translate(replace(v.nombre, ' ', '.'), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN')) || '@example.com',
    v.verificado,
    v.nombre,
    v.telefono,
    now() - make_interval(days => v.dias_atras)
FROM (VALUES
    ('María Quispe',    true,  '987654321', 120),
    ('Carlos Mendoza',  true,  '987112233', 110),
    ('Lucía Fernández', true,  '986554433', 100),
    ('Jorge Ramírez',   false, '985443322',  95),
    ('Ana Torres',      true,  '984332211',  80),
    ('Diego Salazar',   true,  '983221100',  60),
    ('Valeria Chávez',  false, '982110099',  40),
    ('Renzo Paredes',   true,  '981009988',  25)
) AS v(nombre, verificado, telefono, dias_atras);

-- Una dirección para cada uno, con su punto en el mapa. Las coordenadas son de
-- verdad: caen donde dice la dirección, que es lo que hace demostrable el
-- selector de ubicación.
INSERT INTO direccion_cliente (fk_id_cliente, fk_id_distrito, etiqueta, destinatario, telefono,
                               calle, numero, referencia, latitud, longitud, predeterminada)
SELECT c.id_cliente, d.id_distrito, 'Casa', c.nombre, c.telefono,
       v.calle, v.numero, v.referencia, v.lat, v.lng, true
FROM (VALUES
    ('María Quispe',    'LIMA',       'LIMA',    'LINCE',      'Av. Arequipa',      '1234', 'Frente al parque Castilla',   -12.0894, -77.0350),
    ('Carlos Mendoza',  'AREQUIPA',   'AREQUIPA','AREQUIPA',   'Jr. Puno',          '456',  'A media cuadra de la plaza',  -16.3989, -71.5350),
    ('Lucía Fernández', 'LA LIBERTAD','TRUJILLO','TRUJILLO',   'Calle Los Pinos',   '78',   'Portón verde',                 -8.1120, -79.0288),
    ('Jorge Ramírez',   'CUSCO',      'CUSCO',   'CUSCO',      'Av. El Sol',        '990',  'Edificio Inti, piso 3',       -13.5199, -71.9757),
    ('Ana Torres',      'LIMA',       'LIMA',    'MIRAFLORES', 'Av. Grau',          '321',  'Dpto. 502',                   -12.1195, -77.0284),
    ('Diego Salazar',   'JUNIN',      'HUANCAYO','HUANCAYO',   'Jr. Junín',         '55',   'Al costado de la ferretería', -12.0684, -75.2104),
    ('Valeria Chávez',  'LIMA',       'LIMA',    'SAN MIGUEL', 'Av. La Marina',     '2100', 'Torre B, oficina 12',         -12.0776, -77.0862),
    ('Renzo Paredes',   'PIURA',      'PIURA',   'PIURA',      'Calle Bolognesi',   '14',   'Casa de dos pisos',           -5.1945,  -80.6328)
) AS v(nombre, departamento, provincia, distrito, calle, numero, referencia, lat, lng)
JOIN cliente c ON c.nombre = v.nombre
JOIN departamento dep ON dep.nombre = v.departamento
JOIN provincia pro ON pro.fk_id_departamento = dep.id_departamento AND pro.nombre = v.provincia
JOIN distrito d ON d.fk_id_provincia = pro.id_provincia AND d.nombre = v.distrito;

-- =========================================================================
-- 8. Histórico de órdenes
-- =========================================================================
--
-- 90 días de ventas para que el tablero muestre datos reales desde el primer
-- arranque: un tablero de ventas sin ventas no es un tablero, es una maqueta.

-- Vistas: entre 40 y 940, repartidas de forma estable por producto.
UPDATE producto SET vistas = 40 + (id_producto * 137) % 900;

WITH dias AS (
    SELECT d AS dia, (now()::date - d)::timestamptz + interval '9 hours' AS apertura
    FROM generate_series(0, 89) AS d
),
pedidos AS (
    SELECT dias.dia,
           n,
           dias.apertura + (n * interval '3 hours') AS creada,
           (dias.dia * 7 + n * 13) AS semilla
    FROM dias
    CROSS JOIN generate_series(1, 3) AS n
    -- Deja fuera un 40% de las combinaciones: algunos días tienen tres
    -- órdenes, otros una y otros ninguna. Un histórico plano no se parece a
    -- ninguna tienda.
    WHERE (dias.dia * 7 + n * 13) % 5 < 3
)
INSERT INTO orden (numero, nombre_contacto, email, telefono, direccion,
                   subtotal, descuento, total, estado, creado_en, actualizado_en)
SELECT
    'ORD-' || to_char(p.creada, 'YYYY') || '-' || upper(substr(md5(p.dia::text || '-' || p.n::text), 1, 6)),
    c.nombre,
    lower(translate(split_part(c.nombre, ' ', 1), 'áéíóúÁÉÍÓÚñÑ', 'aeiouAEIOUnN'))
        || (p.semilla % 97) || '@example.com',
    '9' || lpad(((p.semilla * 137) % 100000000)::text, 8, '0'),
    c.direccion,
    0, 0, 0,
    CASE
        WHEN p.dia <= 2            THEN 'PENDIENTE'
        WHEN p.semilla % 13 = 0    THEN 'CANCELADA'
        WHEN p.dia <= 5            THEN 'PAGADA'
        WHEN p.dia <= 12           THEN 'ENVIADA'
        ELSE 'ENTREGADA'
    END,
    p.creada,
    p.creada
FROM pedidos p
JOIN LATERAL (
    SELECT nombre, direccion
    FROM (VALUES
        ('María Quispe',     'Av. Arequipa 1234, Lince, Lima'),
        ('Carlos Mendoza',   'Jr. Puno 456, Cercado, Arequipa'),
        ('Lucía Fernández',  'Calle Los Pinos 78, Trujillo'),
        ('Jorge Ramírez',    'Av. El Sol 990, Cusco'),
        ('Ana Torres',       'Av. Grau 321, Miraflores, Lima'),
        ('Diego Salazar',    'Jr. Junín 55, Huancayo'),
        ('Valeria Chávez',   'Av. La Marina 2100, San Miguel, Lima'),
        ('Renzo Paredes',    'Calle Bolognesi 14, Piura')
    ) AS v(nombre, direccion)
    OFFSET (p.semilla % 8) LIMIT 1
) c ON true;

-- Líneas: entre 1 y 3 productos por orden, sin repetir producto en la misma.
WITH catalogo AS (
    SELECT p.id_producto, p.nombre, p.sku, p.precio,
           row_number() OVER (ORDER BY p.id_producto) - 1 AS posicion,
           count(*) OVER () AS cuantos
    FROM producto p
    WHERE p.activo
)
INSERT INTO item_orden (fk_id_orden, fk_id_producto, nombre_producto, sku, precio_unitario, cantidad, total_linea)
SELECT DISTINCT ON (o.id_orden, c.id_producto)
    o.id_orden,
    c.id_producto,
    c.nombre,
    c.sku,
    c.precio,
    1 + ((o.id_orden + k) % 3),
    c.precio * (1 + ((o.id_orden + k) % 3))
FROM orden o
CROSS JOIN generate_series(0, 2) AS k
JOIN catalogo c ON c.posicion = ((o.id_orden * 17 + k * 31) % c.cuantos)
WHERE k <= (o.id_orden % 3);

-- Los importes se calculan desde las líneas, nunca al revés: si alguna vez no
-- cuadran, el defecto está en quien escribió la orden, no en el tablero.
UPDATE orden o
SET subtotal = suma.importe,
    descuento = CASE WHEN o.id_orden % 5 = 0 THEN round(suma.importe * 0.10, 2) ELSE 0 END,
    codigo_cupon = CASE WHEN o.id_orden % 5 = 0 THEN 'BIENVENIDA10' ELSE NULL END,
    total = suma.importe - CASE WHEN o.id_orden % 5 = 0 THEN round(suma.importe * 0.10, 2) ELSE 0 END
FROM (SELECT fk_id_orden, sum(total_linea) AS importe FROM item_orden GROUP BY fk_id_orden) suma
WHERE suma.fk_id_orden = o.id_orden;

-- Una orden sin líneas no tiene sentido; si la aleatoriedad dejó alguna, fuera.
DELETE FROM orden WHERE id_orden NOT IN (SELECT fk_id_orden FROM item_orden);

-- Las órdenes usan los mismos nombres que los clientes: se enlazan y se
-- normaliza el correo para que el de la orden y el de la cuenta coincidan.
UPDATE orden o
SET fk_id_cliente = c.id_cliente,
    email = c.email,
    telefono = c.telefono
FROM cliente c
WHERE c.nombre = o.nombre_contacto;

-- =========================================================================
-- 9. Favoritos de demostración
-- =========================================================================
--
-- Tres productos por cliente, elegidos de forma estable.

INSERT INTO favorito (fk_id_cliente, fk_id_producto, creado_en)
SELECT c.id_cliente, p.id_producto, now() - make_interval(days => ((c.id_cliente + p.id_producto) % 30)::int)
FROM cliente c
JOIN LATERAL (
    SELECT id_producto FROM producto
    WHERE activo AND (id_producto + c.id_cliente) % 7 = 0
    ORDER BY id_producto
    LIMIT 3
) p ON true;

-- =========================================================================
-- 10. Opiniones de demostración (RN-090 … RN-094)
-- =========================================================================
--
-- Van después de las órdenes porque dependen de ellas: una opinión lleva la
-- insignia de compra verificada cuando su autor tiene una orden ENTREGADA con
-- ese producto (RN-092), y aquí eso se cumple de verdad en lugar de escribirse
-- a mano.
--
-- El promedio y el conteo del producto NO se siembran: se derivan al final de
-- este bloque con la misma sentencia que usa el servicio (RN-093). Si alguna
-- vez el número de la tienda no cuadra con las opiniones que se leen, el
-- defecto está en quien escribió la opinión, no en la ficha.

-- Verificadas: una por (cliente, producto) salida de sus compras entregadas.
-- La condición del módulo deja fuera dos de cada tres para que no todo producto
-- comprado acabe con opinión -una tienda en la que opina el 100% de quien
-- compra no se parece a ninguna tienda.
INSERT INTO opinion (fk_id_producto, fk_id_cliente, calificacion, titulo, cuerpo,
                     compra_verificada, creado_en, creado_por, actualizado_en, actualizado_por)
SELECT DISTINCT ON (io.fk_id_producto, o.fk_id_cliente)
       io.fk_id_producto,
       o.fk_id_cliente,
       v.calificacion,
       v.titulo,
       v.cuerpo,
       true,
       o.creado_en + interval '6 days',
       'cliente:' || o.fk_id_cliente,
       o.creado_en + interval '6 days',
       'cliente:' || o.fk_id_cliente
FROM orden o
JOIN item_orden io ON io.fk_id_orden = o.id_orden
JOIN LATERAL (
    SELECT t.calificacion, t.titulo, t.cuerpo
    FROM (VALUES
        (0, 5, 'Cumple de sobra',
            'Llegó bien embalado y funciona como esperaba. Lo uso casi a diario en obra y no me ha dado ni un problema.'),
        (1, 4, 'Buena relación precio-calidad',
            'Por lo que cuesta está muy bien. Le pongo cuatro y no cinco porque el acabado podría cuidarse más, pero cumple.'),
        (2, 5, 'Lo volvería a comprar',
            'Segunda vez que lo pido. Resistente, y esta vez llegó incluso antes de lo previsto.'),
        (3, 3, 'Correcto, sin más',
            'Hace lo que promete. Por las fotos esperaba algo más robusto, aunque para un uso ocasional va perfecto.'),
        (4, 4, 'Aguanta el ritmo del taller',
            'Lo tengo desde hace unas semanas y se le nota el uso diario sin que haya perdido nada.'),
        (5, 5, 'El pedido llegó completo',
            'Todo tal cual la descripción y bien protegido. Repetiré con esta tienda.'),
        (6, 2, 'Esperaba más por el precio',
            'A mí no terminó de convencerme: el material se siente liviano. No es malo, pero hay opciones mejores por ahí.'),
        (7, 4, 'Buen material',
            'Se nota la calidad en la mano. Le falta un estuche para guardarlo, y por eso no le doy las cinco.')
    ) AS t(pos, calificacion, titulo, cuerpo)
    WHERE t.pos = (io.fk_id_producto * 3 + o.fk_id_cliente) % 8
) v ON true
WHERE o.estado = 'ENTREGADA'
  AND o.fk_id_cliente IS NOT NULL
  AND (io.fk_id_producto + o.fk_id_cliente) % 3 = 0
ORDER BY io.fk_id_producto, o.fk_id_cliente, o.creado_en;

-- Sin compra verificada: opinar no exige haber comprado (RN-091), y la tienda
-- tiene que poder enseñar las dos formas de la tarjeta -con insignia y sin
-- ella- desde el primer arranque.
INSERT INTO opinion (fk_id_producto, fk_id_cliente, calificacion, titulo, cuerpo,
                     compra_verificada, creado_en, creado_por, actualizado_en, actualizado_por)
SELECT p.id_producto,
       c.id_cliente,
       v.calificacion,
       v.titulo,
       v.cuerpo,
       false,
       now() - make_interval(days => ((c.id_cliente * 5 + p.id_producto) % 45)::int),
       'cliente:' || c.id_cliente,
       now() - make_interval(days => ((c.id_cliente * 5 + p.id_producto) % 45)::int),
       'cliente:' || c.id_cliente
FROM cliente c
JOIN LATERAL (
    SELECT id_producto FROM producto
    WHERE activo
      AND eliminado_en IS NULL
      AND (id_producto * 11 + c.id_cliente) % 23 = 0
      AND NOT EXISTS (
          SELECT 1 FROM opinion o
           WHERE o.fk_id_producto = producto.id_producto AND o.fk_id_cliente = c.id_cliente)
    ORDER BY id_producto
    LIMIT 2
) p ON true
JOIN LATERAL (
    SELECT t.calificacion, t.titulo, t.cuerpo
    FROM (VALUES
        (0, 4, 'Justo lo que buscaba',
            'Lo tenía fichado hace tiempo y coincide con lo que dice la ficha. Sin sorpresas.'),
        (1, 5, 'Muy buena compra',
            'La marca no falla y el precio está por debajo de lo que vi en otros sitios.'),
        (2, 3, 'Bien para empezar',
            'Para un uso casero cumple de sobra. Si le vas a dar mucha caña, mira algo de gama superior.'),
        (3, 4, 'Cumple lo que promete',
            'Ni más ni menos que lo anunciado, que en herramientas es justo lo que uno quiere.')
    ) AS t(pos, calificacion, titulo, cuerpo)
    WHERE t.pos = (c.id_cliente + p.id_producto) % 4
) v ON true;

-- Y aquí se derivan las dos columnas del producto (RN-093). Es la misma
-- sentencia que ejecuta el servicio cada vez que alguien opina: la semilla no
-- tiene una vía propia para escribir un promedio.
UPDATE producto p
SET calificacion_promedio = coalesce(x.promedio, 0),
    calificacion_conteo   = coalesce(x.conteo, 0)
FROM (
    SELECT fk_id_producto,
           round(avg(calificacion)::numeric, 1) AS promedio,
           count(*)                             AS conteo
    FROM opinion
    WHERE eliminado_en IS NULL
    GROUP BY fk_id_producto
) x
WHERE x.fk_id_producto = p.id_producto;

-- =========================================================================
-- 11. Conversaciones y notificaciones
-- =========================================================================

INSERT INTO conversacion (fk_id_cliente, fk_id_orden, asunto, ultimo_mensaje_en, no_leidos_admin, creado_en)
SELECT c.id_cliente,
       o.id_orden,
       v.asunto,
       now() - make_interval(hours => v.horas_atras),
       v.no_leidos,
       now() - make_interval(hours => v.horas_atras + 2)
FROM (VALUES
    ('María Quispe',    '¿Cuándo llega mi pedido?',        3, 1),
    ('Carlos Mendoza',  'Consulta sobre la garantía',     28, 0),
    ('Valeria Chávez',  'Quiero cambiar la dirección',     9, 2)
) AS v(nombre, asunto, horas_atras, no_leidos)
JOIN cliente c ON c.nombre = v.nombre
LEFT JOIN LATERAL (
    SELECT id_orden FROM orden WHERE fk_id_cliente = c.id_cliente ORDER BY creado_en DESC LIMIT 1
) o ON true;

INSERT INTO mensaje (fk_id_conversacion, autor, autor_nombre, cuerpo, enviado_en, leido_en)
SELECT conv.id_conversacion,
       v.autor,
       CASE WHEN v.autor = 'CLIENTE' THEN cli.nombre ELSE 'Administrador General' END,
       v.cuerpo,
       conv.creado_en + make_interval(mins => v.minutos),
       CASE WHEN v.leido THEN conv.creado_en + make_interval(mins => v.minutos + 5) ELSE NULL END
FROM conversacion conv
JOIN cliente cli ON cli.id_cliente = conv.fk_id_cliente
JOIN LATERAL (
    VALUES
        ('CLIENTE',       'Hola, buenas tardes. Quería consultar por mi compra.', 0,  true),
        ('ADMINISTRADOR', 'Hola, con gusto te ayudo. Déjame revisar tu orden.',   12, true),
        ('CLIENTE',       'Gracias, quedo atento.',                               35, false)
) AS v(autor, cuerpo, minutos, leido) ON true;

UPDATE conversacion c
SET ultimo_mensaje_en = m.ultimo
FROM (SELECT fk_id_conversacion, max(enviado_en) AS ultimo FROM mensaje GROUP BY fk_id_conversacion) m
WHERE m.fk_id_conversacion = c.id_conversacion;

-- Una notificación por cada conversación con mensajes sin leer, para que la
-- campana del panel tenga algo que mostrar desde el primer arranque.
INSERT INTO notificacion (tipo, severidad, titulo, detalle, enlace, clave_unicidad, creado_en)
SELECT 'MENSAJE_CLIENTE',
       'AVISO',
       'Mensaje de ' || cli.nombre,
       c.asunto,
       '/conversaciones?abrir=' || c.id_conversacion,
       'conversacion:' || c.id_conversacion,
       c.ultimo_mensaje_en
FROM conversacion c
JOIN cliente cli ON cli.id_cliente = c.fk_id_cliente
WHERE c.no_leidos_admin > 0;

INSERT INTO notificacion (tipo, severidad, titulo, detalle, enlace, clave_unicidad, creado_en)
SELECT 'STOCK_AGOTADO',
       'URGENTE',
       'Sin stock: ' || p.nombre,
       'El producto sigue publicado y no se puede comprar.',
       '/productos/' || p.id_producto,
       'stock-agotado:' || p.id_producto,
       now() - interval '4 hours'
FROM producto p
WHERE p.activo = true AND p.stock = 0 AND p.eliminado_en IS NULL;
"""


FUENTES = {
    "seeders": pathlib.Path(r"D:\hidrojass\hidrojass-sistema\Backend\database\seeders"),
    "marcas": pathlib.Path(r"C:\Users\luisl\Downloads\data\ospos_brands.json"),
    "categorias": pathlib.Path(r"C:\Users\luisl\Downloads\data\ospos_categories.json"),
    "subcategorias": pathlib.Path(r"C:\Users\luisl\Downloads\data\ospos_subcategories.json"),
    "logos": pathlib.Path(r"C:\Users\luisl\Downloads\data\images\uploads\brand_logos"),
    "cat_img": pathlib.Path(r"C:\Users\luisl\Downloads\data\images\uploads\category_pics"),
    "sub_img": pathlib.Path(r"C:\Users\luisl\Downloads\data\images\uploads\subcategory_pics"),
}


if __name__ == "__main__":
    generar(FUENTES)
