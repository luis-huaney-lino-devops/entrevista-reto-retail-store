-- =========================================================================
-- V004 · Vuelve a derivar las URL públicas de los archivos
-- =========================================================================
--
-- V003 ya hizo esto una vez, con el almacén en `local`: dejó las URL
-- apuntando a https://<dominio-api>/archivos/... Al pasar a R2
-- (ALMACEN_TIPO=r2) esas URL murieron de golpe, porque
-- `ArchivoLocalControlador` solo existe cuando el almacén es local
-- (@ConditionalOnProperty) y nadie sirve ya /archivos.
--
-- Cambiar ALMACEN_URL_PUBLICA no arregla lo ya escrito: Flyway no vuelve a
-- ejecutar una migración aplicada. De ahí esta.
--
-- Alcance: TODAS las filas, no solo la semilla. Lo que se subió desde el panel
-- mientras el almacén era local también quedó apuntando a /archivos, y esos
-- bytes tampoco están en el bucket. Se reconstruye la URL de todo y se sube
-- lo que falte con deploy/subir-semilla-r2.sh.
--
-- ------------------------------------------------------------------------
-- NOTA para la próxima vez
-- ------------------------------------------------------------------------
-- Esta es la segunda migración que hace exactamente lo mismo, y eso es la
-- señal de que el arreglo de fondo está en otro sitio: la URL es un dato
-- DERIVADO de la clave más una base que depende del despliegue, así que no
-- debería estar persistida. Mientras `ArchivoRespuesta` siga devolviendo
-- `archivo.getUrlPublica()` en lugar de componer `base + clave` al leer, cada
-- cambio de almacén o de dominio va a necesitar otra migración como esta.

UPDATE archivo
   SET url_publica = '${urlarchivos}/' || clave;

UPDATE archivo_variante
   SET url_publica = '${urlarchivos}/' || clave;
