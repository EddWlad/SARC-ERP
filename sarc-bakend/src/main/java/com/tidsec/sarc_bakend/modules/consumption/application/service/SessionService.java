package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.time.Clock;
import com.tidsec.sarc_bakend.modules.consumption.application.command.DecideEnergyReviewCommand;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergyReviewRepository;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.consumption.application.command.EditSessionCommand;
import com.tidsec.sarc_bakend.modules.consumption.application.command.MarkSessionTestCommand;
import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.application.result.PendingLinkResult;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.application.service.EquipmentResolver.Resolution;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionReading;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.error.BusinessRuleException;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;
import com.tidsec.sarc_bakend.shared.time.OperatingZone;

/** Operaciones sobre sesiones ya importadas: marcar prueba, conciliar pendientes, editar y dar de baja. */
@Service
@Transactional
@PreAuthorize(ConsumptionPermissions.EDITORS)
public class SessionService {

	private static final int PENDING_CHUNK = 500;

	private final EnergySessionRepository sessions;
	private final EquipmentResolver resolver;
	private final EquipmentDirectory directory;
	private final AuditTrail audit;
	private final EnergyReviewRepository reviews;
	private final CurrentActor actor;
	private final Clock clock;
	private final OperatingZone zone;

	SessionService(EnergySessionRepository sessions, EquipmentResolver resolver, EquipmentDirectory directory,
			AuditTrail audit, EnergyReviewRepository reviews, CurrentActor actor, Clock clock, OperatingZone zone) {
		this.sessions = sessions;
		this.resolver = resolver;
		this.directory = directory;
		this.audit = audit;
		this.reviews = reviews;
		this.actor = actor;
		this.clock = clock;
		this.zone = zone;
	}

	/** Excluye o reincorpora una sesion de los indicadores oficiales sin eliminarla (PRD RF-003.9). */
	public SessionView markTest(MarkSessionTestCommand command) {
		EnergySession session = loadForChange(command.id(), command.version());
		String previous = session.getState().name();
		session.markTest(command.test());
		sessions.save(session);
		audit.record("ENERGY_SESSION_STATE_CHANGED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(),
				Map.of("from", previous, "to", session.getState().name()));
		return view(session);
	}

	/**
	 * Aprueba que una sesion de fuente no preferente sume para su equipo (PRD 7.2): el analista
	 * confirma que la fuente preferente no reporta la misma carga.
	 */
	public SessionView approveSource(UUID id, long version) {
		EnergySession session = loadForChange(id, version);
		session.approveSource();
		sessions.save(session);
		audit.record("ENERGY_SESSION_SOURCE_APPROVED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(),
				Map.of("source", session.getSource().name()));
		return view(session);
	}

	/**
	 * Vuelve a buscar el equipo de las sesiones pendientes, p. ej. despues de registrar y verificar
	 * en el inventario la identidad externa que faltaba. Las que se resuelven pasan a validadas, o a
	 * por aprobar si llegaron por una fuente que no es la preferente del equipo: la misma exigencia
	 * que al importarlas con el equipo ya conocido.
	 */
	public PendingLinkResult linkPending() {
		int linked = 0;
		int awaitingApproval = 0;
		int stillPending = 0;
		int awaitingEnergyReview = 0;
		UUID after = null;
		List<EnergySession> chunk;
		while (!(chunk = sessions.findPendingLink(after, PENDING_CHUNK)).isEmpty()) {
			Map<SessionSource, List<EnergySession>> bySource = chunk.stream()
					.collect(Collectors.groupingBy(EnergySession::getSource));
			for (var entry : bySource.entrySet()) {
				Map<EquipmentLookup, Resolution> found = resolver.resolve(entry.getKey(),
						entry.getValue().stream().map(EquipmentResolver::lookupOf).distinct().toList());
				for (EnergySession session : entry.getValue()) {
					Resolution resolution = found.get(EquipmentResolver.lookupOf(session));
					if (resolution.equipment() == null) {
						session.markUnlinkable(resolution.issue());
						sessions.save(session);
						stillPending++;
					}
					else {
						session.link(resolution.equipment());
						sessions.save(session);
						audit.record("ENERGY_SESSION_LINKED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(),
								EquipmentResolver.linkDetails(session, "PENDING_LINK_OPERATION"));
						if (session.getState() == SessionState.AWAITING_APPROVAL) {
							awaitingApproval++;
						}
						else if (session.getState() == SessionState.ENERGY_REVIEW || session.getState() == SessionState.ENERGY_REJECTED) {
							awaitingEnergyReview++;
						}
						else {
							linked++;
						}
					}
				}
			}
			after = chunk.getLast().getId();
		}
		if (linked + awaitingApproval + awaitingEnergyReview > 0) {
			audit.record("ENERGY_SESSIONS_LINKED", EnergySession.AUDIT_ENTITY_TYPE, null,
					Map.of("linked", String.valueOf(linked), "awaitingApproval", String.valueOf(awaitingApproval),
							"stillPending", String.valueOf(stillPending), "awaitingEnergyReview", String.valueOf(awaitingEnergyReview)));
		}
		return new PendingLinkResult(linked, awaitingApproval, stillPending, awaitingEnergyReview);
	}

	public SessionView decideEnergy(DecideEnergyReviewCommand command) {
		EnergySession session = loadForChange(command.id(), command.version());
		String before = session.getEnergyReview() == null ? "NONE" : session.getEnergyReview().name();
		var review = session.decideEnergy(command.decision(), command.justification(), actor.id().orElse(null), clock.instant());
		sessions.flushEnergyDecision(session);
		reviews.insert(review);
		audit.record("ENERGY_SESSION_REVIEWED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(),
				Map.of("from", before, "to", review.getDecision().name(), "justification", review.getJustification(),
						"energyKwh", review.getEnergyKwh().toPlainString(), "sessionVersion", Long.toString(review.getSessionVersion())));
		return view(session);
	}

	/**
	 * Edicion individual con motivo (Feature 009, FR-012 y FR-013): aplica las mismas reglas que una
	 * importacion (validez, umbral de energia, vinculo y fuente preferente) y guarda la version anterior.
	 */
	public SessionView edit(EditSessionCommand command) {
		EnergySession session = loadForChange(command.id(), command.version());
		SessionReading before = session.reading();
		SessionReading after = command.applyTo(before);
		EquipmentReference current = equipmentOf(session.getEquipmentId());
		EquipmentReference equipment = command.equipmentId() == null ? current
				: command.equipmentId().map(this::requireEquipment).orElse(null);
		UUID beforeEquipment = session.getEquipmentId();
		if (after.sameAs(before) && Objects.equals(beforeEquipment, equipment == null ? null : equipment.id())) {
			throw new BusinessRuleException("SESSION_UNCHANGED", "La edición no cambia ningún dato de la sesión");
		}
		String beforeState = session.getState().name();
		sessions.saveRevision(session.edit(after, equipment, command.reason(), actor.id().orElse(null),
				clock.instant(), zone));
		sessions.save(session);
		Map<String, String> details = new LinkedHashMap<>();
		details.put("reason", command.reason());
		details.put("revision", Integer.toString(session.getRevision()));
		changed(details, "startedAt", before.startedAt(), after.startedAt());
		changed(details, "endedAt", before.endedAt(), after.endedAt());
		changed(details, "energyKwh", before.energyKwh(), after.energyKwh());
		changed(details, "equipmentId", beforeEquipment, session.getEquipmentId());
		changed(details, "state", beforeState, session.getState().name());
		audit.record("ENERGY_SESSION_EDITED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(), details);
		return new SessionView(session, equipment);
	}

	/**
	 * Baja logica con motivo (Feature 009, FR-014): sale de los indicadores e informes y conserva identidad,
	 * datos y auditoria. Repetirla devuelve la baja vigente sin crear otra revision.
	 */
	public SessionView delete(UUID id, long version, String reason) {
		EnergySession session = sessions.findById(id)
				.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "La sesion no existe"));
		if (session.isDeleted()) {
			return view(session);
		}
		session.requireVersion(version);
		sessions.saveRevision(session.delete(reason, actor.id().orElse(null), clock.instant()));
		sessions.save(session);
		audit.record("ENERGY_SESSION_DELETED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(),
				Map.of("reason", reason, "state", session.getState().name(),
						"energyKwh", text(session.getEnergyKwh()), "revision", Integer.toString(session.getRevision())));
		return view(session);
	}

	private EquipmentReference requireEquipment(UUID id) {
		EquipmentReference equipment = directory.findByIds(List.of(id)).get(id);
		if (equipment == null) {
			throw new NotFoundException("EQUIPMENT_NOT_FOUND", "El equipo no existe en el inventario");
		}
		return equipment;
	}

	private EquipmentReference equipmentOf(UUID id) {
		return id == null ? null : directory.findByIds(List.of(id)).get(id);
	}

	private static void changed(Map<String, String> details, String field, Object before, Object after) {
		boolean same = before instanceof BigDecimal a && after instanceof BigDecimal b ? a.compareTo(b) == 0
				: Objects.equals(before, after);
		if (!same) {
			details.put(field + ".from", text(before));
			details.put(field + ".to", text(after));
		}
	}

	private static String text(Object value) {
		return switch (value) {
			case null -> "";
			case BigDecimal decimal -> decimal.toPlainString();
			default -> value.toString();
		};
	}

	private EnergySession loadForChange(UUID id, long version) {
		EnergySession session = sessions.findById(id)
				.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "La sesion no existe"));
		session.requireVersion(version);
		return session;
	}

	private SessionView view(EnergySession session) {
		return new SessionView(session, equipmentOf(session.getEquipmentId()));
	}
}
