package com.retailstore.api.correo.servicio;

/**
 * El HTML de los correos.
 *
 * <p>Se escribe a mano y no con un motor de plantillas porque son tres correos
 * cortos: añadir Thymeleaf para esto sería más configuración que contenido.
 *
 * <p><strong>Tablas y estilos en línea, sin CSS externo ni flexbox.</strong> No
 * es dejadez: Outlook y varios clientes de escritorio ignoran las hojas de
 * estilo y el diseño moderno, y un correo que solo se ve bien en Gmail se ve
 * roto en la mitad de las bandejas.
 *
 * <p>Todo lo que viene de fuera pasa por {@link #escapar}: un nombre con un
 * {@code <} rompería el marcado, y uno con una etiqueta lo inyectaría.
 */
final class PlantillasCorreo {

    private static final String MARCA = "#ef6407";
    private static final String TINTA = "#12253f";
    private static final String TEXTO = "#4a5364";

    private PlantillasCorreo() {
    }

    static String bienvenida(String nombre, String urlTienda) {
        return envoltura(
                "Bienvenido a Retail Store",
                """
                <p style="margin:0 0 16px;font-size:16px;color:%s;">Hola %s,</p>
                <p style="margin:0 0 16px;font-size:15px;color:%s;line-height:1.6;">
                  Tu cuenta ya está lista. Desde ahora tus pedidos, direcciones y favoritos
                  te siguen entre dispositivos: entras y están donde los dejaste.
                </p>
                """.formatted(TINTA, escapar(nombre), TEXTO),
                urlTienda.isEmpty() ? null : urlTienda + "/productos",
                "Ver el catálogo");
    }

    static String recuperacion(String nombre, String enlace) {
        return envoltura(
                "Restablece tu contraseña",
                """
                <p style="margin:0 0 16px;font-size:16px;color:%s;">Hola %s,</p>
                <p style="margin:0 0 16px;font-size:15px;color:%s;line-height:1.6;">
                  Recibimos una solicitud para cambiar tu contraseña. Pulsa el botón y elige
                  una nueva. <strong>El enlace caduca en 30 minutos</strong> y solo sirve una vez.
                </p>
                <p style="margin:0 0 16px;font-size:14px;color:%s;line-height:1.6;">
                  Si no fuiste tú, ignora este correo: tu contraseña no cambia hasta que
                  alguien use este enlace.
                </p>
                """.formatted(TINTA, escapar(nombre), TEXTO, TEXTO),
                enlace,
                "Elegir contraseña nueva");
    }

    static String registroDuplicado(String nombre, String urlTienda) {
        return envoltura(
                "Alguien intentó registrarse con tu correo",
                """
                <p style="margin:0 0 16px;font-size:16px;color:%s;">Hola %s,</p>
                <p style="margin:0 0 16px;font-size:15px;color:%s;line-height:1.6;">
                  Alguien intentó crear una cuenta con esta dirección, y ya tienes una.
                  <strong>No hemos cambiado nada.</strong>
                </p>
                <p style="margin:0 0 16px;font-size:14px;color:%s;line-height:1.6;">
                  Si fuiste tú y no recuerdas la contraseña, puedes restablecerla desde la
                  pantalla de acceso. Si no fuiste tú, no hay nada que hacer: nadie ha
                  entrado a tu cuenta.
                </p>
                """.formatted(TINTA, escapar(nombre), TEXTO, TEXTO),
                urlTienda.isEmpty() ? null : urlTienda + "/acceso",
                "Ir a mi cuenta");
    }

    /** Cabecera, cuerpo y -si hay a dónde ir- un botón. */
    private static String envoltura(String titulo, String cuerpo, String enlace, String textoEnlace) {
        String boton = enlace == null ? "" : """
                <tr><td style="padding:8px 0 24px;">
                  <a href="%s" style="display:inline-block;background:%s;color:#ffffff;
                     text-decoration:none;font-weight:700;font-size:15px;
                     padding:12px 24px;border-radius:10px;">%s</a>
                </td></tr>
                <tr><td style="padding:0 0 8px;font-size:12px;color:#6b7486;line-height:1.5;">
                  Si el botón no funciona, copia esta dirección en tu navegador:<br>
                  <span style="color:#4a5364;word-break:break-all;">%s</span>
                </td></tr>
                """.formatted(enlace, MARCA, textoEnlace, enlace);

        return """
                <!doctype html>
                <html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title></head>
                <body style="margin:0;padding:24px 12px;background:#f5f6f8;
                       font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                  <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                         style="max-width:560px;margin:0 auto;background:#ffffff;
                                border-radius:12px;border:1px solid #e3e6eb;">
                    <tr><td style="padding:28px 28px 8px;">
                      <span style="font-size:18px;font-weight:800;color:%s;">Retail&nbsp;Store</span>
                    </td></tr>
                    <tr><td style="padding:8px 28px 0;">%s</td></tr>
                    <tr><td style="padding:0 28px;">
                      <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                             width="100%%">%s</table>
                    </td></tr>
                    <tr><td style="padding:16px 28px 28px;border-top:1px solid #e3e6eb;
                               font-size:12px;color:#6b7486;">
                      Este correo es automático; no hace falta responderlo.
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(escapar(titulo), TINTA, cuerpo, boton);
    }

    /**
     * Un nombre es texto de alguien, no marcado.
     *
     * <p>Sin esto, registrarse como {@code <script>...} pondría ese script en
     * el correo de bienvenida, y un nombre con {@code &} o {@code <} rompería
     * el HTML sin necesidad de mala intención.
     */
    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
