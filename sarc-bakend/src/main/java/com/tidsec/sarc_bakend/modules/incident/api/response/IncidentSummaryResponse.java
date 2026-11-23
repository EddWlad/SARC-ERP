package com.tidsec.sarc_bakend.modules.incident.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.Kpis;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cifras del tablero de gestion (contracts/incidents-api.md, GET /summary; FR-080 a FR-086). Salen de la misma lectura
 * que el PDF y el Excel. Los repartos son listas ordenadas de mayor a menor; las claves de tipo, prioridad, clase y origen
 * son los codigos del dominio, y «Sin red» y «Sin equipo» van como texto. No lleva cifras de disponibilidad (P-13).
 */
public record IncidentSummaryResponse(Instant cutOff, LocalDate from, LocalDate to, int openTicketsAtCutOff,
		IncidentTotals totals, List<IncidentTimeMeasure> measures, IncidentValidation validation, List<IncidentReference> references,
		List<String> withoutReference, List<IncidentDeviation> deviations, List<IncidentCount> byDay, List<IncidentCount> byNetwork,
		List<IncidentCount> byType, List<IncidentCount> byPriority, List<IncidentCount> byClass, List<IncidentCount> byEquipment, List<IncidentCount> byOrigin,
		IncidentVisits visits, List<IncidentCount> repeaters, String referenceVersions) {

	public record IncidentTotals(int uniqueTickets, int episodes, int openEpisodes, int closedEpisodes, int reopened, int critical,
			int excluded) {
	}

	public record IncidentTimeMeasure(String pair, @Schema(nullable = true) BigDecimal averageMinutes, int n, int notApplicable,
			int noData, int open) {
	}

	public record IncidentValidation(int n, int within, @Schema(nullable = true) BigDecimal percent) {
	}

	public record IncidentReference(String measure, String type, int within, int outside, int notEvaluable) {
	}

	public record IncidentDeviation(String code, int episode, String type, String measure,
			@Schema(nullable = true) String justification) {
	}

	public record IncidentCount(String key, int count) {
	}

	public record IncidentVisits(int requested, int confirmed, int cancelledOrPostponed, int executed) {
	}

	public static IncidentSummaryResponse from(ManagementData data) {
		Kpis kpis = data.kpis();
		var summary = kpis.summary();
		return new IncidentSummaryResponse(data.cutOff(), data.from(), data.to(), data.openTicketsAtCutOff(),
				new IncidentTotals(summary.uniqueTickets(), summary.episodes(), summary.openEpisodes(), summary.closedEpisodes(),
						summary.reopened(), summary.critical(), summary.excluded()),
				kpis.measures().stream().map(measure -> new IncidentTimeMeasure(measure.pair(), measure.averageMinutes(),
						measure.n(), measure.notApplicable(), measure.noData(), measure.open())).toList(),
				new IncidentValidation(kpis.validationN(), kpis.validationWithin(), kpis.validationPercent()),
				kpis.references().stream().map(count -> new IncidentReference(count.measure().name(), count.type().name(),
						count.within(), count.outside(), count.notEvaluable())).toList(),
				kpis.withoutReference().stream().map(Enum::name).toList(),
				kpis.deviations().stream().map(deviation -> new IncidentDeviation(deviation.code(), deviation.ordinal(),
						deviation.type().name(), deviation.measure().name(), deviation.justification())).toList(),
				counts(kpis.byDay()), counts(kpis.byNetwork()), counts(kpis.byType()), counts(kpis.byPriority()),
				counts(kpis.byClass()), counts(kpis.byEquipment()), counts(kpis.byOrigin()),
				new IncidentVisits(kpis.visits().requested(), kpis.visits().confirmed(), kpis.visits().cancelledOrPostponed(),
						kpis.visits().executed()),
				kpis.repeaters().stream().map(repeater -> new IncidentCount(repeater.label(), repeater.episodes())).toList(),
				data.referenceVersions());
	}

	private static List<IncidentCount> counts(Map<String, Integer> values) {
		return values.entrySet().stream().map(entry -> new IncidentCount(entry.getKey(), entry.getValue())).toList();
	}
}
