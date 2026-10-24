package com.tidsec.sarc_bakend.modules.consumption.application.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

/**
 * Indicadores de consumo del periodo [from, to) (PRD 7.3). {@code official} cuenta solo sesiones
 * validadas; pendientes de vincular y de prueba se informan aparte y no suman. {@code daily} trae
 * todos los dias del periodo y las distribuciones todas sus categorias, con cero donde no hubo
 * sesiones, para graficar sin huecos.
 */
public record ConsumptionSummary(LocalDate from, LocalDate to, EnergyTotals official,
		BigDecimal averageKwhPerSession, List<RankedEquipment> top, List<DailyEnergy> daily,
		Map<ChargingNetwork, EnergyTotals> byNetwork, Map<SessionSource, EnergyTotals> bySource,
		Map<InfrastructureType, EnergyTotals> byType, EnergyTotals pendingLink, EnergyTotals test,
		Instant generatedAt) {
}
