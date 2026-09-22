-- =========================================================================
-- V006 · El contador de no leídos del cliente
-- =========================================================================
--
-- `conversacion` solo tenía `no_leidos_admin`. El chat se diseñó para que la
-- administración atendiera, y la otra dirección quedó sin contador: el cliente
-- no tenía forma de enterarse de que le habían respondido salvo abriendo el
-- hilo y mirando.
--
-- Va desnormalizado igual que el del admin y por el mismo motivo: la campana de
-- la tienda pinta el número en cada carga, y contar los mensajes de cada
-- conversación para eso sería una consulta por hilo en cada visita.

ALTER TABLE conversacion
    ADD COLUMN no_leidos_cliente integer NOT NULL DEFAULT 0;

ALTER TABLE conversacion
    ADD CONSTRAINT ck_conversacion_noleidos_cliente CHECK (no_leidos_cliente >= 0);

-- Las conversaciones que ya existen arrancan en cero aunque tengan respuestas
-- del administrador sin leer. Es deliberado: marcar como pendiente algo de hace
-- semanas llenaría la campana de avisos viejos el día del despliegue, y quien
-- los viera no sabría si son de ahora.
