# ADR-0008: Cuentas para clientes; el administrador entra con acceso fijo

## Contexto
El sistema tiene dos audiencias: quien compra y quien gestiona. Se pidió acceso con
Google, recuperación de contraseña y envío de correos. La pregunta es para quién.

## Decisión
- **Clientes:** registro con correo, acceso con Google, recuperación de contraseña,
  verificación de correo. Tabla `clientes`.
- **Administradores:** usuario y contraseña, y nada más. Sin registro público, sin
  Google, sin recuperación por correo. Tabla `administradores`, **separada**.

## Consecuencias
- **Tablas separadas, no un campo `rol`.** Con una sola tabla de usuarios, cualquier
  fallo en el registro público sería una escalada a administrador. Aquí ese camino no
  existe: el registro escribe en `clientes`, y `clientes` no abre el panel bajo ninguna
  circunstancia.
- Los tokens llevan audiencia (`aud`): `storefront` o `admin`. El filtro de cada
  aplicación la exige, así que un token de cliente no abre el panel aunque la firma sea
  válida.
- **El administrador que pierde la contraseña no la recupera solo.** Otro se la
  restablece, o se cambia por configuración y se reinicia. Es incómodo y es la decisión
  correcta: el flujo de recuperación por correo es la vía más usada para tomar cuentas
  privilegiadas, y el riesgo no es proporcionado para una cuenta que edita precios y ve
  todas las órdenes.
- Un solo rol de administrador. Más de un perfil de gestión exigiría introducir roles, y
  entonces esta decisión habría que revisarla.
- El comprador puede comprar **sin cuenta**: el carrito sigue identificándose por UUID.
  Tener cuenta añade historial de pedidos y datos guardados, no es un requisito para
  comprar.
