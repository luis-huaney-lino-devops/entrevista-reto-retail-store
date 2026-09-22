package com.retailstore.api.opinion.dto;

/**
 * Cuántas opiniones hay de cada número de estrellas.
 *
 * <p>Es lo primero que mira quien va a leer reseñas: un 4,3 con cincuenta
 * cincos y diez unos no es el mismo producto que un 4,3 con todo cuatros.
 *
 * <p>Siempre vienen las cinco filas, de 5 a 1, aunque alguna valga cero: si el
 * servidor omitiera las vacías, la tienda tendría que reconstruirlas para
 * pintar las barras, y una barra que falta se lee como un dato que falta.
 *
 * @param porcentaje redondeado al entero y calculado aquí, para que el
 *                   servidor y la tienda no puedan discrepar. Suma 100 salvo
 *                   por el redondeo, que no se reparte: inventar un punto para
 *                   cuadrar la suma sería mentir sobre una de las barras
 */
public record DesgloseCalificacionRespuesta(int estrellas, long cantidad, int porcentaje) {
}
