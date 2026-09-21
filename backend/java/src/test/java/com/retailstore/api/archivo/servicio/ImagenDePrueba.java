package com.retailstore.api.archivo.servicio;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Genera un PNG válido sin depender de ninguna librería de imagen.
 *
 * <p>Se escribe a mano a propósito: usar la misma librería que se está probando
 * para fabricar el caso de prueba esconde exactamente los fallos que se buscan.
 */
final class ImagenDePrueba {

    private ImagenDePrueba() {
    }

    static byte[] png(int ancho, int alto) {
        byte[] filas = new byte[alto * (1 + ancho * 3)];
        int i = 0;
        for (int y = 0; y < alto; y++) {
            filas[i++] = 0; // tipo de filtro
            for (int x = 0; x < ancho; x++) {
                filas[i++] = (byte) (x % 256);
                filas[i++] = (byte) (y % 256);
                filas[i++] = (byte) 200;
            }
        }

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        escribir(salida, new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        escribir(salida, trozo("IHDR".getBytes(), cabecera(ancho, alto)));
        escribir(salida, trozo("IDAT".getBytes(), comprimir(filas)));
        escribir(salida, trozo("IEND".getBytes(), new byte[0]));
        return salida.toByteArray();
    }

    private static byte[] cabecera(int ancho, int alto) {
        return new byte[] {
                (byte) (ancho >>> 24), (byte) (ancho >>> 16), (byte) (ancho >>> 8), (byte) ancho,
                (byte) (alto >>> 24), (byte) (alto >>> 16), (byte) (alto >>> 8), (byte) alto,
                8, 2, 0, 0, 0};
    }

    private static byte[] trozo(byte[] tipo, byte[] datos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        int largo = datos.length;
        escribir(salida, new byte[] {
                (byte) (largo >>> 24), (byte) (largo >>> 16), (byte) (largo >>> 8), (byte) largo});
        escribir(salida, tipo);
        escribir(salida, datos);

        CRC32 crc = new CRC32();
        crc.update(tipo);
        crc.update(datos);
        long valor = crc.getValue();
        escribir(salida, new byte[] {
                (byte) (valor >>> 24), (byte) (valor >>> 16), (byte) (valor >>> 8), (byte) valor});
        return salida.toByteArray();
    }

    private static byte[] comprimir(byte[] datos) {
        Deflater deflater = new Deflater();
        deflater.setInput(datos);
        deflater.finish();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] bufer = new byte[8192];
        while (!deflater.finished()) {
            salida.write(bufer, 0, deflater.deflate(bufer));
        }
        deflater.end();
        return salida.toByteArray();
    }

    private static void escribir(ByteArrayOutputStream salida, byte[] datos) {
        salida.write(datos, 0, datos.length);
    }
}
