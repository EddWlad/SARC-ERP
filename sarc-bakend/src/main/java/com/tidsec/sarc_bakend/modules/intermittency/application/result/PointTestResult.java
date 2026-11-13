package com.tidsec.sarc_bakend.modules.intermittency.application.result;

/**
 * Resultado de marcar o quitar la prueba a un punto en un rango: cuantas ventanas cambiaron y cuantas ventanas
 * OPEN del rango quedaron sin marcar, porque una ventana de prueba siempre esta cerrada.
 */
public record PointTestResult(int affectedWindows, int openWindowsNotMarked) {
}
