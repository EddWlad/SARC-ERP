package com.tidsec.sarc_bakend.modules.consumption.application.result;

/** Resultado de conciliar las sesiones pendientes con las identidades registradas en el inventario. */
public record PendingLinkResult(int linked, int stillPending) {
}
