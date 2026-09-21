-- Eliminación lógica.
--
-- Nada se borra: se marca cuándo y **quién** lo eliminó. Un borrado físico
-- destruye la respuesta a «¿quién quitó este producto y cuándo?», que es
-- exactamente la pregunta que se hace cuando algo desaparece del catálogo sin
-- explicación.

ALTER TABLE marca         ADD COLUMN eliminado_en timestamptz, ADD COLUMN eliminado_por varchar(60);
ALTER TABLE categoria     ADD COLUMN eliminado_en timestamptz, ADD COLUMN eliminado_por varchar(60);
ALTER TABLE subcategoria  ADD COLUMN eliminado_en timestamptz, ADD COLUMN eliminado_por varchar(60);
ALTER TABLE producto      ADD COLUMN eliminado_en timestamptz, ADD COLUMN eliminado_por varchar(60);
ALTER TABLE cupon         ADD COLUMN eliminado_en timestamptz, ADD COLUMN eliminado_por varchar(60);
ALTER TABLE administrador ADD COLUMN eliminado_en timestamptz, ADD COLUMN eliminado_por varchar(60);

-- `archivo` queda fuera: es inmutable (RN-075) y solo se borra cuando no lo
-- referencia nadie, así que no hay nada que conservar ni a quién explicárselo.
-- `orden`, `carrito` y `conversacion` también: una orden es un hecho ocurrido
-- y no se elimina, y un carrito es un estado transitorio.

-- Las dos columnas van juntas o no van: una fila con fecha de eliminación y
-- sin autor no responde a la pregunta para la que existe la columna.
ALTER TABLE marca         ADD CONSTRAINT ck_marca_eliminado         CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));
ALTER TABLE categoria     ADD CONSTRAINT ck_categoria_eliminado     CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));
ALTER TABLE subcategoria  ADD CONSTRAINT ck_subcategoria_eliminado  CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));
ALTER TABLE producto      ADD CONSTRAINT ck_producto_eliminado      CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));
ALTER TABLE cupon         ADD CONSTRAINT ck_cupon_eliminado         CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));
ALTER TABLE administrador ADD CONSTRAINT ck_administrador_eliminado CHECK ((eliminado_en IS NULL) = (eliminado_por IS NULL));

-- ---------------------------------------------------------------------------
-- Unicidad: qué se libera al eliminar y qué no
-- ---------------------------------------------------------------------------
--
-- Los nombres y los slugs SÍ se liberan. Si alguien elimina la marca «Pulse»
-- por error y la vuelve a crear, exigirle «Pulse 2» porque queda una fila
-- invisible es castigarle por un borrado que ya se deshizo.
--
-- El SKU, el código de cupón y el usuario NO se liberan. Los tres aparecen
-- copiados en registros históricos —líneas de orden, órdenes con cupón,
-- columnas de auditoría—, y reutilizarlos haría que dos cosas distintas
-- compartieran identificador en el histórico.

ALTER TABLE marca DROP CONSTRAINT uq_marca_nombre;
ALTER TABLE marca DROP CONSTRAINT uq_marca_slug;
CREATE UNIQUE INDEX uq_marca_nombre ON marca (nombre) WHERE eliminado_en IS NULL;
CREATE UNIQUE INDEX uq_marca_slug   ON marca (slug)   WHERE eliminado_en IS NULL;

ALTER TABLE categoria DROP CONSTRAINT uq_categoria_nombre;
ALTER TABLE categoria DROP CONSTRAINT uq_categoria_slug;
CREATE UNIQUE INDEX uq_categoria_nombre ON categoria (nombre) WHERE eliminado_en IS NULL;
CREATE UNIQUE INDEX uq_categoria_slug   ON categoria (slug)   WHERE eliminado_en IS NULL;

ALTER TABLE subcategoria DROP CONSTRAINT uq_subcategoria_nombre;
ALTER TABLE subcategoria DROP CONSTRAINT uq_subcategoria_slug;
CREATE UNIQUE INDEX uq_subcategoria_nombre ON subcategoria (fk_id_categoria, nombre) WHERE eliminado_en IS NULL;
CREATE UNIQUE INDEX uq_subcategoria_slug   ON subcategoria (slug)                    WHERE eliminado_en IS NULL;

ALTER TABLE producto DROP CONSTRAINT uq_producto_slug;
CREATE UNIQUE INDEX uq_producto_slug ON producto (slug) WHERE eliminado_en IS NULL;

-- Índices para la papelera: sin ellos, listar lo eliminado recorre la tabla
-- entera, y lo eliminado es siempre una fracción mínima de las filas.
CREATE INDEX ix_marca_eliminado        ON marca (eliminado_en)        WHERE eliminado_en IS NOT NULL;
CREATE INDEX ix_categoria_eliminado    ON categoria (eliminado_en)    WHERE eliminado_en IS NOT NULL;
CREATE INDEX ix_subcategoria_eliminado ON subcategoria (eliminado_en) WHERE eliminado_en IS NOT NULL;
CREATE INDEX ix_producto_eliminado     ON producto (eliminado_en)     WHERE eliminado_en IS NOT NULL;
CREATE INDEX ix_cupon_eliminado        ON cupon (eliminado_en)        WHERE eliminado_en IS NOT NULL;
