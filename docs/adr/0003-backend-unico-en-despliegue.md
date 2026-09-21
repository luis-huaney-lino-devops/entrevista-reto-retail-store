# ADR-0003: Se desarrollan dos backends (Spring Boot y .NET); solo se despliega Spring Boot

> **SUSTITUIDO por [ADR-0005](0005-backend-solo-java.md).**
> Nunca llegó a escribirse codigo .NET: `backend/dotnet/` solo contenia un README, y la
> carpeta se elimino. Este documento se conserva porque un ADR registra lo que se penso
> en su momento; no describe el estado actual del repositorio.

## Contexto
El reto permite Java+Spring Boot o C#+.NET indistintamente. Se decidió construir ambos
como ejercicio, pero la entrega (repo + URL desplegada) exige un único backend en producción.

## Decisión
- `backend/java/` (Spring Boot) es el backend que corre en el VPS y el que consume el
  storefront desplegado. Es el que se defiende en la entrevista.
- `backend/dotnet/` (ASP.NET Core) se mantiene en el repo, compilable y testeable
  localmente, pero **no** tiene servicio en `deploy/docker-compose.prod.yml`.
- Ambos backends implementan el mismo `contracts/openapi.yaml` y las mismas migraciones
  SQL (`database/migrations/`), así que son intercambiables: cambiar cuál se despliega es
  solo tocar el compose, no reescribir el frontend.

## Consecuencias
- Un solo servicio de API en producción → menos RAM en el VPS, menos superficie de
  despliegue, sin ambigüedad sobre "cuál API está viva".
- Si en la entrevista piden ver el otro backend, se corre localmente
  (`dotnet run` en `backend/dotnet/`) contra la misma base de datos de desarrollo.
- Si más adelante se prefiere desplegar .NET en vez de Java, el cambio es agregar su
  servicio al compose y apagar `api-java`: el contrato no cambia.
