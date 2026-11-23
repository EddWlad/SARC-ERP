package com.tidsec.sarc_bakend.modules.incident.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.Kpis;

/**
 * Todo lo que leen el tablero, el PDF y el Excel de gestion de incidencias (FR-080 a FR-086): el periodo, los filtros,
 * el corte, los indicadores y las filas de detalle. Se arma una sola vez por corte, asi que las tres vistas coinciden.
 * Los nombres de personas son los actuales al corte (D12) y no entran en ninguna cifra.
 */
public record ManagementData(Instant cutOff, LocalDate from, LocalDate to, String network, ElementClass elementClass,
		IncidentType incidentType, Priority priority, Kpis kpis, int openTicketsAtCutOff, List<TicketLine> tickets,
		List<EpisodeLine> episodes, List<EventLine> events, List<CommunicationLine> communications,
		List<VisitLine> visits, String referenceVersions) {

	/** Un ticket con al menos un episodio en el periodo. */
	public record TicketLine(String code, String currentState, String stateAtPeriodEnd, String elementClass,
			String element, String network, String incidentType, String priority, String origin, String responsible,
			int episodes) {
	}

	/** Un episodio con T0 a T4, su marca y los tiempos derivados; vacio es «sin dato», nunca cero (FR-042). */
	public record EpisodeLine(String code, int ordinal, String type, String priority, String element, String network,
			String state, String t0, String t1, String t2, String t3,
			String t4, String t0Mark, String t1Mark, String t2Mark, String t3Mark, String t4Mark, Integer t1Minutes,
			Integer t2Minutes, Integer t3Minutes, Integer t4Minutes, Integer t4MinusT3, String referenceVersion,
			String referenceResults, String deviation, String closureResult, String cause, Long elapsedOpenMinutes) {
	}

	public record EventLine(String code, int episode, String type, Instant occurredAt, Instant recordedAt, String author,
			boolean retroactive, boolean annulled, String text) {
	}

	public record CommunicationLine(String code, String channel, String kind, Instant sentAt, Instant recordedAt,
			String author, boolean retroactive, boolean annulled) {
	}

	public record VisitLine(String code, String provider, String stage, Instant at, String note, UUID visitId) {
	}
}
