package com.tidsec.sarc_bakend.modules.consumption.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.consumption.application.command.MarkSessionTestCommand;
import com.tidsec.sarc_bakend.modules.consumption.application.port.EquipmentLookup;
import com.tidsec.sarc_bakend.modules.consumption.application.result.PendingLinkResult;
import com.tidsec.sarc_bakend.modules.consumption.application.result.SessionView;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.EnergySession;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionSource;
import com.tidsec.sarc_bakend.modules.consumption.domain.model.session.SessionState;
import com.tidsec.sarc_bakend.modules.consumption.domain.repository.EnergySessionRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentDirectory;
import com.tidsec.sarc_bakend.shared.catalog.EquipmentReference;
import com.tidsec.sarc_bakend.shared.error.NotFoundException;

/** Operaciones sobre sesiones ya importadas: marcar prueba y conciliar pendientes. */
@Service
@Transactional
@PreAuthorize(ConsumptionPermissions.EDITORS)
public class SessionService {

	private static final int PENDING_CHUNK = 500;

	private final EnergySessionRepository sessions;
	private final EquipmentResolver resolver;
	private final EquipmentDirectory directory;
	private final AuditTrail audit;

	SessionService(EnergySessionRepository sessions, EquipmentResolver resolver, EquipmentDirectory directory,
			AuditTrail audit) {
		this.sessions = sessions;
		this.resolver = resolver;
		this.directory = directory;
		this.audit = audit;
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
		UUID after = null;
		List<EnergySession> chunk;
		while (!(chunk = sessions.findPendingLink(after, PENDING_CHUNK)).isEmpty()) {
			Map<SessionSource, List<EnergySession>> bySource = chunk.stream()
					.collect(Collectors.groupingBy(EnergySession::getSource));
			for (var entry : bySource.entrySet()) {
				Map<EquipmentLookup, EquipmentReference> found = resolver.resolve(entry.getKey(),
						entry.getValue().stream().map(EquipmentResolver::lookupOf).distinct().toList());
				for (EnergySession session : entry.getValue()) {
					EquipmentReference equipment = found.get(EquipmentResolver.lookupOf(session));
					if (equipment == null) {
						stillPending++;
					}
					else {
						session.link(equipment);
						sessions.save(session);
						if (session.getState() == SessionState.AWAITING_APPROVAL) {
							awaitingApproval++;
						}
						else {
							linked++;
						}
					}
				}
			}
			after = chunk.getLast().getId();
		}
		if (linked + awaitingApproval > 0) {
			audit.record("ENERGY_SESSIONS_LINKED", EnergySession.AUDIT_ENTITY_TYPE, null,
					Map.of("linked", String.valueOf(linked), "awaitingApproval", String.valueOf(awaitingApproval),
							"stillPending", String.valueOf(stillPending)));
		}
		return new PendingLinkResult(linked, awaitingApproval, stillPending);
	}

	private EnergySession loadForChange(UUID id, long version) {
		EnergySession session = sessions.findById(id)
				.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "La sesion no existe"));
		session.requireVersion(version);
		return session;
	}

	private SessionView view(EnergySession session) {
		EquipmentReference equipment = session.getEquipmentId() == null ? null
				: directory.findByIds(List.of(session.getEquipmentId())).get(session.getEquipmentId());
		return new SessionView(session, equipment);
	}
}
