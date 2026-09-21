# ADR-0002: El carrito es un recurso de servidor; el cliente nunca envía precios ni totales

## Contexto
"Calcular y actualizar los montos del carrito en tiempo real" es un requisito explícito.
Si el precio o el total se calculan (o solo se confirman) en el navegador, cualquiera
puede manipular la petición.

## Decisión
- El carrito vive en la base de datos (`carts`, `cart_items`). `cart_items` NO guarda
  precio: siempre se recalcula desde `products.price` vigente.
- Cada mutación (`POST/PATCH/DELETE /carts/{id}/...`) devuelve el carrito completo con
  `subtotal`, `discount` y `total` recalculados por `CartPricingCalculator`.
- El frontend logra "tiempo real" con **UI optimista**: aplica el cambio localmente vía
  `useReducer`, dispara la petición en paralelo, y reconcilia con la respuesta del
  servidor (o revierte si falla).

## Consecuencias
- Un único punto (`CartPricingCalculator`) calcula dinero. Fácil de testear, fácil de
  explicar en la entrevista.
- La UI se siente instantánea sin sacrificar que el servidor sea la fuente de verdad.
