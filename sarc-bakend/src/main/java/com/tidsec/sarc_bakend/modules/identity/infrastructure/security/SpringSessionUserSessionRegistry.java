package com.tidsec.sarc_bakend.modules.identity.infrastructure.security;

import java.util.UUID;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.tidsec.sarc_bakend.modules.identity.application.port.UserSessionRegistry;

/** Revoca sesiones en Spring Session JDBC, y solo despues de confirmar la transaccion que lo motiva. */
@Component
class SpringSessionUserSessionRegistry implements UserSessionRegistry {

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	SpringSessionUserSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessions) {
		this.sessions = sessions;
	}

	@Override
	public void invalidateAll(UUID userId) {
		Runnable revoke = () -> sessions.findByPrincipalName(userId.toString()).keySet().forEach(sessions::deleteById);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					revoke.run();
				}
			});
		}
		else {
			revoke.run();
		}
	}
}
