package com.tidsec.sarc_bakend.modules.consumption.application.result;

import java.time.Instant;
import java.util.List;

/**
 * Datos de un reporte de consumo: alcance, fecha de corte, los mismos indicadores del tablero, el
 * ranking completo, las transacciones oficiales y, aparte, las excluidas (pendientes y de prueba).
 */
public record ConsumptionReport(String scope, Instant cutOff, ConsumptionSummary summary,
		List<RankedEquipment> ranking, List<SessionView> official, List<SessionView> excluded) {
}
