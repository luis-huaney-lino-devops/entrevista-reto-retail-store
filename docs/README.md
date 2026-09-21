# Documentación

Por dónde empezar, según lo que necesites.

| Si quieres… | Lee |
| --- | --- |
| Saber **qué existe y qué falta** hoy | [`estado-y-brecha.md`](estado-y-brecha.md) — manda sobre todo lo demás en cuanto al estado |
| Entender **una regla de negocio** | [`negocio/reglas-negocio.md`](negocio/reglas-negocio.md) — el único sitio donde se numeran las `RN-###` |
| Saber **por qué** algo está hecho así | [`adr/`](adr/) — una decisión por archivo, con su alternativa descartada |
| Escribir código del **backend** | [`backend/estandar-backend.md`](backend/estandar-backend.md) |
| Escribir código de los **frontends** | [`frontend/`](frontend/) |
| Manejar un **error de la API** | [`contrato/catalogo-errores.md`](contrato/catalogo-errores.md) |
| Ver el **modelo de datos** | [`negocio/modelo-dominio.md`](negocio/modelo-dominio.md) |
| Ver el **plan** por bloques | [`PLAN.md`](PLAN.md) |

El contrato REST —`../contracts/openapi.yaml`— **no** se escribe a mano: sale de
la propia aplicación con springdoc. El diseño se discute aquí; ese archivo es el
reflejo de lo que existe.

---

## Dos convenciones que conviene no romper

**Las reglas se numeran en un solo documento.** `negocio/reglas-negocio.md` es
la autoridad; el resto las referencia y nunca las define ni las renumera. Ya
pasó una vez que dos documentos usaran el mismo número para cosas distintas, y
encontrar cuál era el bueno costó más que escribir las dos.

**Si un documento describe algo que el código no tiene, no es un error del
documento**: es trabajo pendiente. Lo que hay que actualizar entonces es
`estado-y-brecha.md`, no borrar el párrafo.
