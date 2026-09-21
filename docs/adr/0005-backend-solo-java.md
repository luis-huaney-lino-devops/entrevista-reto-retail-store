# ADR-0005: Un solo backend, en Java / Spring Boot

**Sustituye a [ADR-0003](0003-backend-unico-en-despliegue.md).**

## Contexto
ADR-0003 planteaba construir dos backends equivalentes (Spring Boot y ASP.NET Core) y
desplegar solo uno. Nunca llegó a escribirse código .NET: `backend/dotnet/` contenía
únicamente un README.

Mantener dos implementaciones del mismo contrato tiene un valor real —demuestra que el
diseño vive en el contrato y no en el framework— pero cuesta el doble en cada cambio, y
ese costo se paga en cada funcionalidad nueva. El alcance de este proyecto creció
(marcas, subcategorías, archivos, identidad de clientes, correo), así que el precio
subió mientras el beneficio seguía igual.

## Decisión
**El backend es Java 21 / Spring Boot, y es el único.** `backend/dotnet/` se elimina.

Se retiran también las menciones a un segundo backend en `pom.xml`, `application.yml`,
las migraciones SQL y `contracts/openapi.yaml`.

## Consecuencias
- Cada funcionalidad se implementa una vez. Con el alcance ampliado, esto decide.
- `contracts/openapi.yaml` sigue siendo útil: ya no como contrato entre dos backends,
  sino como contrato entre el backend y **dos frontends** (tienda y panel), que es
  donde ahora está la frontera que importa.
- Se pierde el argumento de entrevista «mira, son intercambiables». Se compensa con
  algo más defendible: un backend con reglas de negocio numeradas y verificables.
- Volver atrás es posible y caro. Nadie lo planea.
