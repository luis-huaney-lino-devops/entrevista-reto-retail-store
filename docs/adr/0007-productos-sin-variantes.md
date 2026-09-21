# ADR-0007: Productos simples, sin variantes

## Contexto
Una tienda real suele vender el mismo artículo en varias tallas o colores, y cada
combinación tiene su propio stock y a veces su propio precio. Modelarlo exige separar
«producto» (lo que se muestra) de «variante» (lo que se compra y se cuenta), y eso
atraviesa todo el sistema: carrito, control de stock, órdenes y panel.

## Decisión
**Un producto es una unidad vendible: un SKU, un precio, un stock.** No hay variantes.

## Consecuencias
- El carrito referencia productos directamente. El control de stock es sobre una sola
  columna. La orden copia un precio, no el de una variante.
- El panel es un formulario, no una matriz de combinaciones.
- **Una tienda de ropa necesitaría variantes.** Este catálogo se modela con productos
  independientes («Polo Oversize Negro M» es un producto). Funciona, y tiene un costo
  visible: los filtros por talla y color no existen, y el detalle no puede ofrecer un
  selector de talla.
- Añadirlas más adelante **no es aditivo**: cambia el carrito, el control de stock, las
  órdenes y todo el panel. Es la decisión más cara de revertir de las cuatro, y por eso
  queda escrita aquí en lugar de asumida.
