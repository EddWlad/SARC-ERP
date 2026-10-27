package com.tidsec.sarc_bakend.modules.consumption.application.result;

/**
 * Resultado de conciliar las sesiones pendientes con las identidades registradas en el inventario:
 * vinculadas y validadas, vinculadas pero por aprobar (fuente no preferente) y sin identificar.
 */
public record PendingLinkResult(int linked, int awaitingApproval, int stillPending) {
}
