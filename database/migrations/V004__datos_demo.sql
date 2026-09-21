-- Datos de demostración.
--
-- Las imágenes apuntan a picsum.photos en formato WebP. Son marcadores: no
-- existen en el almacén de objetos, solo sirven para que la tienda se vea
-- poblada desde el primer arranque. Una subida real por el panel crea archivos
-- de verdad y los reemplaza.
--
-- Contraseña del administrador: AdminRetail2026!
-- Cámbiala antes de exponer esto a cualquier red.

-- ---------------------------------------------------------------------------
-- Acceso al panel
-- ---------------------------------------------------------------------------

INSERT INTO administrador (usuario, hash_contrasena, nombre, rol) VALUES
    ('admin',
     '{bcrypt}$2a$12$zTUp8.yCL8ZdVPCEocdf3eKuUmnbafp9qBRzqsLXt8Hd9EckVdeka',
     'Administrador General',
     'SUPERADMINISTRADOR');

-- ---------------------------------------------------------------------------
-- Marcas
-- ---------------------------------------------------------------------------

INSERT INTO marca (nombre, slug, descripcion) VALUES
    ('NovaTech',    'novatech',    'Electrónica de consumo y accesorios de cómputo.'),
    ('Pulse',       'pulse',       'Audio personal y wearables.'),
    ('Inca Home',   'inca-home',   'Menaje y textiles para el hogar.'),
    ('Andes Gear',  'andes-gear',  'Ropa y equipo para exteriores.'),
    ('Kuska',       'kuska',       'Tejidos de alpaca y algodón pima.'),
    ('Aurora',      'aurora',      'Cuidado personal y fragancias.'),
    ('Lúdica',      'ludica',      'Juegos y juguetes educativos.');

-- ---------------------------------------------------------------------------
-- Categorías (nivel 1)
-- ---------------------------------------------------------------------------

INSERT INTO categoria (nombre, slug, descripcion, orden) VALUES
    ('Tecnología', 'tecnologia', 'Cómputo, audio y accesorios.',        1),
    ('Hogar',      'hogar',      'Cocina, dormitorio e iluminación.',   2),
    ('Moda',       'moda',       'Ropa, calzado y accesorios.',         3),
    ('Deportes',   'deportes',   'Ciclismo, fitness y aire libre.',     4),
    ('Belleza',    'belleza',    'Cuidado personal y fragancias.',      5),
    ('Juguetes',   'juguetes',   'Juegos de mesa, construcción y más.', 6);

-- ---------------------------------------------------------------------------
-- Subcategorías (nivel 2). Aquí cuelgan los productos.
-- ---------------------------------------------------------------------------

INSERT INTO subcategoria (fk_id_categoria, nombre, slug, orden)
SELECT c.id_categoria, v.nombre, v.slug, v.orden
FROM (VALUES
    ('tecnologia', 'Audio',               'audio-tecnologia',        1),
    ('tecnologia', 'Computación',         'computacion-tecnologia',  2),
    ('tecnologia', 'Accesorios',          'accesorios-tecnologia',   3),
    ('hogar',      'Cocina',              'cocina-hogar',            1),
    ('hogar',      'Dormitorio',          'dormitorio-hogar',        2),
    ('hogar',      'Iluminación',         'iluminacion-hogar',       3),
    ('moda',       'Abrigos',             'abrigos-moda',            1),
    ('moda',       'Calzado',             'calzado-moda',            2),
    ('moda',       'Bolsos y mochilas',   'bolsos-y-mochilas-moda',  3),
    ('deportes',   'Ciclismo',            'ciclismo-deportes',       1),
    ('deportes',   'Fitness',             'fitness-deportes',        2),
    ('deportes',   'Accesorios',          'accesorios-deportes',     3),
    ('belleza',    'Cuidado del cabello', 'cuidado-del-cabello-belleza', 1),
    ('belleza',    'Cuidado de la piel',  'cuidado-de-la-piel-belleza',  2),
    ('belleza',    'Fragancias',          'fragancias-belleza',      3),
    ('juguetes',   'Construcción',        'construccion-juguetes',   1),
    ('juguetes',   'Juegos de mesa',      'juegos-de-mesa-juguetes', 2),
    ('juguetes',   'Vehículos',           'vehiculos-juguetes',      3)
) AS v(categoria_slug, nombre, slug, orden)
JOIN categoria c ON c.slug = v.categoria_slug;

-- ---------------------------------------------------------------------------
-- Catálogo
-- ---------------------------------------------------------------------------

-- Tabla de trabajo: se usa tres veces (archivo, producto, producto_imagen) y
-- repetir 24 filas tres veces es garantizar que algún día no coincidan.
CREATE TEMPORARY TABLE semilla_producto (
    sku               varchar(40),
    subcategoria_slug varchar(120),
    marca_slug        varchar(100),
    nombre            varchar(160),
    slug              varchar(200),
    descripcion_corta varchar(300),
    descripcion       text,
    precio            numeric(12,2),
    precio_anterior   numeric(12,2),
    stock             integer,
    calificacion      numeric(2,1),
    calificacion_conteo integer,
    destacado         boolean,
    dias_atras        integer
) ON COMMIT DROP;

INSERT INTO semilla_producto VALUES
 ('TEC-AUD-001','audio-tecnologia','pulse','Audífonos Bluetooth Pulse X','audifonos-bluetooth-pulse-x','Cancelación de ruido y 30 h de batería.','Audífonos inalámbricos con cancelación activa de ruido, 30 horas de batería y estuche de carga rápida.',189.90,249.90,35,4.6,214,true,2),
 ('TEC-AUD-002','audio-tecnologia','pulse','Parlante Portátil Pulse Go','parlante-portatil-pulse-go','Resistente al agua, 12 h de autonomía.','Parlante Bluetooth con certificación IPX7, sonido de 360 grados y 12 horas de reproducción continua.',149.00,NULL,42,4.3,131,false,6),
 ('TEC-CMP-001','computacion-tecnologia','novatech','Teclado Mecánico K70 RGB','teclado-mecanico-k70-rgb','Switches rojos e iluminación RGB.','Teclado mecánico en español con switches rojos, iluminación RGB por tecla y reposamuñecas desmontable.',259.90,299.90,12,4.7,156,false,9),
 ('TEC-CMP-002','computacion-tecnologia','novatech','Monitor 27 Pulgadas QHD','monitor-27-pulgadas-qhd','2560x1440 a 144 Hz.','Monitor IPS de 27 pulgadas, resolución QHD, 144 Hz y soporte ajustable en altura.',1099.00,1299.00,8,4.5,73,true,4),
 ('TEC-CMP-003','computacion-tecnologia',NULL,'Mouse Inalámbrico Silencioso','mouse-inalambrico-silencioso','Clic silencioso, 2400 DPI.','Mouse inalámbrico de 2,4 GHz con clic silencioso, sensor de 2400 DPI y batería de larga duración.',59.90,NULL,95,4.1,288,false,12),
 ('TEC-ACC-001','accesorios-tecnologia','novatech','Cargador Portátil 20000 mAh','cargador-portatil-20000-mah','Carga rápida de 22,5 W.','Power bank de 20000 mAh con carga rápida de 22,5 W, dos puertos USB-A y un USB-C.',99.90,NULL,60,4.3,321,false,14),
 ('TEC-ACC-002','accesorios-tecnologia','pulse','Smartwatch Fit Pro 2','smartwatch-fit-pro-2','GPS, ritmo cardíaco y 5 ATM.','Reloj inteligente con GPS integrado, monitoreo cardíaco, 14 modos deportivos y resistencia al agua 5 ATM.',329.00,NULL,18,4.4,97,true,5),
 ('HOG-COC-001','cocina-hogar','inca-home','Cafetera de Goteo Aroma 12','cafetera-de-goteo-aroma-12','12 tazas, programable.','Cafetera programable de 12 tazas con filtro permanente, jarra de vidrio templado y sistema antigoteo.',159.00,199.00,22,4.5,88,true,1),
 ('HOG-COC-002','cocina-hogar','inca-home','Set de Ollas Antiadherentes 5 Piezas','set-de-ollas-antiadherentes-5-piezas','Aptas para inducción.','Ollas de aluminio forjado con recubrimiento antiadherente libre de PFOA, aptas para todo tipo de cocina.',349.00,429.00,9,4.6,52,false,20),
 ('HOG-DOR-001','dormitorio-hogar','kuska','Juego de Sábanas Algodón Pima','juego-de-sabanas-algodon-pima','300 hilos, dos plazas.','Sábanas de algodón pima peruano de 300 hilos. Incluye sábana ajustable, plana y dos fundas.',219.90,NULL,40,4.8,143,false,7),
 ('HOG-DOR-002','dormitorio-hogar','kuska','Edredón Alpaca Ligero','edredon-alpaca-ligero','Mezcla de alpaca, dos plazas.','Edredón de mezcla de fibra de alpaca, ligero y abrigador, con funda de algodón lavable.',389.00,449.00,11,4.7,61,true,10),
 ('HOG-ILU-001','iluminacion-hogar','novatech','Lámpara de Escritorio LED Flex','lampara-de-escritorio-led-flex','3 temperaturas y puerto USB.','Lámpara con brazo flexible, tres temperaturas de color, atenuador táctil y puerto USB de carga.',79.90,99.90,0,4.2,65,false,11),
 ('MOD-ABR-001','abrigos-moda','andes-gear','Casaca Impermeable Andes','casaca-impermeable-andes','Costuras selladas y forro polar.','Casaca cortaviento con capucha ajustable, costuras selladas y forro polar ligero. Pensada para altura.',279.90,349.90,25,4.7,178,true,3),
 ('MOD-ABR-002','abrigos-moda','kuska','Chompa de Alpaca Cusco','chompa-de-alpaca-cusco','Tejido de alpaca, corte regular.','Chompa tejida con mezcla de fibra de alpaca. Abrigadora, suave y de corte regular.',239.00,NULL,15,4.9,74,false,17),
 ('MOD-CAL-001','calzado-moda',NULL,'Zapatillas Urbanas Classic','zapatillas-urbanas-classic','Plantilla acolchada, suela de caucho.','Zapatillas de cuero sintético con plantilla acolchada y suela de caucho antideslizante.',199.00,NULL,48,4.3,240,false,6),
 ('MOD-CAL-002','calzado-moda','andes-gear','Botines de Trekking Ligeros','botines-de-trekking-ligeros','Membrana impermeable.','Botines de trekking con membrana impermeable, suela de alta tracción y refuerzo en la puntera.',359.00,399.00,14,4.6,92,false,13),
 ('MOD-BOL-001','bolsos-y-mochilas-moda','andes-gear','Mochila Urbana 25 L','mochila-urbana-25l','Compartimento para laptop 15,6.','Mochila con compartimento acolchado para laptop de 15,6 pulgadas, bolsillo antirrobo y puerto USB.',129.90,159.90,3,4.5,301,true,8),
 ('DEP-CIC-001','ciclismo-deportes','andes-gear','Bicicleta Montañera Aro 29','bicicleta-montanera-aro-29','21 velocidades, frenos de disco.','Marco de aluminio, 21 velocidades, frenos de disco mecánicos y suspensión delantera con bloqueo.',1299.00,1499.00,6,4.6,39,true,4),
 ('DEP-FIT-001','fitness-deportes',NULL,'Mat de Yoga Antideslizante 6 mm','mat-de-yoga-antideslizante-6mm','TPE ecológico con correa.','Mat de TPE ecológico de 6 mm con doble textura antideslizante y correa de transporte.',69.90,NULL,80,4.4,412,false,10),
 ('DEP-FIT-002','fitness-deportes',NULL,'Mancuernas Ajustables 20 kg','mancuernas-ajustables-20kg','Discos intercambiables.','Par de mancuernas con discos intercambiables y barra conectora para usarlas como pesa.',249.00,289.00,14,4.5,126,false,13),
 ('DEP-ACC-001','accesorios-deportes','andes-gear','Botella Térmica Acero 750 ml','botella-termica-acero-750ml','Frío 24 h, caliente 12 h.','Botella de acero inoxidable 18/8, libre de BPA. Mantiene frío 24 horas y caliente 12 horas.',59.90,NULL,120,4.7,533,false,22),
 ('BEL-CAB-001','cuidado-del-cabello-belleza','aurora','Secadora de Cabello Ionic 2200 W','secadora-de-cabello-ionic-2200w','Tecnología iónica antifrizz.','Secadora con tecnología iónica antifrizz, tres temperaturas, dos velocidades, difusor y concentrador.',149.90,189.90,27,4.4,191,false,5),
 ('BEL-PIE-001','cuidado-de-la-piel-belleza','aurora','Set de Skincare Hidratante','set-de-skincare-hidratante','Limpiador, sérum y crema.','Limpiador facial, sérum con ácido hialurónico y crema hidratante. Apto para piel sensible.',119.00,NULL,33,4.6,87,true,12),
 ('BEL-FRA-001','fragancias-belleza','aurora','Perfume Brisa 100 ml','perfume-brisa-100ml','Cítrico, jazmín y cedro.','Eau de parfum con notas cítricas, jazmín y madera de cedro. Larga duración.',179.00,219.00,19,4.3,58,false,16),
 ('JUG-CON-001','construccion-juguetes','ludica','Bloques de Construcción 500 Piezas','bloques-de-construccion-500-piezas','Incluye base y organizador.','Set de 500 piezas compatible con las principales marcas. Incluye placa base y caja organizadora.',89.90,119.90,45,4.8,267,true,2),
 ('JUG-MES-001','juegos-de-mesa-juguetes','ludica','Juego de Mesa Familiar Estrategia','juego-de-mesa-familiar-estrategia','De 2 a 6 jugadores.','Juego de estrategia para 2 a 6 jugadores, partidas de 45 minutos. Recomendado desde los 8 años.',99.00,NULL,28,4.6,61,false,30),
 ('JUG-MES-002','juegos-de-mesa-juguetes','ludica','Rompecabezas Machu Picchu 1000 Piezas','rompecabezas-machu-picchu-1000-piezas','70 x 50 cm, acabado mate.','Rompecabezas de cartón premium con acabado mate antirreflejo. Medidas finales de 70 x 50 cm.',49.90,NULL,70,4.7,119,false,15),
 ('JUG-VEH-001','vehiculos-juguetes','ludica','Carro a Control Remoto 4x4','carro-a-control-remoto-4x4','Escala 1:16, alcance 50 m.','Escala 1:16 con tracción en las cuatro ruedas, batería recargable y alcance de 50 metros.',159.00,199.00,11,4.2,83,false,18);

-- Un archivo por producto. La clave imita la que generaría una subida real.
INSERT INTO archivo (clave, nombre_original, tipo_mime, bytes, ancho, alto, texto_alt, hash_contenido, url_publica)
SELECT 'semilla/' || s.slug || '/original.webp',
       s.slug || '.jpg',
       'image/webp',
       120000,
       2400,
       2400,
       s.nombre,
       md5(s.slug),
       'https://picsum.photos/seed/' || s.slug || '/2400/2400.webp'
FROM semilla_producto s;

-- Las cuatro variantes de cada archivo.
INSERT INTO archivo_variante (fk_id_archivo, nombre, clave, url_publica, ancho, alto, bytes)
SELECT a.id_archivo,
       v.nombre,
       'semilla/' || s.slug || '/' || lower(v.nombre) || '.webp',
       'https://picsum.photos/seed/' || s.slug || '/' || v.ancho || '/' || v.ancho || '.webp',
       v.ancho,
       v.ancho,
       v.bytes
FROM semilla_producto s
JOIN archivo a ON a.hash_contenido = md5(s.slug)
CROSS JOIN (VALUES
    ('MINIATURA', 160,  6000),
    ('TARJETA',   480,  28000),
    ('DETALLE',   1200, 90000),
    ('ORIGINAL',  2400, 120000)
) AS v(nombre, ancho, bytes);

INSERT INTO producto (
    sku, nombre, slug, descripcion_corta, descripcion,
    fk_id_subcategoria, fk_id_marca, precio, precio_anterior, stock,
    destacado, activo, calificacion_promedio, calificacion_conteo, creado_en, actualizado_en)
SELECT s.sku, s.nombre, s.slug, s.descripcion_corta, s.descripcion,
       sub.id_subcategoria,
       m.id_marca,
       s.precio, s.precio_anterior, s.stock,
       s.destacado,
       true,
       s.calificacion, s.calificacion_conteo,
       now() - make_interval(days => s.dias_atras), now()
FROM semilla_producto s
JOIN subcategoria sub ON sub.slug = s.subcategoria_slug
LEFT JOIN marca m ON m.slug = s.marca_slug;

INSERT INTO producto_imagen (fk_id_producto, fk_id_archivo, orden)
SELECT p.id_producto, a.id_archivo, 0
FROM semilla_producto s
JOIN producto p ON p.slug = s.slug
JOIN archivo a ON a.hash_contenido = md5(s.slug);

-- ---------------------------------------------------------------------------
-- Cupones
-- ---------------------------------------------------------------------------

INSERT INTO cupon (codigo, tipo, valor, subtotal_minimo, inicia_en, termina_en, usos_maximos) VALUES
    ('BIENVENIDA10', 'PORCENTAJE', 10,    50.00,  now() - interval '1 day', now() + interval '90 days', NULL),
    ('ENVIO20',      'MONTO_FIJO', 20.00, 100.00, now() - interval '1 day', now() + interval '90 days', 500),
    ('VERANO25',     'PORCENTAJE', 25,    300.00, now() - interval '1 day', now() + interval '30 days', 100),
    -- Caducado a propósito: sirve para comprobar que la tienda lo rechaza.
    ('EXPIRADO',     'PORCENTAJE', 50,    0,      now() - interval '60 days', now() - interval '30 days', NULL);
