package com.tidsec.sarc_bakend.shared.persistence;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Ejecuta efectos externos (revocar sesiones, borrar archivos) solo si la transaccion que los
 * motiva se confirma. Sin transaccion activa, los ejecuta de inmediato.
 */
public final class AfterCommit {

	private AfterCommit() {
	}

	public static void run(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}
}
