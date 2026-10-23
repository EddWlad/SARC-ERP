package com.tidsec.sarc_bakend.shared.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tidsec.sarc_bakend.shared.security.CurrentActor;
import com.tidsec.sarc_bakend.shared.web.RequestTraceFilter;

/**
 * Registra hechos auditables dentro de la misma transaccion del cambio que describen, de modo que
 * auditoria y dato confirman o revierten juntos. Nunca debe recibir secretos en {@code details}.
 */
@Service
public class AuditTrail {

	private final AuditEventRepository repository;
	private final CurrentActor currentActor;
	private final Clock clock;

	AuditTrail(AuditEventRepository repository, CurrentActor currentActor, Clock clock) {
		this.repository = repository;
		this.currentActor = currentActor;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(String action, String entityType, UUID entityId, Map<String, String> details) {
		repository.save(new AuditEvent(Instant.now(clock), currentActor.id().orElse(null), action, entityType,
				entityId, MDC.get(RequestTraceFilter.TRACE_ID_KEY), details));
	}
}
