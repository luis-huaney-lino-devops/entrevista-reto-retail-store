# ADR-0002: El carrito es un recurso de servidor; el cliente nunca envía precios ni totales

## Contexto
"Calcular y actualizar los montos del carrito en tiempo real" es un requisito explícito.
Si el precio o el total se calculan (o solo se confirman) en el navegador, cualquiera
puede manipular la petición.

## Decisión
- El carrito vive en la base de datos (`carrito`, `item_carrito`). `item_carrito` **no
  guarda el precio**: siempre se recalcula desde el `precio` vigente del producto.
- Cada mutación (`POST/PATCH/DELETE /api/v1/carritos/{id}/...`) devuelve el carrito
  completo con `subtotal`, `descuento` y `total` recalculados por `CalculadoraCarrito`.
- El frontend logra "tiempo real" con **UI optimista**: aplica el cambio localmente vía
  `useReducer`, dispara la petición en paralelo, y reconcilia con la respuesta del
  servidor (o revierte si falla).

## Consecuencias
- Un único punto (`CalculadoraCarrito`) calcula dinero. Fácil de probar y fácil de
  explicar.
- La UI se siente instantánea sin sacrificar que el servidor sea la fuente de verdad.

> **Nota.** Este ADR se escribió antes del [ADR-0011](0011-dominio-en-espanol-convencion-nombres.md)
> y nombraba las tablas y clases en inglés (`carts`, `cart_items`,
> `CartPricingCalculator`). Los nombres de arriba son los que existen en el código; la
> decisión no ha cambiado.
