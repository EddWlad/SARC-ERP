package com.tidsec.sarc_bakend.modules.consumption.application.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;

/**
 * Indicadores de consumo del periodo [from, to) (PRD 7.3). {@code official} cuenta solo sesiones
 * validadas; pendientes de vincular y de prueba se informan aparte y no suman. {@code daily} trae
 * todos los dias del periodo, con cero donde no hubo sesiones, para graficar la tendencia.
 */
public record ConsumptionSummary(LocalDate from, LocalDate to, EnergyTotals official,
		BigDecimal averageKwhPerSession, List<RankedEquipment> top, List<DailyEnergy> daily, EnergyTotals pendingLink,
		EnergyTotals test, Instant generatedAt) {
}
