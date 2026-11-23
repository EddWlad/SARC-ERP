package com.tidsec.sarc_bakend.modules.incident.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.modules.incident.application.command.ConfigureNumberingCommand;
import com.tidsec.sarc_bakend.modules.incident.application.port.IncidentSettings;
import com.tidsec.sarc_bakend.modules.incident.application.result.NumberingView;
import com.tidsec.sarc_bakend.modules.incident.domain.IncidentErrors;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketCode;
import com.tidsec.sarc_bakend.modules.incident.domain.model.numbering.TicketNumbering;
import com.tidsec.sarc_bakend.modules.incident.domain.repository.TicketNumberingRepository;
import com.tidsec.sarc_bakend.shared.audit.AuditTrail;
import com.tidsec.sarc_bakend.shared.security.CurrentActor;

/**
 * Numeracion de tickets (FR-012 a FR-015). Mientras no se configure, en ningun ambiente se crean tickets. El numero
 * inicial de produccion (P-01) no esta en ningun archivo: lo configura un Administrador despues de publicar, y solo
 * antes del primer ticket.
 */
@Service
public class TicketNumberingService {

	/** Identidad de auditoria del contador: tiene una sola fila, sin UUID propio. */
	static final UUID AUDIT_ID = UUID.nameUUIDFromBytes("inc_ticket_numbering".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	static final String AUDIT_ENTITY_TYPE = "INCIDENT_NUMBERING";

	private final TicketNumberingRepository numbering;
	private final IncidentSettings settings;
	private final AuditTrail audit;
	private final CurrentActor actor;
	private final Clock clock;

	TicketNumberingService(TicketNumberingRepository numbering, IncidentSettings settings, AuditTrail audit,
			CurrentActor actor, Clock clock) {
		this.numbering = numbering;
		this.settings = settings;
		this.audit = audit;
		this.actor = actor;
		this.clock = clock;
	}

	@PreAuthorize(IncidentPermissions.READERS)
	@Transactional(readOnly = true)
	public NumberingView view() {
		TicketNumbering current = numbering.current();
		return new NumberingView(current.configured(), current.environment(), current.startNumber(),
				current.nextCode().map(TicketCode::text).orElse(null),
				settings.expectedNumberingEnvironment().orElse(null), numbering.ticketsExist());
	}

	@PreAuthorize(IncidentPermissions.NUMBERING)
	@Transactional
	public NumberingView configure(ConfigureNumberingCommand command) {
		String expected = settings.expectedNumberingEnvironment().orElse(null);
		if (expected == null || !expected.equals(command.environment())) {
			throw IncidentErrors.numberingEnvironmentMismatchOnConfigure();
		}
		String code = new TicketCode(command.startNumber()).text();
		if (!code.equalsIgnoreCase(command.confirmation() == null ? "" : command.confirmation().strip())) {
			throw IncidentErrors.numberingConfirmationMismatch(code);
		}
		Instant now = Instant.now(clock);
		if (!numbering.configure(command.startNumber(), command.environment(), actor.id().orElse(null), now)) {
			throw IncidentErrors.numberingLocked();
		}
		audit.record("NUMBERING_CONFIGURED", AUDIT_ENTITY_TYPE, AUDIT_ID,
				Map.of("startNumber", Integer.toString(command.startNumber()), "environment", command.environment()));
		return view();
	}
}
