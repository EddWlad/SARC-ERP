package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.application.command.CreateTicketCommand;
import com.tidsec.sarc_bakend.modules.incident.application.port.IncidentSettings;
import com.tidsec.sarc_bakend.modules.incident.application.result.TicketCreation;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentLabels;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketNumbering;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ElementClass;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReferenceVersion;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.AffectedElement;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.DuplicateKey;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.NewTicket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketNumberingRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAsset;
import com.tidsec.sarc_bakend.shared.catalog.IncidentAssetDirectory;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;

/**
 * Crea un ticket en una sola transaccion (FR-010 a FR-019): idempotencia, elemento validado, aviso de duplicado,
 * numero consecutivo, prioridad propuesta, T0 y la version de referencias vigente, evento y auditoria. Si cualquier
 * paso falla, el numero tomado vuelve: no hay huecos.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class TicketCreationService {

	private static final int MAX_AUDIT_VALUE = 500;

	private final TicketRepository tickets;
	private final TicketEventRepository events;
	private final TicketNumberingRepository numbering;
	private final IncidentSettings settings;
	private final IncidentAssetDirectory assets;
	private final IncidentStaffDirectory staff;
	private final ReferenceCatalog references;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	TicketCreationService(TicketRepository tickets, TicketEventRepository events, TicketNumberingRepository numbering,
			IncidentSettings settings, IncidentAssetDirectory assets, IncidentStaffDirectory staff,
			ReferenceCatalog references, AuditTrail audit, CurrentActor actor, Clock clock) {
		this.tickets = tickets;
		this.events = events;
		this.numbering = numbering;
		this.settings = settings;
		this.assets = assets;
		this.staff = staff;
		this.references = references;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	@Transactional
	public TicketCreation create(CreateTicketCommand command) {
		// 1) Un envio repetido devuelve el mismo ticket y no consume otro numero.
		Optional<Ticket> existing = tickets.findByClientRequestId(command.clientRequestId());
		if (existing.isPresent()) {
			return new TicketCreation(existing.get().getId(), false);
		}
		// 2) El elemento y el tipo.
		AffectedElement element = element(command);
		if (!command.incidentType().allowedFor(element.elementClass())) {
			throw IncidentErrors.elementInvalid("El tipo de incidencia no corresponde a este elemento");
		}
		requireOriginDetail(command.reportOrigin(), command.reportOriginDetail());
		// 3) Aviso de duplicado: no bloquea si el analista da un motivo, que se audita.
		String duplicateKey = DuplicateKey.of(element);
		String override = blankToNull(command.duplicateOverrideReason());
		if (override != null && override.length() > 300) {
			throw IncidentErrors.elementInvalid("El motivo para crear un duplicado admite hasta 300 caracteres");
		}
		List<Ticket> open = tickets.findOpenByDuplicateKey(duplicateKey);
		if (!open.isEmpty() && override == null) {
			throw IncidentErrors.openDuplicate(open.stream().map(this::openMatch).toList());
		}
		// 4) Numeracion: primero el ambiente, despues la configuracion, y solo entonces el numero.
		int number = nextNumber();
		// 5) T0 y version de referencias, prioridad propuesta.
		Instant now = Instant.now(clock);
		Instant t0 = command.t0() == null ? now : command.t0();
		ReferenceVersion version = references.versionAt(t0);
		Priority proposed = references.proposedPriority(version, command.incidentType());
		Priority priority = command.priority() == null ? proposed : command.priority();
		String priorityReason = blankToNull(command.priorityReason());
		if (priority != proposed && priorityReason == null) {
			throw IncidentErrors.priorityReasonRequired();
		}
		// 6) El responsable inicial es el autor.
		UUID author = actor.id().orElseThrow(IncidentErrors::responsibleInvalid);
		String authorName = staff.namesOf(Set.of(author)).getOrDefault(author, "Sin nombre");
		Ticket ticket = tickets.save(Ticket.open(new NewTicket(number, command.clientRequestId(), element,
				command.incidentType(), command.reportOrigin(), blankToNull(command.reportOriginDetail()),
				command.description().strip(), priority, author, authorName, duplicateKey, override, t0,
				blankToNull(command.t0Reason()), version.getId(), author, now)));
		// 7) El evento de creacion alimenta la campana futura (FR-096).
		events.save(TicketEvent.record(ticket.getId(), ticket.latestEpisode().getId(), EventType.TICKET_CREATED, now, now,
				author, false, null, null, null, eventDetails(number, ticket, proposed, priorityReason, override),
				EventType.TICKET_CREATED.isNotifiable(priority)));
		// 8) Auditoria.
		audit.record("TICKET_CREATED", "INCIDENT_TICKET", ticket.getId(),
				auditDetails(number, ticket, override != null));
		return new TicketCreation(ticket.getId(), true);
	}

	/** El ambiente se comprueba antes que la configuracion (research R2); despues se toma el numero. */
	private int nextNumber() {
		Optional<String> expected = settings.expectedNumberingEnvironment();
		TicketNumbering current = numbering.current();
		if (expected.isEmpty() || (current.environment() != null && !expected.get().equals(current.environment()))) {
			throw IncidentErrors.numberingEnvironmentMismatchOnCreate();
		}
		if (!current.configured()) {
			throw IncidentErrors.numberingNotConfigured();
		}
		return numbering.nextNumber().orElseThrow(IncidentErrors::numberingNotConfigured);
	}

	private AffectedElement element(CreateTicketCommand command) {
		ElementClass elementClass = command.elementClass();
		return switch (elementClass) {
			case EQUIPMENT -> {
				if (command.equipmentId() == null) {
					throw IncidentErrors.elementInvalid("Elija el equipo afectado");
				}
				yield AffectedElement.forEquipment(
						assets.findByIds(Set.of(command.equipmentId())).get(command.equipmentId()),
						command.connectorIds());
			}
			case CHATBOT_SERVICE -> {
				if (command.equipmentId() != null || command.siteElement() != null
						|| (command.connectorIds() != null && !command.connectorIds().isEmpty())) {
					throw IncidentErrors.elementInvalid(
							"Una falla del servicio de chatbot no lleva equipo, conectores ni elemento del sitio");
				}
				yield AffectedElement.chatbot(command.chatbotScope());
			}
			case SITE_ELEMENT -> {
				CreateTicketCommand.SiteElementInput site = command.siteElement();
				if (site == null || command.equipmentId() != null
						|| (command.connectorIds() != null && !command.connectorIds().isEmpty())) {
					throw IncidentErrors.elementInvalid(
							"Un elemento del sitio se describe con su clase y su ubicación, sin equipo ni conectores");
				}
				IncidentAsset reference = site.referenceEquipmentId() == null ? null
						: assets.findByIds(Set.of(site.referenceEquipmentId())).get(site.referenceEquipmentId());
				if (site.referenceEquipmentId() != null && reference == null) {
					throw IncidentErrors.elementInvalid("El equipo de referencia no existe");
				}
				yield AffectedElement.forSiteElement(site.kind(), site.elementName(), reference,
						site.manualSiteName(), site.manualLocation(), site.manualMapsUrl());
			}
		};
	}

	private static void requireOriginDetail(ReportOrigin origin, String detail) {
		if (origin == ReportOrigin.OTHER && blankToNull(detail) == null) {
			throw IncidentErrors.elementInvalid("«Otro» como origen del aviso exige un detalle");
		}
	}

	private Map<String, Object> openMatch(Ticket ticket) {
		Map<String, Object> match = new HashMap<>();
		match.put("id", ticket.getId().toString());
		match.put("code", new TicketCode(ticket.getTicketNumber()).text());
		match.put("incidentTypeLabel", IncidentLabels.type(ticket.getIncidentType()));
		match.put("priority", ticket.getPriority().name());
		match.put("createdAt", ticket.getCreatedAt() == null ? null : ticket.getCreatedAt().toString());
		return match;
	}

	private static Map<String, Object> eventDetails(int number, Ticket ticket, Priority proposed, String priorityReason,
			String override) {
		Map<String, Object> details = new HashMap<>();
		details.put("number", number);
		details.put("incidentType", ticket.getIncidentType().name());
		details.put("priority", ticket.getPriority().name());
		details.put("proposedPriority", proposed.name());
		if (priorityReason != null) {
			details.put("priorityReason", cut(priorityReason));
		}
		if (override != null) {
			details.put("duplicateOverrideReason", cut(override));
		}
		return details;
	}

	private static Map<String, String> auditDetails(int number, Ticket ticket, boolean overrodeDuplicate) {
		Map<String, String> details = new HashMap<>();
		details.put("code", new TicketCode(number).text());
		details.put("elementClass", ticket.getElementClass().name());
		details.put("incidentType", ticket.getIncidentType().name());
		details.put("priority", ticket.getPriority().name());
		details.put("duplicateOverride", Boolean.toString(overrodeDuplicate));
		if (overrodeDuplicate) {
			details.put("duplicateOverrideReason", cut(ticket.getDuplicateOverrideReason()));
		}
		return details;
	}

	private static String cut(String text) {
		return text == null || text.length() <= MAX_AUDIT_VALUE ? text : text.substring(0, MAX_AUDIT_VALUE);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
