package com.tidsec.sarc_bakend.shared.persistence;

import jakarta.persistence.EntityManager;

/**
 * Bloqueos consultivos de PostgreSQL de alcance de transaccion: serializan las escrituras concurrentes sobre lo
 * mismo y se liberan solos al confirmar o revertir. Cada uso pone su propio prefijo para que dos tipos de
 * recurso nunca compartan clave.
 */
public final class AdvisoryLocks {

	private AdvisoryLocks() {
	}

	/**
	 * Toma el bloqueo de {@code prefix + key}; espera si otra transaccion lo tiene. Debe llamarse dentro de una
	 * transaccion. El subselect evita leer el valor void que devuelve {@code pg_advisory_xact_lock}.
	 */
	public static void lock(EntityManager entityManager, String prefix, String key) {
		entityManager.createNativeQuery(
				"SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(?1 || CAST(?2 AS text)))) AS locked")
				.setParameter(1, prefix).setParameter(2, key).getResultList();
	}
}
