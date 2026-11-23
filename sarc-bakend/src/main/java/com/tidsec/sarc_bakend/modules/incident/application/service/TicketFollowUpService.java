package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.IncidentType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.Priority;
import com.tidsec.sarc_bakend.modules.incident.domain.model.reference.ReportOrigin;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.EventType;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TargetKind;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.Ticket;
import com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.TicketEvent;
import com.tidsec.sarc_bakend.modules.incident.domain.policy.RetroactivePolicy;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.CommunicationRepository;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketEventRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.security.IncidentStaffDirectory;
import com.tidsec.sarc_bakend.shared.security.StaffMember;

/**
 * Seguimiento de un ticket abierto o cerrado (FR-031 a FR-036): eventos manuales y su anulacion, prioridad,
 * responsable, relevo de turno, reclasificacion y correccion de datos. Toda escritura lleva la version que leyo el
 * cliente, deja su evento en la cronologia, actualiza la ultima actividad y se audita. Nunca se borra nada: un evento
 * erroneo se anula con motivo.
 */
@Service
@PreAuthorize(IncidentPermissions.EDITORS)
public class TicketFollowUpService {

	private static final int MAX_TEXT = 1000;

	private final TicketLoader loader;
	private final TicketEventRepository events;
	private final CommunicationRepository communications;
	private final IncidentStaffDirectory staff;
	private final ReferenceCatalog references;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	TicketFollowUpService(TicketLoader loader, TicketEventRepository events, CommunicationRepository communications,
			IncidentStaffDirectory staff, ReferenceCatalog references, AuditTrail audit, CurrentActor actor,
			Clock clock) {
		this.loader = loader;
		this.events = events;
		this.communications = communications;
		this.staff = staff;
		this.references = references;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	/** Un evento manual: validacion, accion correctiva, escalamiento u observacion. Los demas tipos los genera el sistema. */
	@Transactional
	public void addEvent(UUID ticketId, long version, EventType type, Instant occurredAt, String text,
			TargetKind targetKind, String targetRoleOrProvider) {
		Ticket ticket = loader.load(ticketId, version);
		if (!type.isManual()) {
			throw IncidentErrors.eventInvalid("Ese tipo de evento lo genera el sistema");
		}
		String body = requireText(text, "Escriba el detalle del evento");
		if (type == EventType.ESCALATED && targetKind == null) {
			throw IncidentErrors.eventInvalid("Un escalamiento exige su destino");
		}
		Instant now = Instant.now(clock);
		Instant when = occurredAt == null ? now : occurredAt;
		RetroactivePolicy.Kind kind = RetroactivePolicy.evaluate(when, now);
		if (kind == RetroactivePolicy.Kind.FUTURE) {
			throw IncidentErrors.eventInvalid("La hora del evento no puede estar en el futuro");
		}
		UUID author = actor.id().orElse(null);
		events.save(TicketEvent.record(ticketId, ticket.latestEpisode().getId(), type, when, now, author,
				kind == RetroactivePolicy.Kind.RETROACTIVE, body, type == EventType.ESCALATED ? targetKind : null,
				blankToNull(targetRoleOrProvider), null, type.isNotifiable(null)));
		ticket.touch(now);
		audit.record("TICKET_EVENT_ADDED", "INCIDENT_TICKET", ticketId, Map.of("code", code(ticket), "type", type.name()));
	}

	/** Anula un evento manual con motivo; sigue visible en la cronologia y en la auditoria. */
	@Transactional
	public void annulEvent(UUID ticketId, long version, UUID eventId, String reason) {
		Ticket ticket = loader.load(ticketId, version);
		TicketEvent event = events.findById(eventId).filter(found -> found.getTicketId().equals(ticketId))
				.orElseThrow(IncidentErrors::ticketNotFound);
		if (!event.getType().isAnnullable()) {
			throw IncidentErrors.eventInvalid("Solo se anulan los eventos manuales, las evidencias y las comunicaciones");
		}
		if (reason == null || reason.isBlank()) {
			throw IncidentErrors.justificationRequired("Anular un evento exige un motivo");
		}
		Instant now = Instant.now(clock);
		event.annul(actor.id().orElse(null), now, reason.strip());
		annulCommunicationOf(event, now, reason.strip());
		ticket.touch(now);
		audit.record("TICKET_EVENT_ANNULLED", "INCIDENT_TICKET", ticketId, Map.of("code", code(ticket), "event",
				eventId.toString()));
	}

	/** Anular el evento de una comunicacion anula tambien su fila: el evento y la comunicacion nunca se contradicen. */
	private void annulCommunicationOf(TicketEvent event, Instant now, String reason) {
		if (event.getType() != EventType.COMMUNICATION_RECORDED) {
			return;
		}
		Object id = event.getDetails().get("communicationId");
		if (id instanceof String text) {
			communications.findById(UUID.fromString(text)).ifPresent(
					communication -> communication.annul(actor.id().orElse(null), now, reason));
		}
	}

	/** Cambia la prioridad con motivo; pasar a Critica alimenta la campana futura (FR-096). */
	@Transactional
	public void changePriority(UUID ticketId, long version, Priority priority, String reason) {
		Ticket ticket = loader.load(ticketId, version);
		String why = requireJustification(reason, "Cambiar la prioridad exige un motivo");
		if (priority == ticket.getPriority()) {
			throw IncidentErrors.eventInvalid("El ticket ya tiene esa prioridad");
		}
		Map<String, Object> details = new HashMap<>();
		details.put("previous", ticket.getPriority().name());
		details.put("new", priority.name());
		ticket.changePriority(priority);
		record(ticket, EventType.PRIORITY_CHANGED, why, details, EventType.PRIORITY_CHANGED.isNotifiable(priority),
				"TICKET_PRIORITY_CHANGED");
	}

	/** Asigna un responsable: debe ser un Analista o Administrador activo. */
	@Transactional
	public void assignResponsible(UUID ticketId, long version, UUID responsibleId) {
		Ticket ticket = loader.load(ticketId, version);
		StaffMember member = staff.findActiveEditor(responsibleId).orElseThrow(IncidentErrors::responsibleInvalid);
		Map<String, Object> details = new HashMap<>();
		details.put("previousId", ticket.getCurrentResponsibleId().toString());
		details.put("newId", member.id().toString());
		details.put("newName", member.fullName());
		ticket.assignResponsible(member.id(), member.fullName());
		record(ticket, EventType.RESPONSIBLE_ASSIGNED, null, details, false, "TICKET_RESPONSIBLE_ASSIGNED");
	}

	/**
	 * Relevo de turno (FR-033): cambia el responsable y deja el responsable saliente y el entrante, el estado actual, los
	 * pendientes, la proxima verificacion y el criterio previsto de cierre. No crea un ticket nuevo.
	 */
	@Transactional
	public void handover(UUID ticketId, long version, UUID incomingResponsibleId, String currentStatus, String pending,
			Instant nextCheckAt, String closureCriterion) {
		Ticket ticket = loader.load(ticketId, version);
		StaffMember incoming = staff.findActiveEditor(incomingResponsibleId).orElseThrow(IncidentErrors::responsibleInvalid);
		Map<String, Object> details = new HashMap<>();
		details.put("outgoingResponsibleId", ticket.getCurrentResponsibleId().toString());
		details.put("outgoingResponsibleName", ticket.getCurrentResponsibleName());
		details.put("incomingResponsibleId", incoming.id().toString());
		details.put("incomingResponsibleName", incoming.fullName());
		details.put("currentStatus", requireText(currentStatus, "Indique el estado actual"));
		details.put("pending", requireText(pending, "Indique los pendientes"));
		details.put("closureCriterion", requireText(closureCriterion, "Indique el criterio previsto de cierre"));
		if (nextCheckAt != null) {
			details.put("nextCheckAt", nextCheckAt.toString());
		}
		ticket.assignResponsible(incoming.id(), incoming.fullName());
		record(ticket, EventType.SHIFT_HANDOVER, null, details, false, "TICKET_SHIFT_HANDOVER");
	}

	/** Cambia el tipo de incidencia: recalcula la prioridad propuesta, pero no cambia la vigente (FR-021). */
	@Transactional
	public void reclassify(UUID ticketId, long version, IncidentType type, String reason) {
		Ticket ticket = loader.load(ticketId, version);
		String why = requireJustification(reason, "Reclasificar exige un motivo");
		if (type == ticket.getIncidentType()) {
			throw IncidentErrors.eventInvalid("El ticket ya es de ese tipo");
		}
		Map<String, Object> details = new HashMap<>();
		details.put("previousType", ticket.getIncidentType().name());
		details.put("newType", type.name());
		ticket.reclassify(type);
		Priority proposed = references.proposedPriority(references.versionAt(ticket.latestEpisode()
				.milestone(com.tidsec.sarc_bakend.modules.incident.domain.model.ticket.MilestoneKind.T0).getOccurredAt()),
				type);
		details.put("proposedPriority", proposed.name());
		record(ticket, EventType.RECLASSIFIED, why, details, false, "TICKET_RECLASSIFIED");
	}

	/**
	 * Corrige la descripcion o el origen del aviso, siempre con justificacion (FR-036): un evento por campo con el valor
	 * anterior y el nuevo. El elemento afectado no se corrige: se cierra como «Registrado por error» y se crea otro.
	 */
	@Transactional
	public void correctData(UUID ticketId, long version, String description, ReportOrigin origin, String originDetail,
			String justification) {
		Ticket ticket = loader.load(ticketId, version);
		String why = requireJustification(justification, "Corregir un dato exige una justificación");
		if (description == null && origin == null) {
			throw IncidentErrors.eventInvalid("Indique qué dato se corrige");
		}
		if (description != null) {
			String clean = requireText(description, "La descripción no puede quedar vacía");
			if (clean.length() > 500) {
				throw IncidentErrors.eventInvalid("La descripción admite hasta 500 caracteres");
			}
			Map<String, Object> details = new HashMap<>();
			details.put("field", "DESCRIPTION");
			details.put("previous", ticket.getDescription());
			details.put("new", clean);
			details.put("justification", why);
			ticket.correctDescription(clean);
			record(ticket, EventType.DATA_CORRECTED, why, details, false, "TICKET_DATA_CORRECTED");
		}
		if (origin != null) {
			String detail = blankToNull(originDetail);
			if (origin == ReportOrigin.OTHER && detail == null) {
				throw IncidentErrors.eventInvalid("«Otro» como origen del aviso exige un detalle");
			}
			Map<String, Object> details = new HashMap<>();
			details.put("field", "REPORT_ORIGIN");
			details.put("previous", ticket.getReportOrigin().name());
			details.put("new", origin.name());
			details.put("justification", why);
			ticket.correctOrigin(origin, origin == ReportOrigin.OTHER ? detail : null);
			record(ticket, EventType.DATA_CORRECTED, why, details, false, "TICKET_DATA_CORRECTED");
		}
	}

	private void record(Ticket ticket, EventType type, String text, Map<String, Object> details, boolean notifiable,
			String auditAction) {
		Instant now = Instant.now(clock);
		events.save(TicketEvent.record(ticket.getId(), ticket.latestEpisode().getId(), type, now, now,
				actor.id().orElse(null), false, text, null, null, details, notifiable));
		ticket.touch(now);
		audit.record(auditAction, "INCIDENT_TICKET", ticket.getId(), Map.of("code", code(ticket)));
	}

	private static String code(Ticket ticket) {
		return new TicketCode(ticket.getTicketNumber()).text();
	}

	private static String requireText(String text, String message) {
		if (text == null || text.isBlank()) {
			throw IncidentErrors.eventInvalid(message);
		}
		if (text.strip().length() > MAX_TEXT) {
			throw IncidentErrors.eventInvalid("El texto admite hasta " + MAX_TEXT + " caracteres");
		}
		return text.strip();
	}

	private static String requireJustification(String text, String message) {
		if (text == null || text.isBlank()) {
			throw IncidentErrors.justificationRequired(message);
		}
		return text.strip();
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}
}
