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
		EnergySession session = sessions.findById(command.id())
				.orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "La sesion no existe"));
		session.requireVersion(command.version());
		String previous = session.getState().name();
		session.markTest(command.test());
		sessions.save(session);
		audit.record("ENERGY_SESSION_STATE_CHANGED", EnergySession.AUDIT_ENTITY_TYPE, session.getId(),
				Map.of("from", previous, "to", session.getState().name()));
		EquipmentReference equipment = session.getEquipmentId() == null ? null
				: directory.findByIds(List.of(session.getEquipmentId())).get(session.getEquipmentId());
		return new SessionView(session, equipment);
	}

	/**
	 * Vuelve a buscar el equipo de las sesiones pendientes, p. ej. despues de registrar y verificar
	 * en el inventario la identidad externa que faltaba. Las que se resuelven pasan a validadas.
	 */
	public PendingLinkResult linkPending() {
		int linked = 0;
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
						linked++;
					}
				}
			}
			after = chunk.getLast().getId();
		}
		if (linked > 0) {
			audit.record("ENERGY_SESSIONS_LINKED", EnergySession.AUDIT_ENTITY_TYPE, null,
					Map.of("linked", String.valueOf(linked), "stillPending", String.valueOf(stillPending)));
		}
		return new PendingLinkResult(linked, stillPending);
	}
}
