# ADR-0006: Categorías de dos niveles fijos, no un árbol

## Contexto
El catálogo necesita agrupar productos. Hay dos formas habituales: una jerarquía fija de
profundidad conocida, o un árbol con `parent_id` y profundidad libre.

El esquema actual (`V001__catalog_schema.sql`) tiene **un solo nivel**: una tabla
`categories` y `products.category_id` apuntando directamente a ella. Eso se queda corto
para una tienda real, donde «Tecnología» y «Laptops» son cosas distintas.

## Decisión
**Dos niveles, fijos: categoría → subcategoría. Los productos cuelgan de la
subcategoría, nunca de la categoría.**

## Consecuencias
- Las consultas no necesitan CTE recursivos. Filtrar por categoría es un `JOIN` a
  subcategoría; el menú es una consulta; las migas de pan son dos niveles y ya está.
- El panel de administración es un formulario con dos selectores en cascada, no un
  árbol arrastrable.
- **Un producto no puede colgar de una categoría.** Si el negocio necesita «productos
  sueltos en Tecnología», se crea una subcategoría «General». Es un rodeo pequeño y
  conocido, y a cambio toda consulta de catálogo tiene un solo camino y todo recuento
  una sola fuente. La alternativa —permitir ambos— duplica cada consulta del catálogo.
- El nombre de subcategoría es único **dentro** de su categoría, no globalmente:
  «Accesorios» puede existir bajo Tecnología y bajo Deportes. El slug sí es único
  globalmente, para que la URL no necesite al padre.
- Exige una migración sobre el esquema actual: crear `subcategories`, migrar las
  categorías existentes a subcategorías de sí mismas o reasignarlas a mano, y mover
  `products.category_id` a `products.subcategory_id`.
- Si algún día hiciera falta un tercer nivel, es una migración y una reescritura de las
  consultas del catálogo. Se asume: es mucho menos probable que el costo diario de
  mantener un árbol recursivo.
