-- =========================================================================
-- V005 · Vuelve a derivar las URL públicas. Otra vez.
-- =========================================================================
--
-- V004 hizo esto mismo y no sirvió de nada, por un fallo que no estaba en el
-- SQL sino en el compose del despliegue: `ALMACEN_URL_PUBLICA` estaba escrita
-- a fuego como `https://<dominio-api>/archivos`, así que el valor puesto en el
-- entorno del servicio se ignoraba en silencio. El marcador de Flyway leía esa
-- misma variable, y V004 reescribió las URL exactamente al valor roto que ya
-- tenían: 246 filas «corregidas» a lo mismo.
--
-- Arreglado el compose, hace falta esta migración porque V004 ya está aplicada
-- y Flyway no reejecuta lo aplicado.
--
-- ------------------------------------------------------------------------
-- Esta es la TERCERA migración que hace lo mismo (V003, V004, V005)
-- ------------------------------------------------------------------------
-- Y tres veces ya no es mala suerte: es el diseño. La URL pública es un dato
-- **derivado** —la clave más una base que depende del despliegue— y está
-- persistida. Mientras siga así, cada cambio de almacén, de dominio o de
-- bucket va a dejar filas apuntando a un sitio muerto y a pedir otra migración
-- como esta.
--
-- El arreglo de fondo está anotado como deuda D-16 en `docs/estado-y-brecha.md`:
-- que `ArchivoRespuesta` componga `base + clave` al construir la respuesta, en
-- lugar de devolver `archivo.getUrlPublica()`. Entonces la columna deja de
-- importar y esto no vuelve a pasar.

UPDATE archivo
   SET url_publica = '${urlarchivos}/' || clave;

UPDATE archivo_variante
   SET url_publica = '${urlarchivos}/' || clave;
