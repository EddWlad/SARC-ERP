package com.tidsec.sarc_bakend.modules.identity.infrastructure.security;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import com.tidsec.sarc_bakend.modules.identity.application.port.UserSessionRegistry;
import com.tidsec.sarc_bakend.shared.persistence.AfterCommit;

/** Revoca sesiones en Spring Session JDBC, y solo despues de confirmar la transaccion que lo motiva. */
@Component
class SpringSessionUserSessionRegistry implements UserSessionRegistry {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	SpringSessionUserSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	@Override
	public void invalidateAll(UUID userId) {
		AfterCommit.run(() -> sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById));
	}
}
