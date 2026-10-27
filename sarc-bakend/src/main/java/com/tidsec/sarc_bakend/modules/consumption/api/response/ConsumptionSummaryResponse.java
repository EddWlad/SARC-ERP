package com.tidsec.sarc_bakend.modules.consumption.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tidsec.sarc_bakend.modules.consumption.application.result.ConsumptionSummary;
import com.tidsec.sarc_bakend.modules.consumption.application.result.RankedEquipment;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.DailyEnergy;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyTotals;
import com.tidsec.sarc_bakend.shared.domain.ChargingNetwork;
import com.tidsec.sarc_bakend.shared.domain.InfrastructureType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Indicadores del periodo [from, to). {@code official} solo cuenta sesiones validadas; pendientes y
 * pruebas se informan aparte y no suman (PRD 3.3).
 */
public record ConsumptionSummaryResponse(
		LocalDate from,
		LocalDate to,
		Totals official,
		BigDecimal averageKwhPerSession,
		List<Ranked> top,
		List<Day> daily,
		List<NetworkShare> byNetwork,
		List<SourceShare> bySource,
		List<TypeShare> byType,
		Totals pendingLink,
		Totals awaitingApproval,
		Totals test,
		Instant generatedAt) {

	public record Totals(long sessions, BigDecimal energyKwh, long equipment) {

		static Totals from(EnergyTotals totals) {
			return new Totals(totals.sessions(), totals.energyKwh(), totals.equipment());
		}
	}

	public record Ranked(@Schema(nullable = true) EquipmentRefResponse equipment, long sessions,
			BigDecimal energyKwh) {

		static Ranked from(RankedEquipment ranked) {
			return new Ranked(EquipmentRefResponse.from(ranked.equipment()), ranked.sessions(), ranked.energyKwh());
		}
	}

	public record Day(LocalDate date, long sessions, BigDecimal energyKwh) {

		static Day from(DailyEnergy day) {
			return new Day(day.date(), day.sessions(), day.energyKwh());
		}
	}

	/** Participacion de una red en la energia oficial del periodo. */
	public record NetworkShare(ChargingNetwork network, long sessions, BigDecimal energyKwh) {
	}

	/** Participacion de una plataforma de origen. */
	public record SourceShare(SessionSource source, long sessions, BigDecimal energyKwh) {
	}

	/** Participacion de un tipo de infraestructura. */
	public record TypeShare(InfrastructureType type, long sessions, BigDecimal energyKwh) {
	}

	public static ConsumptionSummaryResponse from(ConsumptionSummary summary) {
		return new ConsumptionSummaryResponse(summary.from(), summary.to(), Totals.from(summary.official()),
				summary.averageKwhPerSession(), summary.top().stream().map(Ranked::from).toList(),
				summary.daily().stream().map(Day::from).toList(),
				summary.byNetwork().entrySet().stream()
						.map(e -> new NetworkShare(e.getKey(), e.getValue().sessions(), e.getValue().energyKwh())).toList(),
				summary.bySource().entrySet().stream()
						.map(e -> new SourceShare(e.getKey(), e.getValue().sessions(), e.getValue().energyKwh())).toList(),
				summary.byType().entrySet().stream()
						.map(e -> new TypeShare(e.getKey(), e.getValue().sessions(), e.getValue().energyKwh())).toList(),
				Totals.from(summary.pendingLink()), Totals.from(summary.awaitingApproval()),
				Totals.from(summary.test()), summary.generatedAt());
	}
}
