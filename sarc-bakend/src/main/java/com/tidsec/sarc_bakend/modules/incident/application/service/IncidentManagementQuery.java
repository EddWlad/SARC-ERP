package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.tidsec.sarc_bakend.modules.incident.application.result.ManagementData;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Communication;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Milestone;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneRevision;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneStatus;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Visit;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.VisitStage;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.EpisodeFact;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.IncidentKpiCalculator.VisitFact;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.MilestonesAsKnownAt.Value;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator.Judgment;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.TicketStateAt;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.TicketStateAt.Span;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.VisitRepository;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/**
 * Consulta de gestion de incidencias (FR-080 a FR-086): el tablero, el PDF y el Excel salen de aqui, con un solo corte y
 * el mismo {@link IncidentKpiCalculator}. Los indicadores de tiempo usan los episodios cuyo T0 cae en el periodo, con
 * inicio inclusivo y fin exclusivo (FR-084), y los hitos tal como se conocian en el corte. Debe llamarse dentro de una
 * transaccion de solo lectura; los lotes se piden con una consulta cada uno.
 *
 * <p>Los juicios de referencia se evaluan con los hitos vigentes del episodio, no con los conocidos en el corte: son
 * lo que muestra el detalle. Con el mismo corte y la misma instantanea, el tablero, el PDF y el Excel coinciden.
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
public class IncidentManagementQuery {

	/**
	 * Periodo y filtros: {@code from} y {@code to} son dias inclusivos y obligatorios (el inicial es «Este mes», D13);
	 * los filtros opcionales son la red en T0, la clase, el tipo y la prioridad (FR-080).
	 */
	public record Selection(LocalDate from, LocalDate to, String network, ElementClass elementClass,
			IncidentType incidentType, Priority priority) {

		public Selection {
			if (from == null || to == null) {
				throw new BusinessRuleException("PERIOD_INVALID", "El período necesita el primer y el último día");
			}
			if (to.isBefore(from)) {
				throw new BusinessRuleException("PERIOD_INVALID", "El último día no puede ser anterior al primero");
			}
			if (ChronoUnit.DAYS.between(from, to) + 1 > IncidentReportQuery.MAX_PERIOD_DAYS) {
				throw new BusinessRuleException("PERIOD_TOO_LONG",
						"El período admite hasta " + IncidentReportQuery.MAX_PERIOD_DAYS + " días");
			}
		}
	}

	private final TicketRepository tickets;
	private final TicketEventRepository events;
	private final CommunicationRepository communications;
	private final VisitRepository visits;
	private final ReferenceCatalog references;
	private final IncidentReportQuery base;
	private final IncidentStaffDirectory staff;
	private final OperatingZone zone;

	IncidentManagementQuery(TicketRepository tickets, TicketEventRepository events, CommunicationRepository communications,
			VisitRepository visits, ReferenceCatalog references, IncidentReportQuery base, IncidentStaffDirectory staff,
			OperatingZone zone) {
		this.tickets = tickets;
		this.events = events;
		this.communications = communications;
		this.visits = visits;
		this.references = references;
		this.base = base;
		this.staff = staff;
		this.zone = zone;
	}

	/** Los eventos que cambian una cifra: el escalamiento (referencia) y las etapas de visita. */
	private static final List<EventType> FIGURE_EVENTS = List.of(EventType.ESCALATED, EventType.VISIT_STAGE);

	/** El reporte completo, con la bitacora de eventos y las comunicaciones. */
	public ManagementData management(Selection selection, Instant cutOff) {
		return management(selection, cutOff, true);
	}

	/**
	 * @param fullLog con {@code false} solo se leen los eventos que cambian una cifra y no se arman la bitacora ni las
	 *        comunicaciones: las cifras son las mismas que las del reporte completo, y el tablero no las muestra
	 */
	public ManagementData management(Selection selection, Instant cutOff, boolean fullLog) {
		Instant start = zone.startOf(selection.from());
		Instant end = zone.startOf(selection.to().plusDays(1));
		Instant stateInstant = TicketStateAt.effectiveEnd(end, cutOff);

		List<Ticket> created = tickets.findCreatedUntil(cutOff).stream().filter(ticket -> matches(ticket, selection)).toList();
		Map<UUID, List<MilestoneRevision>> revisions = base.revisionsOf(created);
		List<UUID> createdIds = created.stream().map(Ticket::getId).toList();
		Map<UUID, List<TicketEvent>> eventsByTicket = (fullLog ? events.findByTickets(createdIds)
				: events.findByTicketsAndTypes(createdIds, FIGURE_EVENTS)).stream()
				.filter(event -> !event.getRecordedAt().isAfter(cutOff))
				.collect(Collectors.groupingBy(TicketEvent::getTicketId));

		List<Ticket> inPeriod = new ArrayList<>();
		List<Row> rows = new ArrayList<>();
		Map<UUID, List<Span>> allSpans = new HashMap<>();
		for (Ticket ticket : created) {
			boolean any = false;
			List<Span> spans = new ArrayList<>();
			for (Episode episode : ticket.getEpisodes()) {
				if (episode.getCreatedAt() != null && episode.getCreatedAt().isAfter(cutOff)) {
					continue;
				}
				Map<MilestoneKind, Value> known = MilestonesAsKnownAt.of(episode,
						revisions.getOrDefault(episode.getId(), List.of()), cutOff);
				Instant t0 = IncidentKpiCalculator.timeOf(known, MilestoneKind.T0);
				if (t0 == null) {
					continue;
				}
				spans.add(new Span(t0, IncidentKpiCalculator.timeOf(known, MilestoneKind.T4)));
				if (!t0.isBefore(start) && t0.isBefore(end)) {
					rows.add(new Row(ticket, episode, known, t0));
					any = true;
				}
			}
			if (any) {
				inPeriod.add(ticket);
				allSpans.put(ticket.getId(), spans);
			}
		}

		Map<UUID, IncidentAsset> assets = base.assetsOf(inPeriod);
		Map<UUID, ReferenceVersion> versions = new HashMap<>();
		// Las reglas son de la version y el tipo, no del episodio: se piden una sola vez cada una.
		Map<String, Optional<com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceRule>> rules = new HashMap<>();
		List<EpisodeFact> facts = new ArrayList<>();
		List<ManagementData.EpisodeLine> lines = new ArrayList<>();
		for (Row row : rows) {
			ReferenceVersion version = versions.computeIfAbsent(row.episode().getReferenceVersionId(),
					id -> references.version(id).orElse(null));
			List<Judgment> judgments = ReferenceEvaluator.evaluate(row.episode(),
					rules.computeIfAbsent(row.episode().getReferenceVersionId() + "|" + row.ticket().getIncidentType(),
							key -> references.ruleFor(row.episode().getReferenceVersionId(), row.ticket().getIncidentType())),
					eventsByTicket.getOrDefault(row.ticket().getId(), List.of()), cutOff);
			EpisodeFact fact = fact(row, judgments, assets, cutOff);
			facts.add(fact);
			lines.add(line(row, fact, version, judgments, ElementLabelComposer.compose(row.ticket(), assets)));
		}

		Set<UUID> episodeIds = rows.stream().map(row -> row.episode().getId()).collect(Collectors.toSet());
		List<Visit> visitRows = visits.findByTickets(inPeriod.stream().map(Ticket::getId).toList()).stream()
				.filter(visit -> episodeIds.contains(visit.getEpisodeId())
						&& (visit.getCreatedAt() == null || !visit.getCreatedAt().isAfter(cutOff)))
				.toList();
		Map<UUID, List<VisitStage>> stagesByVisit = new HashMap<>();
		List<ManagementData.VisitLine> visitLines = new ArrayList<>();
		Map<UUID, String> codes = inPeriod.stream()
				.collect(Collectors.toMap(Ticket::getId, ticket -> new TicketCode(ticket.getTicketNumber()).text()));
		Map<UUID, Visit> visitById = visitRows.stream().collect(Collectors.toMap(Visit::getId, visit -> visit));
		for (List<TicketEvent> list : eventsByTicket.values()) {
			for (TicketEvent event : list) {
				if (event.getType() != EventType.VISIT_STAGE || event.isAnnulled()) {
					continue;
				}
				Object rawVisit = event.getDetails().get("visitId");
				Object rawStage = event.getDetails().get("stage");
				if (rawVisit == null || rawStage == null) {
					continue;
				}
				UUID visitId = UUID.fromString(rawVisit.toString());
				if (!visitById.containsKey(visitId)) {
					continue;
				}
				VisitStage stage = VisitStage.valueOf(rawStage.toString());
				stagesByVisit.computeIfAbsent(visitId, id -> new ArrayList<>()).add(stage);
				visitLines.add(new ManagementData.VisitLine(codes.get(event.getTicketId()), event.getTargetRoleOrProvider(),
						stage.name(), event.getOccurredAt(), event.getText(), visitId));
			}
		}
		visitLines.sort(Comparator.comparing(ManagementData.VisitLine::code)
				.thenComparing(ManagementData.VisitLine::at));
		List<VisitFact> visitFacts = visitRows.stream().map(visit -> new VisitFact(visit.getEpisodeId(),
				IncidentKpiCalculator.reached(stagesByVisit.getOrDefault(visit.getId(), List.of())))).toList();

		Set<UUID> people = new HashSet<>();
		inPeriod.forEach(ticket -> people.add(ticket.getCurrentResponsibleId()));
		List<Communication> communicationRows = !fullLog ? List.of() : communications
				.findByTickets(inPeriod.stream().map(Ticket::getId).toList()).stream()
				.filter(item -> episodeIds.contains(item.getEpisodeId()) && !item.getRecordedAt().isAfter(cutOff)).toList();
		List<TicketEvent> eventRows = !fullLog ? List.of() : eventsByTicket.values().stream().flatMap(List::stream)
				.filter(event -> episodeIds.contains(event.getEpisodeId())).toList();
		eventRows.forEach(event -> people.add(event.getRecordedBy()));
		communicationRows.forEach(item -> people.add(item.getRecordedBy()));
		people.remove(null);
		Map<UUID, String> names = staff.namesOf(people);

		Map<UUID, Integer> ordinals = rows.stream()
				.collect(Collectors.toMap(row -> row.episode().getId(), row -> row.episode().getOrdinal()));
		List<ManagementData.EventLine> eventLines = eventRows.stream()
				.sorted(Comparator.comparing((TicketEvent event) -> codes.get(event.getTicketId()))
						.thenComparing(TicketEvent::getOccurredAt).thenComparing(TicketEvent::getRecordedAt))
				.map(event -> new ManagementData.EventLine(codes.get(event.getTicketId()),
						ordinals.get(event.getEpisodeId()), event.getType().name(), event.getOccurredAt(),
						event.getRecordedAt(), names.get(event.getRecordedBy()), event.isRetroactive(), event.isAnnulled(),
						event.getText()))
				.toList();
		List<ManagementData.CommunicationLine> communicationLines = communicationRows.stream()
				.sorted(Comparator.comparing((Communication item) -> codes.get(item.getTicketId()))
						.thenComparing(Communication::getSentAt))
				.map(item -> new ManagementData.CommunicationLine(codes.get(item.getTicketId()), item.getChannel().name(),
						item.getKind().name(), item.getSentAt(), item.getRecordedAt(), names.get(item.getRecordedBy()),
						item.isRetroactive(), item.isAnnulled()))
				.toList();

		Map<UUID, Long> episodeCounts = rows.stream()
				.collect(Collectors.groupingBy(row -> row.ticket().getId(), Collectors.counting()));
		List<ManagementData.TicketLine> ticketLines = inPeriod.stream().map(ticket -> {
			List<Span> spans = allSpans.get(ticket.getId());
			return new ManagementData.TicketLine(codes.get(ticket.getId()), IncidentLabels.state(ticket.getState()),
					IncidentLabels.state(TicketStateAt.at(spans, stateInstant)),
					IncidentLabels.elementClass(ticket.getElementClass()), ElementLabelComposer.compose(ticket, assets),
					IncidentLabels.network(ticket.getNetworkAtT0()), IncidentLabels.type(ticket.getIncidentType()),
					IncidentLabels.priority(ticket.getPriority()), IncidentLabels.origin(ticket.getReportOrigin()),
					names.getOrDefault(ticket.getCurrentResponsibleId(), ticket.getCurrentResponsibleName()),
					episodeCounts.get(ticket.getId()).intValue());
		}).toList();

		int openAtCutOff = (int) inPeriod.stream().filter(ticket -> TicketStateAt.at(allSpans.get(ticket.getId()),
				cutOff.plusNanos(1)) == TicketState.OPEN).count();
		String versionCodes = versions.values().stream().filter(java.util.Objects::nonNull).map(ReferenceVersion::getCode)
				.collect(Collectors.toCollection(TreeSet::new)).stream().collect(Collectors.joining(", "));
		return new ManagementData(cutOff, selection.from(), selection.to(), selection.network(), selection.elementClass(),
				selection.incidentType(), selection.priority(), IncidentKpiCalculator.compute(facts, visitFacts, zone.zone()),
				openAtCutOff, ticketLines, lines, eventLines, communicationLines, visitLines, versionCodes);
	}

	private static boolean matches(Ticket ticket, Selection selection) {
		return (selection.network() == null || selection.network().equals(ticket.getNetworkAtT0()))
				&& (selection.elementClass() == null || selection.elementClass() == ticket.getElementClass())
				&& (selection.incidentType() == null || selection.incidentType() == ticket.getIncidentType())
				&& (selection.priority() == null || selection.priority() == ticket.getPriority());
	}

	private record Row(Ticket ticket, Episode episode, Map<MilestoneKind, Value> known, Instant t0) {
	}

	private EpisodeFact fact(Row row, List<Judgment> judgments, Map<UUID, IncidentAsset> assets, Instant cutOff) {
		Ticket ticket = row.ticket();
		boolean openAtCut = IncidentKpiCalculator.timeOf(row.known(), MilestoneKind.T4) == null;
		Optional<com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.ClosureResult> closure = row.episode()
				.closureResult();
		UUID equipment = ticket.getElementClass() == ElementClass.EQUIPMENT ? ticket.getEquipmentId() : null;
		IncidentAsset asset = equipment == null ? null : assets.get(equipment);
		String label = equipment == null ? null : asset != null ? asset.name() : ticket.getEquipmentLabelAtT0();
		return new EpisodeFact(ticket.getId(), new TicketCode(ticket.getTicketNumber()).text(),
				row.episode().getOrdinal(), openAtCut, openAtCut ? null : closure.orElse(null), ticket.getIncidentType(),
				ticket.getElementClass(), ticket.getNetworkAtT0(), equipment, label, ticket.getPriority(),
				ticket.getReportOrigin(), row.t0(), row.known(),
				openAtCut ? Math.max(0, java.time.Duration.between(row.t0(), cutOff).toMinutes()) : null, judgments,
				row.episode().getDeviationJustification());
	}

	private static ManagementData.EpisodeLine line(Row row, EpisodeFact fact, ReferenceVersion version,
			List<Judgment> judgments, String element) {
		Map<MilestoneKind, Milestone> current = new EnumMap<>(MilestoneKind.class);
		current.putAll(row.episode().milestonesByKind());
		Map<MilestoneKind, String> marks = new EnumMap<>(MilestoneKind.class);
		for (MilestoneKind kind : MilestoneKind.values()) {
			Value value = row.known().get(kind);
			MilestoneStatus status = value == null ? MilestoneStatus.PENDING : value.status();
			boolean retroactive = current.get(kind) != null && current.get(kind).isRetroactive();
			marks.put(kind, IncidentLabels.milestoneMark(status, retroactive));
		}
		Map<MilestoneKind, Value> known = row.known();
		String results = judgments.stream().map(judgment -> judgment.measure() + "=" + judgment.result())
				.collect(Collectors.joining("; "));
		Ticket ticket = row.ticket();
		return new ManagementData.EpisodeLine(fact.code(), fact.ordinal(), IncidentLabels.type(ticket.getIncidentType()),
				IncidentLabels.priority(ticket.getPriority()), element, IncidentLabels.network(ticket.getNetworkAtT0()),
				fact.open() ? "OPEN" : "CLOSED",
				time(known, MilestoneKind.T0), time(known, MilestoneKind.T1), time(known, MilestoneKind.T2),
				time(known, MilestoneKind.T3), time(known, MilestoneKind.T4), marks.get(MilestoneKind.T0),
				marks.get(MilestoneKind.T1), marks.get(MilestoneKind.T2), marks.get(MilestoneKind.T3),
				marks.get(MilestoneKind.T4), IncidentKpiCalculator.minutes(known, MilestoneKind.T1, MilestoneKind.T0),
				IncidentKpiCalculator.minutes(known, MilestoneKind.T2, MilestoneKind.T0),
				IncidentKpiCalculator.minutes(known, MilestoneKind.T3, MilestoneKind.T0),
				IncidentKpiCalculator.minutes(known, MilestoneKind.T4, MilestoneKind.T0),
				IncidentKpiCalculator.minutes(known, MilestoneKind.T4, MilestoneKind.T3),
				version == null ? null : version.getCode(), results, fact.deviationJustification(),
				fact.closure() == null ? null : fact.closure().name(),
				row.episode().causeKind().map(Enum::name).orElse(null), fact.elapsedOpenMinutes());
	}

	private static String time(Map<MilestoneKind, Value> known, MilestoneKind kind) {
		Instant instant = IncidentKpiCalculator.timeOf(known, kind);
		return instant == null ? null : instant.toString();
	}
}
