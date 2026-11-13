package com.tidsec.sarc_bakend.modules.intermittency.domain.model.importing;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un dia que toca una fila pendiente de un lote confirmado y que, por eso, deja sin cubrir la disponibilidad
 * estimada (FR-045). Una fila que toca varios dias aporta uno por dia. {@code equipmentId} es el punto de una fila
 * en conflicto; una fila por vincular no tiene punto.
 */
public record BlockingDay(RowClassification classification, UUID equipmentId, LocalDate day) {
}
