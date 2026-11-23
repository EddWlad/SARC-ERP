package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.application.result.OpenMatchView;
import com.tidsec.sarc_bakend.modules.incident.application.result.ResponsibleView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.ClosureView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.CommunicationView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.ConnectorView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.DurationView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.EpisodeView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.EquipmentView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.EventView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.EvidenceView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.VisitView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.MilestoneView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.ReferenceVersionView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.ReferenceView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.RelationView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketDetailView.SiteElementView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketPageView;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketSummaryView;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.SiteElementKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Communication;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.DuplicateKey;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Episode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Evidence;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Milestone;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketCondition;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketConnector;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketRelation;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketState;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.EpisodeDurations;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.ReferenceEvaluator;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.EvidenceRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketListQuery;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketListRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketNumberingRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRelationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.VisitRepository;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;
import com.tidsec.sarc_bakend.shared.catalog.IncidentConnector;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;

/**
 * Consulta de tickets: listado con filtros, orden y pagina (FR-061) y detalle. Nunca escribe. Los nombres del equipo y
 * de las personas se resuelven con los puertos, en lote, para toda la pagina. Lo pueden usar los cinco roles; las
 * acciones disponibles ({@code allowedActions}) solo las ven los editores.
 */
@Service
@PreAuthorize(IncidentPermissions.READERS)
@Transactional(readOnly = true)
public class TicketReadService {

	private final TicketRepository tickets;
	private final TicketListRepository list;
	private final TicketEventRepository events;
	private final TicketRelationRepository relations;
	private final VisitRepository visits;
	private final EvidenceRepository evidence;
	private final CommunicationRepository communications;
	private final TicketNumberingRepository numbering;
	private final ReferenceCatalog references;
	private final IncidentAssetDirectory assets;
	private final IncidentStaffDirectory staff;
	private final IncidentReportQuery reportQuery;
	private final CurrentActor actor;
	private final Clock clock;

	TicketReadService(TicketRepository tickets, TicketListRepository list, TicketEventRepository events,
			TicketRelationRepository relations, VisitRepository visits, EvidenceRepository evidence,
			CommunicationRepository communications,
			TicketNumberingRepository numbering, ReferenceCatalog references, IncidentAssetDirectory assets,
			IncidentStaffDirectory staff, IncidentReportQuery reportQuery, CurrentActor actor, Clock clock) {
		this.tickets = tickets;
		this.list = list;
		this.events = events;
		this.relations = relations;
		this.visits = visits;
		this.evidence = evidence;
		this.communications = communications;
		this.numbering = numbering;
		this.references = references;
		this.assets = assets;
		this.staff = staff;
		this.reportQuery = reportQuery;
		this.actor = actor;
		this.clock = clock;
	}

	public TicketPageView list(TicketListQuery query) {
		return list(query, null, null);
	}

	/**
	 * El listado, con un periodo opcional (FR-073, FR-074): solo los tickets que estuvieron abiertos en el rango y, en
	 * cada fila, su estado al final de el. Usa la misma consulta que el informe de control, asi que ambos coinciden.
	 */
	public TicketPageView list(TicketListQuery query, LocalDate from, LocalDate to) {
		Instant cutOff = Instant.now(clock);
		Map<UUID, TicketState> stateAtEnd = null;
		if (from != null || to != null) {
			IncidentReportQuery.ControlData data = reportQuery
					.control(new IncidentReportQuery.Selection(from, to, query.state(), null), cutOff);
			stateAtEnd = data.rows().stream().collect(
					Collectors.toMap(IncidentReportQuery.ControlRow::ticketId, IncidentReportQuery.ControlRow::stateAtEnd));
			query = query.withPeriod(stateAtEnd.keySet());
		}
		TicketListRepository.IdPage page = list.find(query);
		Map<UUID, Ticket> byId = tickets.findAllById(page.ids()).stream()
				.collect(Collectors.toMap(Ticket::getId, Function.identity()));
		List<Ticket> ordered = page.ids().stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
		Map<UUID, IncidentAsset> currentAssets = assetsOf(ordered);
		Map<UUID, String> names = staff.namesOf(ordered.stream().map(Ticket::getCurrentResponsibleId)
				.collect(Collectors.toSet()));
		Map<UUID, TicketState> ended = stateAtEnd;
		List<TicketSummaryView> items = ordered.stream()
				.map(ticket -> summary(ticket, currentAssets, names, ended == null ? null : ended.get(ticket.getId())))
				.toList();
		return new TicketPageView(items, page.total(), query.page(), query.size(), cutOff,
				noticeFor(query.search()));
	}

	public TicketDetailView detail(UUID id) {
		Ticket ticket = tickets.findById(id).orElseThrow(IncidentErrors::ticketNotFound);
		Map<UUID, IncidentAsset> currentAssets = assetsOf(List.of(ticket));
		List<TicketEvent> timeline = events.findByTicket(id);
		List<TicketRelation> related = relations.findByTicket(id);
		Map<UUID, Ticket> otherTickets = tickets.findAllById(related.stream().map(r -> r.otherThan(id)).toList())
				.stream().collect(Collectors.toMap(Ticket::getId, Function.identity()));

		Set<UUID> people = new HashSet<>();
		people.add(ticket.getCurrentResponsibleId());
		timeline.forEach(event -> {
			if (event.getRecordedBy() != null) {
				people.add(event.getRecordedBy());
			}
		});
		Map<UUID, String> names = staff.namesOf(people);
		TicketSummaryView summary = summary(ticket, currentAssets, names, null);

		IncidentAsset equipment = ticket.getEquipmentId() == null ? null : currentAssets.get(ticket.getEquipmentId());
		return new TicketDetailView(summary, ticket.getDescription(), ticket.getReportOrigin(),
				ticket.getReportOriginDetail(), equipmentView(ticket, equipment), connectorViews(ticket, equipment),
				ticket.getChatbotScope(), siteElementView(ticket, currentAssets), location(ticket, equipment),
				mapsUrl(ticket, equipment), referenceVersion(ticket), episodes(ticket, timeline),
				related.stream().map(relation -> relationView(relation, id, otherTickets)).toList(),
				timeline.stream().map(event -> eventView(event, names)).toList(), visitViews(ticket),
				evidenceViews(id, timeline, names), communicationViews(id), allowedActions(ticket));
	}

	private List<VisitView> visitViews(Ticket ticket) {
		Map<UUID, Integer> ordinals = ticket.getEpisodes().stream()
				.collect(Collectors.toMap(Episode::getId, Episode::getOrdinal));
		return visits.findByTicket(ticket.getId()).stream()
				.map(visit -> new VisitView(visit.getId(), ordinals.getOrDefault(visit.getEpisodeId(), 0),
						visit.getProviderName(), visit.getStage(), visit.getCancelDecidedBy(), visit.getCancelEvidenceNote(),
						visit.getReescalationCondition(), visit.getCreatedAt(), visit.getUpdatedAt()))
				.toList();
	}

	private List<CommunicationView> communicationViews(UUID ticketId) {
		List<Communication> stored = communications.findByTicket(ticketId);
		Map<UUID, String> authors = staff.namesOf(stored.stream().map(Communication::getRecordedBy)
				.filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return stored.stream()
				.map(item -> new CommunicationView(item.getId(), item.getChannel(), item.getChannelDetail(), item.getKind(),
						item.getSentAt(), item.isRetroactive(), item.getJustification(), item.getRecordedBy(),
						item.getRecordedBy() == null ? null : authors.get(item.getRecordedBy()), item.getRecordedAt(),
						item.isAnnulled(), item.getAnnulReason()))
				.toList();
	}

	/** Las evidencias con el nombre de quien las cargo; estan anuladas si su evento lo esta. */
	private List<EvidenceView> evidenceViews(UUID ticketId, List<TicketEvent> timeline, Map<UUID, String> names) {
		Map<UUID, TicketEvent> byEvent = timeline.stream().collect(Collectors.toMap(TicketEvent::getId, Function.identity()));
		List<Evidence> stored = evidence.findByTicket(ticketId);
		Map<UUID, String> authors = staff.namesOf(stored.stream().map(Evidence::getUploadedBy)
				.filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
		return stored.stream().map(item -> {
			TicketEvent event = byEvent.get(item.getEventId());
			return new EvidenceView(item.getId(), item.getEventId(), item.getCommunicationId(), item.getPurpose(), item.getFormat(), item.getSizeBytes(),
					item.getSha256(), item.getOriginalName(), item.getUploadedBy(),
					item.getUploadedBy() == null ? null : authors.getOrDefault(item.getUploadedBy(), names.get(item.getUploadedBy())),
					item.getUploadedAt(), event != null && event.isAnnulled());
		}).toList();
	}

	/**
	 * Tickets abiertos con la misma clave de duplicado que el elemento descrito (FR-016, research R4). Solo para editores:
	 * sirve al formulario de creacion.
	 */
	@PreAuthorize(IncidentPermissions.EDITORS)
	public List<OpenMatchView> openMatches(ElementClass elementClass, UUID equipmentId, SiteElementKind siteKind,
			UUID referenceEquipmentId, String manualSiteName, String elementName) {
		String key = switch (elementClass) {
			case EQUIPMENT -> {
				if (equipmentId == null) {
					throw IncidentErrors.elementInvalid("Indique el equipo");
				}
				yield DuplicateKey.forEquipment(equipmentId);
			}
			case CHATBOT_SERVICE -> DuplicateKey.forChatbot();
			case SITE_ELEMENT -> {
				if (siteKind == null || (referenceEquipmentId == null && (manualSiteName == null || manualSiteName.isBlank()))) {
					throw IncidentErrors.elementInvalid("Indique la clase del elemento y su sitio");
				}
				yield DuplicateKey.forSite(siteKind, referenceEquipmentId, manualSiteName, elementName);
			}
		};
		return tickets.findOpenByDuplicateKey(key).stream()
				.map(ticket -> new OpenMatchView(ticket.getId(), new TicketCode(ticket.getTicketNumber()).text(),
						IncidentLabels.type(ticket.getIncidentType()), ticket.getPriority(), ticket.getCreatedAt()))
				.toList();
	}

	// ----- Resumen -----

	private TicketSummaryView summary(Ticket ticket, Map<UUID, IncidentAsset> currentAssets, Map<UUID, String> names,
			TicketState stateAtPeriodEnd) {
		Episode latest = ticket.latestEpisode();
		Instant t0 = latest.milestone(MilestoneKind.T0).getOccurredAt();
		String responsibleName = names.getOrDefault(ticket.getCurrentResponsibleId(), ticket.getCurrentResponsibleName());
		return new TicketSummaryView(ticket.getId(), new TicketCode(ticket.getTicketNumber()).text(), ticket.getState(),
				ticket.getElementClass(), ElementLabelComposer.compose(ticket, currentAssets), ticket.getNetworkAtT0(),
				ticket.getIncidentType(), IncidentLabels.type(ticket.getIncidentType()), ticket.getPriority(),
				new ResponsibleView(ticket.getCurrentResponsibleId(), responsibleName), t0, ticket.getLastActivityAt(),
				TicketCondition.of(latest).orElse(null), ticket.getEpisodes().size(), ticket.getVersion(),
				stateAtPeriodEnd);
	}

	private Map<UUID, IncidentAsset> assetsOf(List<Ticket> ofTickets) {
		Set<UUID> ids = new HashSet<>();
		for (Ticket ticket : ofTickets) {
			if (ticket.getEquipmentId() != null) {
				ids.add(ticket.getEquipmentId());
			}
			if (ticket.getReferenceEquipmentId() != null) {
				ids.add(ticket.getReferenceEquipmentId());
			}
		}
		return assets.findByIds(ids);
	}

	/** Un codigo buscado anterior al numero inicial es de la secuencia externa anterior, no de SARC. */
	private String noticeFor(String search) {
		Optional<TicketCode> code = TicketCode.parse(search);
		if (code.isEmpty()) {
			return null;
		}
		Integer start = numbering.current().startNumber();
		return start != null && code.get().number() < start ? TicketPageView.NOTICE_BEFORE_START : null;
	}

	// ----- Detalle -----

	private static EquipmentView equipmentView(Ticket ticket, IncidentAsset current) {
		if (ticket.getEquipmentId() == null) {
			return null;
		}
		return new EquipmentView(ticket.getEquipmentId(), ticket.getEquipmentLabelAtT0(),
				current != null ? current.name() : ticket.getEquipmentLabelAtT0(), current != null && current.active());
	}

	private static List<ConnectorView> connectorViews(Ticket ticket, IncidentAsset current) {
		Map<UUID, IncidentConnector> now = current == null ? Map.of()
				: current.connectors().stream().collect(Collectors.toMap(IncidentConnector::id, Function.identity()));
		List<ConnectorView> views = new ArrayList<>();
		for (TicketConnector connector : ticket.getConnectors()) {
			IncidentConnector found = now.get(connector.getConnectorId());
			views.add(new ConnectorView(connector.getConnectorId(), connector.getPositionAtT0(),
					found == null ? null : found.position(), found != null && found.active()));
		}
		views.sort(java.util.Comparator.comparingInt(ConnectorView::positionAtT0));
		return views;
	}

	private static SiteElementView siteElementView(Ticket ticket, Map<UUID, IncidentAsset> currentAssets) {
		if (ticket.getElementClass() != ElementClass.SITE_ELEMENT) {
			return null;
		}
		IncidentAsset reference = ticket.getReferenceEquipmentId() == null ? null
				: currentAssets.get(ticket.getReferenceEquipmentId());
		String site = reference != null ? reference.name()
				: ticket.getReferenceEquipmentId() != null ? ticket.getSiteLabelAtT0() : ticket.getManualSiteName();
		return new SiteElementView(ticket.getSiteElementKind(), IncidentLabels.siteElementKind(ticket.getSiteElementKind()),
				ticket.getElementName(), ticket.getReferenceEquipmentId(), site);
	}

	/** El equipo muestra su ubicacion actual; un elemento del sitio, la que se conservo en T0; el chatbot no tiene. */
	private static String location(Ticket ticket, IncidentAsset equipment) {
		return switch (ticket.getElementClass()) {
			case EQUIPMENT -> equipment == null ? null : equipment.location();
			case CHATBOT_SERVICE -> null;
			case SITE_ELEMENT -> ticket.getLocationAtT0();
		};
	}

	private static String mapsUrl(Ticket ticket, IncidentAsset equipment) {
		return switch (ticket.getElementClass()) {
			case EQUIPMENT -> equipment == null ? null : equipment.mapsUrl();
			case CHATBOT_SERVICE -> null;
			case SITE_ELEMENT -> ticket.getMapsUrlAtT0();
		};
	}

	private ReferenceVersionView referenceVersion(Ticket ticket) {
		return references.version(ticket.latestEpisode().getReferenceVersionId())
				.map((ReferenceVersion version) -> new ReferenceVersionView(version.getCode(), version.getLabel()))
				.orElse(null);
	}

	/**
	 * Los episodios con sus hitos, duraciones y juicio frente a las referencias de la version de cada uno (FR-051a).
	 * El corte es el momento de la consulta: un episodio abierto se mide hasta el.
	 */
	private List<EpisodeView> episodes(Ticket ticket, List<TicketEvent> timeline) {
		Instant cutOff = Instant.now(clock);
		Map<UUID, String> originals = duplicateCodes(ticket);
		Map<Integer, EpisodeView> byOrdinal = new LinkedHashMap<>();
		for (Episode episode : ticket.getEpisodes()) {
			List<MilestoneView> milestones = episode.getMilestones().stream().map(TicketReadService::milestoneView).toList();
			List<DurationView> durations = EpisodeDurations.pairs(episode).stream()
					.map(pair -> new DurationView(pair.pair(), pair.minutes(), pair.missing())).toList();
			List<ReferenceView> judgments = ReferenceEvaluator.evaluate(episode,
					references.ruleFor(episode.getReferenceVersionId(), ticket.getIncidentType()), timeline, cutOff).stream()
					.map(j -> new ReferenceView(j.measure().name(), j.limitMinutes(), j.extendable(), j.result().name()))
					.toList();
			byOrdinal.put(episode.getOrdinal(), new EpisodeView(episode.getOrdinal(), episode.getState(),
					episode.getReopenReason(), milestones, durations, judgments,
					EpisodeDurations.elapsedOpenMinutes(episode, cutOff), closureView(episode, originals)));
		}
		return List.copyOf(byOrdinal.values());
	}

	/** Los codigos de los tickets originales de los episodios cerrados como duplicado. */
	private Map<UUID, String> duplicateCodes(Ticket ticket) {
		List<UUID> ids = ticket.getEpisodes().stream().map(Episode::getDuplicateOfTicketId)
				.filter(java.util.Objects::nonNull).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return tickets.findAllById(ids).stream()
				.collect(Collectors.toMap(Ticket::getId, found -> new TicketCode(found.getTicketNumber()).text()));
	}

	private static ClosureView closureView(Episode episode, Map<UUID, String> originals) {
		if (episode.isOpen() || episode.closureResult().isEmpty()) {
			return null;
		}
		return new ClosureView(episode.closureResult().get(), episode.getDuplicateOfTicketId(),
				episode.getDuplicateOfTicketId() == null ? null : originals.get(episode.getDuplicateOfTicketId()), episode.causeKind().orElse(null), episode.getCauseText(),
				episode.getRecommendations(), episode.getClosureChecks(), episode.getFinalEvidenceWaiver(),
				episode.getClosureCommunicationWaiver(), episode.getDeviationJustification());
	}

	private static MilestoneView milestoneView(Milestone milestone) {
		return new MilestoneView(milestone.getKind(), milestone.getStatus(), milestone.getOccurredAt(),
				milestone.getReason(), milestone.isRetroactive(), milestone.isOrderWarningAck(),
				milestone.getRecordedBy(), milestone.getRecordedAt());
	}

	private static RelationView relationView(TicketRelation relation, UUID ticketId, Map<UUID, Ticket> others) {
		UUID otherId = relation.otherThan(ticketId);
		Ticket other = others.get(otherId);
		return new RelationView(otherId, other == null ? null : new TicketCode(other.getTicketNumber()).text(),
				relation.getKind(), relation.getReason());
	}

	private static EventView eventView(TicketEvent event, Map<UUID, String> names) {
		return new EventView(event.getId(), event.getType(), event.getOccurredAt(), event.getRecordedAt(),
				event.getRecordedBy(), event.getRecordedBy() == null ? null : names.get(event.getRecordedBy()),
				event.isRetroactive(), event.getText(), event.getTargetKind(), event.getTargetRoleOrProvider(),
				event.getDetails(), event.isAnnulled(), event.getAnnulledAt(), event.getAnnulReason());
	}

	/**
	 * Las acciones que el usuario actual puede hacer sobre el ticket, calculadas con su rol y el estado. La interfaz las
	 * usa para mostrar botones, pero la autorizacion sigue en cada operacion. Los roles de consulta no tienen ninguna.
	 */
	private List<String> allowedActions(Ticket ticket) {
		if (!actor.hasAnyRole("ADMINISTRADOR", "ANALISTA")) {
			return List.of();
		}
		if (ticket.getState() == TicketState.CLOSED) {
			boolean reopenable = ticket.latestEpisode().closureResult().map(result -> !result.excludedFromIndicators())
					.orElse(true);
			return reopenable ? List.of("REOPEN", "MESSAGE", "RELATE", "CORRECT_MILESTONES") : List.of("MESSAGE", "RELATE");
		}
		return List.of("MILESTONE", "EVENT", "CORRECT", "RECLASSIFY", "PRIORITY", "RESPONSIBLE", "HANDOVER", "VISIT",
				"EVIDENCE", "COMMUNICATION", "MESSAGE", "RELATE", "CLOSE");
	}
}
