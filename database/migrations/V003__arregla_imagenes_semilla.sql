-- =========================================================================
-- V003 · Dos arreglos sobre las imágenes de la semilla
-- =========================================================================
--
-- V002 no se edita: Flyway guarda el checksum del archivo completo y cambiar
-- hasta un comentario impediría arrancar contra una base ya migrada. Lo que
-- hay que corregir va aquí.


-- -------------------------------------------------------------------------
-- 1. El host estaba escrito a mano en las URL públicas
-- -------------------------------------------------------------------------
--
-- V002 insertó `url_publica` con `http://localhost:8080/archivos/...` dentro.
-- La aplicación devuelve ese valor tal cual —`ArchivoRespuesta` lee
-- `archivo.getUrlPublica()`, no lo recalcula—, así que en cualquier despliegue
-- que no sea la máquina de desarrollo el catálogo entero sale con las fotos
-- rotas: el navegador pide localhost y no hay nada ahí.
--
-- Solo afecta a la semilla. Lo que se suba desde el panel ya nace bien, porque
-- `ServicioArchivo` construye la URL con `almacen.urlPublica(clave)`.
--
-- `${urlarchivos}` es un marcador de Flyway que se resuelve al arrancar con el
-- valor de ALMACEN_URL_PUBLICA (ver application.yml). La URL es dato derivado
-- de la clave, y aquí se vuelve a derivar en lugar de arrastrar el host viejo.

UPDATE archivo
   SET url_publica = '${urlarchivos}/' || clave
 WHERE clave LIKE 'semilla/%';

UPDATE archivo_variante
   SET url_publica = '${urlarchivos}/' || clave
 WHERE clave LIKE 'semilla/%';


-- -------------------------------------------------------------------------
-- 2. Cuatro marcas mostraban el logotipo de otra marca
-- -------------------------------------------------------------------------
--
-- En V002, estas cuatro apuntaban a la clave equivocada mientras su propio
-- logotipo estaba en disco sin que ninguna fila lo referenciara:
--
--     Cemento Rumi  ->  logotipo de Cemento Yura
--     Cemento Rua   ->  logotipo de Cemento Yura
--     Plastisur     ->  logotipo de Vinilit
--     Terokal       ->  logotipo de Tekno
--
-- Rumi y Rua comparten el mismo archivo byte a byte (mismo SHA-256), así que
-- se inserta UNA fila y las dos marcas la referencian. Es lo que hace
-- `ServicioArchivo` al subir: comprueba `findByHashContenido` antes de crear
-- nada, y el catálogo deduplica imágenes a propósito.

INSERT INTO archivo (clave, nombre_original, tipo_mime, bytes, ancho, alto, texto_alt, hash_contenido, url_publica) VALUES
    ('semilla/marca/cemento-rumi-tarjeta.webp', 'cemento-rumi.webp', 'image/webp', 2768, 285, 241,
     'Logotipo de Cemento Rumi',
     '97d0e22ed0ab1c0e8370512a07f4fabbcbd76205c56ed787722845b4dbd5124d',
     '${urlarchivos}/semilla/marca/cemento-rumi-tarjeta.webp'),

    ('semilla/marca/plastisur-tarjeta.webp', 'plastisur.webp', 'image/webp', 3618, 225, 225,
     'Logotipo de Plastisur',
     '631f6f785e75d431e4d979c3b87f30f5ffa3f8136da461f7e2cc42b1d56c183f',
     '${urlarchivos}/semilla/marca/plastisur-tarjeta.webp'),

    ('semilla/marca/terokal-tarjeta.webp', 'terokal.webp', 'image/webp', 2508, 225, 225,
     'Logotipo de Terokal',
     '9f9561ad0800dfbe1c064f6264e72db6dbd2ee97b8dcbde92c08115b5552d7b8',
     '${urlarchivos}/semilla/marca/terokal-tarjeta.webp');

-- Las cuatro variantes de cada una. MINIATURA es el archivo pequeño; ORIGINAL,
-- TARJETA y DETALLE comparten el de tarjeta, que es el único tamaño que se
-- tiene. Es el mismo patrón que siguen las 207 marcas de V002: una imagen
-- nunca se amplía (ADR-0009).

INSERT INTO archivo_variante (fk_id_archivo, nombre, clave, url_publica, ancho, alto, bytes)
SELECT a.id_archivo, v.nombre, v.clave, '${urlarchivos}/' || v.clave, v.ancho, v.alto, v.bytes
FROM (VALUES
    ('semilla/marca/cemento-rumi-tarjeta.webp', 'ORIGINAL',  'semilla/marca/cemento-rumi-tarjeta.webp',   285, 241, 2768),
    ('semilla/marca/cemento-rumi-tarjeta.webp', 'DETALLE',   'semilla/marca/cemento-rumi-tarjeta.webp',   285, 241, 2768),
    ('semilla/marca/cemento-rumi-tarjeta.webp', 'TARJETA',   'semilla/marca/cemento-rumi-tarjeta.webp',   285, 241, 2768),
    ('semilla/marca/cemento-rumi-tarjeta.webp', 'MINIATURA', 'semilla/marca/cemento-rumi-miniatura.webp', 160, 135, 1594),

    ('semilla/marca/plastisur-tarjeta.webp',    'ORIGINAL',  'semilla/marca/plastisur-tarjeta.webp',      225, 225, 3618),
    ('semilla/marca/plastisur-tarjeta.webp',    'DETALLE',   'semilla/marca/plastisur-tarjeta.webp',      225, 225, 3618),
    ('semilla/marca/plastisur-tarjeta.webp',    'TARJETA',   'semilla/marca/plastisur-tarjeta.webp',      225, 225, 3618),
    ('semilla/marca/plastisur-tarjeta.webp',    'MINIATURA', 'semilla/marca/plastisur-miniatura.webp',    160, 160, 2392),

    ('semilla/marca/terokal-tarjeta.webp',      'ORIGINAL',  'semilla/marca/terokal-tarjeta.webp',        225, 225, 2508),
    ('semilla/marca/terokal-tarjeta.webp',      'DETALLE',   'semilla/marca/terokal-tarjeta.webp',        225, 225, 2508),
    ('semilla/marca/terokal-tarjeta.webp',      'TARJETA',   'semilla/marca/terokal-tarjeta.webp',        225, 225, 2508),
    ('semilla/marca/terokal-tarjeta.webp',      'MINIATURA', 'semilla/marca/terokal-miniatura.webp',      160, 160, 1708)
) AS v(clave_padre, nombre, clave, ancho, alto, bytes)
JOIN archivo a ON a.clave = v.clave_padre;

-- Y se reenlazan las marcas. Rumi y Rua a la misma fila.

UPDATE marca
   SET fk_id_archivo = (SELECT id_archivo FROM archivo WHERE clave = 'semilla/marca/cemento-rumi-tarjeta.webp')
 WHERE slug IN ('cemento-rumi', 'cemento-rua');

UPDATE marca
   SET fk_id_archivo = (SELECT id_archivo FROM archivo WHERE clave = 'semilla/marca/plastisur-tarjeta.webp')
 WHERE slug = 'plastisur';

UPDATE marca
   SET fk_id_archivo = (SELECT id_archivo FROM archivo WHERE clave = 'semilla/marca/terokal-tarjeta.webp')
 WHERE slug = 'terokal';
