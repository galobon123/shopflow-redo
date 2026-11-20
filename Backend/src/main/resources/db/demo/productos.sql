-- Sample catalog for local development (loaded by DemoDataLoader when there are no products).
-- Mixes stock levels on purpose: plenty, few left (badge "Quedan N"), out of stock, and one inactive product.

INSERT INTO categories (name, description) VALUES
    ('Cocina', 'Ollas, sartenes y utensilios para cocinar todos los días'),
    ('Mesa', 'Vajilla, vasos y todo para poner la mesa'),
    ('Baño', 'Toallas, accesorios y orden para el baño'),
    ('Dormitorio', 'Ropa de cama, almohadas y mantas'),
    ('Decoración', 'Velas, macetas, cuadros y detalles para la casa'),
    ('Organización', 'Cajas, frascos y canastos para ordenar')
ON CONFLICT (name) DO NOTHING;

INSERT INTO products (name, description, price, stock, category_id, active, created_at)
SELECT p.name, p.description, p.price, p.stock, c.id, p.active, now() - make_interval(days => p.days_ago)
FROM (VALUES
    -- Cocina
    ('Cocina', 'Sartén de hierro fundido 26 cm', 'Hierro fundido curado, apta para todo tipo de cocina incluida inducción. Retiene el calor y dura toda la vida.', 48900.00, 3, TRUE, 2),
    ('Cocina', 'Olla de acero inoxidable 24 cm con tapa', 'Fondo triple difusor, tapa de vidrio templado y asas que no calientan. 6 litros.', 62500.50, 18, TRUE, 10),
    ('Cocina', 'Set de 3 cuchillos de cocina', 'Chef, pan y verduras, en acero alemán con mango de madera de guayubira.', 39900.00, 25, TRUE, 35),
    ('Cocina', 'Tabla de picar de algarrobo', 'Una sola pieza de algarrobo con canal para jugos. 40 x 25 cm.', 18700.00, 40, TRUE, 50),
    ('Cocina', 'Pava eléctrica de acero 1,7 L', 'Corte automático, base giratoria y control de temperatura para el mate.', 54990.00, 0, TRUE, 60),
    ('Cocina', 'Set de utensilios de silicona x5', 'Espátula, cuchara, cucharón, pinza y batidor. Resisten hasta 230 °C.', 15400.00, 60, TRUE, 15),
    ('Cocina', 'Fuente para horno de cerámica', 'Rectangular, 32 x 22 cm. Va del horno a la mesa y al lavavajillas.', 22300.00, 12, TRUE, 80),
    ('Cocina', 'Rallador de 4 caras', 'Acero inoxidable con base antideslizante.', 7630.00, 4, TRUE, 120),
    -- Mesa
    ('Mesa', 'Juego de 6 platos playos de gres', 'Esmalte artesanal color arena, cada plato es apenas distinto. 27 cm.', 45600.00, 14, TRUE, 5),
    ('Mesa', 'Set de 6 vasos de vidrio reciclado', 'Vidrio soplado reciclado con burbujas naturales. 350 ml.', 21900.00, 30, TRUE, 20),
    ('Mesa', 'Mantel de lino rústico 2,5 m', 'Lino lavado para seis personas, color natural.', 33800.00, 9, TRUE, 40),
    ('Mesa', 'Cubiertos x24 de acero mate', 'Seis cubiertos completos con terminación cepillada.', 58700.00, 2, TRUE, 25),
    ('Mesa', 'Jarra de vidrio 1,5 L', 'Con tapa de bambú que filtra el hielo.', 12900.00, 22, TRUE, 70),
    ('Mesa', 'Set de 4 individuales de yute', 'Tejidos a mano, redondos de 38 cm.', 16500.00, 0, TRUE, 90),
    ('Mesa', 'Taza de cerámica esmaltada', 'Hecha a mano en Córdoba. 300 ml.', 8900.00, 45, TRUE, 3),
    -- Baño
    ('Baño', 'Toallón de algodón 600 g', 'Algodón peinado, suave y muy absorbente. 90 x 150 cm.', 27400.00, 35, TRUE, 12),
    ('Baño', 'Set de toalla y toallón gris perla', 'Algodón 500 g, el par.', 31200.00, 16, TRUE, 30),
    ('Baño', 'Dispenser de jabón de cerámica', 'Con bomba de acero inoxidable. 350 ml.', 11800.00, 5, TRUE, 55),
    ('Baño', 'Alfombra de baño de algodón', 'Antideslizante, se lava en lavarropas. 50 x 80 cm.', 14300.00, 20, TRUE, 65),
    ('Baño', 'Organizador de ducha de bambú', 'Estante extensible para shampoo y jabón.', 19900.00, 1, TRUE, 100),
    ('Baño', 'Bata de toalla unisex', 'Algodón 400 g con capucha. Talle único.', 42500.00, 8, TRUE, 140),
    -- Dormitorio
    ('Dormitorio', 'Juego de sábanas 2 plazas 300 hilos', 'Percal de algodón, sábana con elástico, sábana plana y dos fundas.', 69900.00, 11, TRUE, 4),
    ('Dormitorio', 'Almohada de fibra siliconada', 'Firmeza media, funda de algodón. 70 x 50 cm.', 18900.00, 50, TRUE, 18),
    ('Dormitorio', 'Acolchado de pluma sintética 2 plazas', 'Liviano y abrigado, se lava en casa.', 89500.00, 3, TRUE, 45),
    ('Dormitorio', 'Manta tejida de lana', 'Tejida en telar en Catamarca. 130 x 180 cm.', 76400.00, 6, TRUE, 75),
    ('Dormitorio', 'Pie de cama de algodón', 'Tejido waffle, color tiza. 60 x 220 cm.', 24800.00, 0, TRUE, 110),
    ('Dormitorio', 'Lámpara de mesa de luz', 'Base de madera y pantalla de lino. Lámpara E27 no incluida.', 35600.00, 13, TRUE, 28),
    -- Decoración
    ('Decoración', 'Vela de soja en frasco de vidrio', 'Aroma a higo y vainilla, 40 horas de duración.', 9800.00, 70, TRUE, 1),
    ('Decoración', 'Maceta de terracota con plato', 'Hecha a mano, 18 cm de diámetro.', 8400.00, 28, TRUE, 22),
    ('Decoración', 'Espejo redondo con marco de ratán', '60 cm de diámetro, listo para colgar.', 64300.00, 4, TRUE, 38),
    ('Decoración', 'Almohadón de lino con relleno', 'Funda con cierre, 45 x 45 cm.', 17600.00, 24, TRUE, 58),
    ('Decoración', 'Florero de vidrio soplado', 'Color ámbar, 25 cm de alto.', 21500.00, 10, TRUE, 85),
    ('Decoración', 'Cuadro lámina botánica 30 x 40', 'Impresión en papel de algodón con marco de pino.', 26900.00, 0, TRUE, 130),
    ('Decoración', 'Portavelas de hierro x3', 'Tres alturas, terminación negra mate.', 13700.00, 15, FALSE, 200),
    -- Organización
    ('Organización', 'Set de 3 frascos herméticos', 'Vidrio con tapa de bambú y burlete. 0,5, 1 y 1,5 L.', 23400.00, 32, TRUE, 7),
    ('Organización', 'Canasto de seagrass con asas', 'Tejido a mano, 40 x 30 x 30 cm.', 28900.00, 12, TRUE, 33),
    ('Organización', 'Caja organizadora de tela x2', 'Plegables, con manija. 30 x 30 x 30 cm.', 15900.00, 40, TRUE, 48),
    ('Organización', 'Perchero de pie de madera', 'Pino macizo con seis ganchos. 1,75 m.', 47800.00, 2, TRUE, 95),
    ('Organización', 'Especiero giratorio con 12 frascos', 'Base de acero y frascos de vidrio con etiquetas.', 32600.00, 7, TRUE, 150)
) AS p(category, name, description, price, stock, active, days_ago)
JOIN categories c ON c.name = p.category
WHERE NOT EXISTS (SELECT 1 FROM products existing WHERE existing.name = p.name);
