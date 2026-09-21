# ADR-0004: PostgreSQL como base de datos (con SQLite como plan B de tiempo)

## Contexto
Se necesita una BD "rápida de integrar" en el VPS, con dinero exacto y buen soporte de
transacciones para el control de stock del carrito.

## Decisión
PostgreSQL en Docker, un contenedor más en `docker-compose`. Tipos `numeric(12,2)` para
todo campo monetario (nunca `float`/`double`).

## Consecuencias
- Un contenedor adicional (~100 MB de RAM), asumible en un VPS de 2 GB.
- Si el tiempo antes de la entrega aprieta, EF Core/Hibernate permiten cambiar a SQLite
  cambiando el provider/dialecto, sin tocar el modelo de dominio ni las specifications/
  queries LINQ (las migraciones SQL crudas sí tendrían que adaptarse: es el costo de ese
  plan B y por eso es la última opción, no la primera).
